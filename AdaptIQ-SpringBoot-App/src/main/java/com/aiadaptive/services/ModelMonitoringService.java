package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.CategoryRelevance;
import com.aiadaptive.dto.Dto.ModelMetrics;
import com.aiadaptive.dto.Dto.ModelVersion;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.LearningProfileRepository;
import com.aiadaptive.repositories.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** FR-12: model performance, per-category bias audit (5-point threshold) and retraining. */
@Service
public class ModelMonitoringService {

    public static final double BIAS_THRESHOLD = 0.05;
    public static final int MIN_FOR_RETRAIN = 10;
    public static final int MIN_PER_CATEGORY = 5;
    /** Keep both signals in play: calibration may only move the blend within this range. */
    public static final double MIN_WEIGHT = 0.2;
    public static final double MAX_WEIGHT = 0.8;

    private final RecommendationRepository recs;
    private final LearningProfileRepository profiles;
    private final ModelRegistry registry;

    public ModelMonitoringService(RecommendationRepository recs, LearningProfileRepository profiles,
                                  ModelRegistry registry) {
        this.recs = recs;
        this.profiles = profiles;
        this.registry = registry;
    }

    public ModelMetrics metrics() {
        List<Recommendation> rated = recs.findAll().stream().filter(r -> r.getRating() != null).collect(Collectors.toList());
        Double overall = relevance(rated);
        List<String> notes = new ArrayList<>();

        Map<DisabilityCategory, List<Recommendation>> byCat = new EnumMap<>(DisabilityCategory.class);
        for (Recommendation r : rated) {
            Set<DisabilityCategory> cats = profiles.findById(r.getStudentId())
                    .map(LearningProfile::getDisabilityCategories).orElse(EnumSet.of(DisabilityCategory.NONE));
            for (DisabilityCategory c : cats) byCat.computeIfAbsent(c, k -> new ArrayList<>()).add(r);
        }
        List<CategoryRelevance> rows = new ArrayList<>();
        List<Double> comparable = new ArrayList<>();
        for (Map.Entry<DisabilityCategory, List<Recommendation>> e : byCat.entrySet()) {
            Double rel = relevance(e.getValue());
            boolean enough = e.getValue().size() >= MIN_PER_CATEGORY;
            boolean flagged = enough && overall != null && rel != null && Math.abs(rel - overall) > BIAS_THRESHOLD;
            if (enough && rel != null) comparable.add(rel);
            if (!enough) notes.add(e.getKey() + ": only " + e.getValue().size() + " ratings, too few to audit (need " + MIN_PER_CATEGORY + ").");
            rows.add(new CategoryRelevance(e.getKey().name(), e.getValue().size(), rel == null ? null : round3(rel), flagged));
        }
        Double gap = comparable.size() >= 2
                ? round1(100.0 * (Collections.max(comparable) - Collections.min(comparable))) : null;
        boolean alert = rows.stream().anyMatch(CategoryRelevance::flagged);
        if (alert) notes.add("Bias alert: at least one category deviates from the overall relevance by more than 5 percentage points.");

        ModelVersion v = registry.current();
        return new ModelMetrics(rated.size(), overall == null ? null : round3(overall), rows, gap, alert,
                v.version(), v.similarityWeight(), v.preferenceWeight(), notes);
    }

    /**
     * Calibrates the blend between collaborative similarity and preference match: picks the weight whose
     * predicted scores best align with students' thumbs up/down ratings, then registers a new model version.
     */
    public ModelVersion retrain(String triggeredBy) {
        List<Recommendation> rated = recs.findAll().stream().filter(r -> r.getRating() != null).collect(Collectors.toList());
        if (rated.size() < MIN_FOR_RETRAIN) {
            throw new ApiException(409, "Not enough rated recommendations to retrain (" + rated.size()
                    + " of " + MIN_FOR_RETRAIN + " needed)");
        }
        long start = System.currentTimeMillis();
        ModelVersion before = registry.current();
        double bestW = before.similarityWeight();
        double bestAlign = -1;
        for (int i = 2; i <= 8; i++) {
            double w = i / 10.0;
            double a = alignment(rated, w);
            if (a > bestAlign + 1e-9) {
                bestAlign = a;
                bestW = w;
            }
        }
        double previous = alignment(rated, before.similarityWeight());
        String log = String.format(Locale.ROOT,
                "Retrained on %d rated recommendations. Similarity weight %.2f -> %.2f; alignment with student ratings %.3f -> %.3f.",
                rated.size(), before.similarityWeight(), bestW, previous, bestAlign);
        return registry.register(bestW, round3(bestAlign), System.currentTimeMillis() - start, triggeredBy, log);
    }

    public List<ModelVersion> versions() {
        return registry.all();
    }

    private static double alignment(List<Recommendation> rated, double wSim) {
        double sum = 0;
        for (Recommendation r : rated) {
            double predicted = wSim * r.getSimilarityScore() + (1 - wSim) * r.getPreferenceScore();
            sum += r.getRating() == Rating.UP ? predicted : 1 - predicted;
        }
        return sum / rated.size();
    }

    private static Double relevance(List<Recommendation> rated) {
        if (rated.isEmpty()) return null;
        long up = rated.stream().filter(r -> r.getRating() == Rating.UP).count();
        return (double) up / rated.size();
    }

    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
