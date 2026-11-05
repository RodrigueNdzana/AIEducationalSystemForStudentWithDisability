package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.ModelVersion;
import com.aiadaptive.dto.Dto.RecommendationView;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FR-03: hybrid recommender from the design phase (section 3.2.2).
 * Hard accessibility filter first, then score = wSim * collaborativeSimilarity + wPref * preferenceMatch,
 * adjusted for difficulty fit, clamped to 0-100 (DEF-002 fix).
 */
@Service
public class RecommendationService {

    public static final int MAX_RECOMMENDATIONS = 5;

    private final RecommendationRepository recs;
    private final ContentRepository contentRepo;
    private final LearningProfileRepository profiles;
    private final InteractionRepository interactions;
    private final UserRepository users;
    private final ModelRegistry registry;

    public RecommendationService(RecommendationRepository recs, ContentRepository contentRepo,
                                 LearningProfileRepository profiles, InteractionRepository interactions,
                                 UserRepository users, ModelRegistry registry) {
        this.recs = recs;
        this.contentRepo = contentRepo;
        this.profiles = profiles;
        this.interactions = interactions;
        this.users = users;
        this.registry = registry;
    }

    // ---- existing API kept from the original repository ----
    public Recommendation createRecommendation(Recommendation rec) {
        rec.setMatchScore(clamp(rec.getMatchScore()));
        return recs.save(rec);
    }

    public List<Recommendation> getAllRecommendations() {
        return recs.findAll();
    }

    // ---- new API ----
    public List<RecommendationView> forStudent(String studentId) {
        Map<String, Content> byId = contentRepo.findAll().stream()
                .collect(Collectors.toMap(Content::getId, c -> c, (a, b) -> a));
        return recs.findByStudent(studentId).stream()
                .filter(r -> !r.isArchived())
                .sorted(Comparator.comparing(Recommendation::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparingInt(Recommendation::getMatchScore).reversed()))
                .filter(r -> byId.containsKey(r.getContentId()))
                .map(r -> {
                    Content c = byId.get(r.getContentId());
                    return new RecommendationView(r, c.getTitle(), c.getFormat(), c.getDifficulty(), c.getModuleId());
                }).collect(Collectors.toList());
    }

    /** Generates a fresh set of recommendations, replacing earlier unrated ones. */
    public List<Recommendation> refresh(String studentId) {
        User u = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (u.getRole() != Role.STUDENT) throw ApiException.badRequest("Recommendations are for students");

        LearningProfile profile = profiles.findById(studentId).orElseGet(() -> new LearningProfile(studentId));
        ModelVersion model = registry.current();

        for (Recommendation old : recs.findByStudent(studentId)) {
            if (old.getRating() == null) {
                recs.delete(old.getId());
            } else if (!old.isArchived()) {
                old.setArchived(true);          // keep rated history for retraining and bias audits
                recs.save(old);
            }
        }

        List<Content> eligible = contentRepo.findPublished().stream()
                .filter(c -> isCompatible(c, profile)).collect(Collectors.toList());
        if (eligible.isEmpty()) return List.of();

        List<Interaction> mine = interactions.findByStudent(studentId);
        Difficulty target = targetDifficulty(mine);
        Set<String> completed = mine.stream().filter(Interaction::isCompleted)
                .map(Interaction::getContentId).collect(Collectors.toSet());
        PeerStats peers = peerStats(studentId, profile.getDisabilityCategories());

        List<Recommendation> out = new ArrayList<>();
        for (Content c : eligible) {
            double sim = peers.similarity(c);
            double pref = preferenceMatch(profile, c);
            double diffFactor = 1.0 - 0.15 * Math.abs(c.getDifficulty().level() - target.level());
            double doneFactor = completed.contains(c.getId()) ? 0.5 : 1.0;
            double raw = 100.0 * (model.similarityWeight() * sim + model.preferenceWeight() * pref)
                    * diffFactor * doneFactor;

            Recommendation r = new Recommendation();
            r.setStudentId(studentId);
            r.setContentId(c.getId());
            r.setMatchScore(clamp((int) Math.round(raw)));
            r.setSimilarityScore(round2(sim));
            r.setPreferenceScore(round2(pref));
            r.setModelVersion(model.version());
            r.setReason(reason(c, profile, sim, pref, target, completed.contains(c.getId()), peers.hasData(c)));
            out.add(r);
        }
        out.sort(Comparator.comparingInt(Recommendation::getMatchScore).reversed());
        List<Recommendation> top = out.subList(0, Math.min(MAX_RECOMMENDATIONS, out.size()));
        List<Recommendation> saved = new ArrayList<>();
        for (Recommendation r : top) saved.add(recs.save(r));
        return saved;
    }

    public Recommendation rate(String recommendationId, String studentId, Rating rating) {
        if (rating == null) throw ApiException.badRequest("rating (UP or DOWN) is required");
        Recommendation r = recs.findById(recommendationId).orElseThrow(() -> ApiException.notFound("Recommendation"));
        if (!r.getStudentId().equals(studentId)) throw ApiException.forbidden("Not your recommendation");
        r.setRating(rating);
        return recs.save(r);
    }

    /** The non-negotiable accessibility rules; the AI ranking can never override them. */
    public boolean isCompatible(Content c, LearningProfile p) {
        for (DisabilityCategory cat : p.getDisabilityCategories()) {
            switch (cat) {
                case HEARING:
                    if (c.getFormat() == ContentFormat.VIDEO && !c.captionsAvailable()) return false;
                    if (c.getFormat() == ContentFormat.AUDIO && !c.transcriptAvailable()) return false;
                    break;
                case VISUAL:
                    if (c.isHasImages() && !c.isHasAltText()) return false;
                    if ((c.getFormat() == ContentFormat.VIDEO || c.getFormat() == ContentFormat.INTERACTIVE)
                            && !c.isHasAudioNarration()) return false;
                    break;
                case MOTOR:
                    if (c.isRequiresFineMotor()) return false;
                    break;
                case COGNITIVE:
                    if (c.getFormat() == ContentFormat.INTERACTIVE && !c.transcriptAvailable()) return false;
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    static Difficulty targetDifficulty(List<Interaction> mine) {
        List<Integer> scores = mine.stream().map(Interaction::getScore).filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (scores.isEmpty()) return Difficulty.BEGINNER;
        List<Integer> last = scores.subList(Math.max(0, scores.size() - 5), scores.size());
        double avg = last.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (avg < 50) return Difficulty.BEGINNER;
        if (avg < 75) return Difficulty.INTERMEDIATE;
        return Difficulty.ADVANCED;
    }

    static double preferenceMatch(LearningProfile p, Content c) {
        double base = p.getPreferredFormats().isEmpty() ? 0.5 : (p.getPreferredFormats().contains(c.getFormat()) ? 1.0 : 0.3);
        String goals = p.getGoals() == null ? "" : p.getGoals().toLowerCase();
        if (!goals.isBlank() && c.getTopic() != null && !c.getTopic().isBlank()
                && goals.contains(c.getTopic().toLowerCase())) {
            base = Math.min(1.0, base + 0.15);
        }
        return base;
    }

    private String reason(Content c, LearningProfile p, double sim, double pref, Difficulty target,
                          boolean done, boolean peerData) {
        List<String> parts = new ArrayList<>();
        if (p.getPreferredFormats().contains(c.getFormat())) parts.add("matches your preferred format (" + c.getFormat() + ")");
        if (peerData) parts.add("learners with similar accessibility needs rated similar content " + Math.round(sim * 100) + "% positively");
        else parts.add("no peer data yet, so a neutral starting score was used");
        parts.add(c.getDifficulty() == target ? "difficulty fits your recent results"
                : "difficulty is " + c.getDifficulty() + " (your level looks like " + target + ")");
        if (done) parts.add("you already completed it, so it is ranked lower");
        return String.join("; ", parts) + ".";
    }

    /** Peer positivity by format and by content, from ratings and assessed interactions of similar learners. */
    private PeerStats peerStats(String studentId, Set<DisabilityCategory> mine) {
        PeerStats ps = new PeerStats();
        Map<String, Content> byId = contentRepo.findAll().stream()
                .collect(Collectors.toMap(Content::getId, c -> c, (a, b) -> a));
        Map<String, Double> weights = new HashMap<>();
        for (User u : users.findByRole(Role.STUDENT)) {
            if (u.getId().equals(studentId)) continue;
            Set<DisabilityCategory> theirs = profiles.findById(u.getId())
                    .map(LearningProfile::getDisabilityCategories).orElse(EnumSet.of(DisabilityCategory.NONE));
            double w = jaccard(mine, theirs);
            if (w > 0) weights.put(u.getId(), w);
        }
        for (Recommendation r : recs.findAll()) {
            Double w = weights.get(r.getStudentId());
            Content c = byId.get(r.getContentId());
            if (w == null || c == null || r.getRating() == null) continue;
            ps.add(c, w, r.getRating() == Rating.UP ? 1.0 : 0.0);
        }
        for (String peerId : weights.keySet()) {
            for (Interaction i : interactions.findByStudent(peerId)) {
                Content c = byId.get(i.getContentId());
                if (c == null || i.getScore() == null) continue;
                ps.add(c, weights.get(peerId) * 0.5, i.getScore() >= 60 ? 1.0 : 0.0);
            }
        }
        return ps;
    }

    static double jaccard(Set<DisabilityCategory> a, Set<DisabilityCategory> b) {
        Set<DisabilityCategory> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<DisabilityCategory> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    private static final class PeerStats {
        private final Map<ContentFormat, double[]> byFormat = new EnumMap<>(ContentFormat.class);
        private final Map<String, double[]> byContent = new HashMap<>();

        void add(Content c, double weight, double value) {
            double[] f = byFormat.computeIfAbsent(c.getFormat(), k -> new double[2]);
            f[0] += weight * value; f[1] += weight;
            double[] k = byContent.computeIfAbsent(c.getId(), x -> new double[2]);
            k[0] += weight * value; k[1] += weight;
        }

        boolean hasData(Content c) {
            double[] f = byFormat.get(c.getFormat());
            return f != null && f[1] > 0;
        }

        /** 0-1; neutral 0.5 on cold start. Blends format-level and content-level peer positivity. */
        double similarity(Content c) {
            double[] f = byFormat.get(c.getFormat());
            double[] k = byContent.get(c.getId());
            boolean hasF = f != null && f[1] > 0;
            boolean hasK = k != null && k[1] > 0;
            if (!hasF && !hasK) return 0.5;
            if (hasF && hasK) return 0.5 * (f[0] / f[1]) + 0.5 * (k[0] / k[1]);
            return hasF ? f[0] / f[1] : k[0] / k[1];
        }
    }

    static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
