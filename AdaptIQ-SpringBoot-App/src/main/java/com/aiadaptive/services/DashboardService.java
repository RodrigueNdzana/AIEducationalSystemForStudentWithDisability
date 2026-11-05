package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** FR-05 student dashboard and FR-06 educator dashboard. */
@Service
public class DashboardService {

    private final UserRepository users;
    private final ContentRepository contentRepo;
    private final InteractionRepository interactions;
    private final LearningProfileRepository profiles;
    private final AlertService alerts;
    private final int totalWeeks;
    private final int elapsedWeeks;

    public DashboardService(UserRepository users, ContentRepository contentRepo, InteractionRepository interactions,
                            LearningProfileRepository profiles, AlertService alerts,
                            @Value("${adaptiq.semester.total-weeks:12}") int totalWeeks,
                            @Value("${adaptiq.semester.elapsed-weeks:8}") int elapsedWeeks) {
        this.users = users;
        this.contentRepo = contentRepo;
        this.interactions = interactions;
        this.profiles = profiles;
        this.alerts = alerts;
        this.totalWeeks = Math.max(1, totalWeeks);
        this.elapsedWeeks = Math.max(0, Math.min(elapsedWeeks, this.totalWeeks));
    }

    /** Share of the semester that has passed = the progress a student is expected to have reached. */
    public double expectedPercent() {
        return Math.round(1000.0 * elapsedWeeks / totalWeeks) / 10.0;
    }

    /** At risk = below 50% of the expected progress for the timeframe elapsed. */
    public boolean isAtRisk(double completionPercent) {
        return completionPercent < 0.5 * expectedPercent();
    }

    public StudentDashboard student(String studentId, LocalDate from, LocalDate to) {
        User u = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (u.getRole() != Role.STUDENT) throw ApiException.badRequest("User is not a student");

        Map<String, Content> byId = contentRepo.findAll().stream()
                .collect(Collectors.toMap(Content::getId, c -> c, (a, b) -> a));
        List<Interaction> all = interactions.findByStudent(studentId);
        List<Content> published = contentRepo.findPublished();

        List<ModuleProgress> modules = moduleProgress(published, all);
        double completion = completionPercent(modules);

        List<Interaction> inRange = all.stream().filter(i -> inRange(i.getTimestamp(), from, to)).collect(Collectors.toList());
        List<ScorePoint> timeline = inRange.stream().filter(i -> i.getScore() != null)
                .map(i -> new ScorePoint(i.getTimestamp(), i.getScore(),
                        byId.containsKey(i.getContentId()) ? byId.get(i.getContentId()).getTitle() : i.getContentId()))
                .collect(Collectors.toList());
        Double avg = timeline.isEmpty() ? null
                : round1(timeline.stream().mapToInt(ScorePoint::score).average().orElse(0));
        int minutes = inRange.stream().mapToInt(Interaction::getMinutes).sum();

        return new StudentDashboard(studentId, u.getName(), completion, expectedPercent(), avg, minutes,
                modules, timeline, insights(all, modules, completion));
    }

    public EducatorDashboard educator(String course, String disability, String progressLevel, Boolean riskOnly) {
        List<Content> published = contentRepo.findPublished().stream()
                .filter(c -> course == null || course.isBlank() || course.equalsIgnoreCase(c.getCourse()))
                .collect(Collectors.toList());
        DisabilityCategory filterCat = null;
        if (disability != null && !disability.isBlank() && !disability.equalsIgnoreCase("ANY")) {
            try {
                filterCat = DisabilityCategory.valueOf(disability.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown disability filter: " + disability);
            }
        }
        List<StudentRow> rows = new ArrayList<>();
        for (User s : users.findByRole(Role.STUDENT)) {
            Set<DisabilityCategory> cats = profiles.findById(s.getId())
                    .map(LearningProfile::getDisabilityCategories).orElse(EnumSet.of(DisabilityCategory.NONE));
            if (filterCat != null && !cats.contains(filterCat)) continue;

            List<Interaction> all = interactions.findByStudent(s.getId());
            double completion = completionPercent(moduleProgress(published, all));
            if (progressLevel != null && !progressLevel.isBlank() && !level(completion).equalsIgnoreCase(progressLevel)) continue;
            boolean risk = isAtRisk(completion);
            if (Boolean.TRUE.equals(riskOnly) && !risk) continue;

            OptionalDouble avg = all.stream().filter(i -> i.getScore() != null).mapToInt(Interaction::getScore).average();
            Instant last = all.isEmpty() ? null : all.get(all.size() - 1).getTimestamp();
            rows.add(new StudentRow(s.getId(), s.getName(), cats, completion,
                    avg.isPresent() ? round1(avg.getAsDouble()) : null, risk, alerts.openCountFor(s.getId()), last));
        }
        rows.sort(Comparator.comparing(StudentRow::atRisk).reversed().thenComparing(StudentRow::name));
        int risky = (int) rows.stream().filter(StudentRow::atRisk).count();
        return new EducatorDashboard(expectedPercent(), rows.size(), risky, rows);
    }

    /** Overall completion for one student across all published content (used by reports too). */
    public double completionFor(String studentId) {
        return completionPercent(moduleProgress(contentRepo.findPublished(), interactions.findByStudent(studentId)));
    }

    static String level(double completion) {
        if (completion < 34) return "LOW";
        if (completion <= 66) return "MEDIUM";
        return "HIGH";
    }

    private List<ModuleProgress> moduleProgress(List<Content> published, List<Interaction> all) {
        Map<String, List<Content>> byModule = new TreeMap<>();
        for (Content c : published) {
            if (c.getModuleId() == null) continue;
            byModule.computeIfAbsent(c.getModuleId(), k -> new ArrayList<>()).add(c);
        }
        List<ModuleProgress> out = new ArrayList<>();
        for (Map.Entry<String, List<Content>> e : byModule.entrySet()) {
            Set<String> ids = e.getValue().stream().map(Content::getId).collect(Collectors.toSet());
            boolean done = all.stream().anyMatch(i -> i.isCompleted() && ids.contains(i.getContentId()));
            Integer best = all.stream().filter(i -> ids.contains(i.getContentId()) && i.getScore() != null)
                    .map(Interaction::getScore).max(Integer::compare).orElse(null);
            out.add(new ModuleProgress(e.getKey(), e.getValue().size(), done, best));
        }
        return out;
    }

    private double completionPercent(List<ModuleProgress> modules) {
        if (modules.isEmpty()) return 0;
        long done = modules.stream().filter(ModuleProgress::completed).count();
        return round1(100.0 * done / modules.size());
    }

    private List<String> insights(List<Interaction> all, List<ModuleProgress> modules, double completion) {
        List<String> out = new ArrayList<>();
        Map<ContentFormat, List<Integer>> byFormat = new EnumMap<>(ContentFormat.class);
        for (Interaction i : all) {
            if (i.getScore() != null && i.getFormatUsed() != null) {
                byFormat.computeIfAbsent(i.getFormatUsed(), k -> new ArrayList<>()).add(i.getScore());
            }
        }
        byFormat.entrySet().stream().filter(e -> e.getValue().size() >= 2)
                .max(Comparator.comparingDouble(e -> e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)))
                .ifPresent(e -> out.add("You score best with " + e.getKey() + " content (average "
                        + round1(e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)) + "%)."));

        List<Integer> scores = all.stream().map(Interaction::getScore).filter(Objects::nonNull).collect(Collectors.toList());
        if (scores.size() >= 4) {
            double recent = scores.subList(scores.size() - 2, scores.size()).stream().mapToInt(Integer::intValue).average().orElse(0);
            double earlier = scores.subList(0, scores.size() - 2).stream().mapToInt(Integer::intValue).average().orElse(0);
            if (recent > earlier + 3) out.add("Your recent scores are trending up. Keep going!");
            else if (recent < earlier - 3) out.add("Your recent scores dipped; try an easier format or ask your educator for help.");
        }
        long left = modules.stream().filter(m -> !m.completed()).count();
        if (left > 0) out.add(left + " module(s) left to complete; you are at " + completion
                + "% against an expected " + expectedPercent() + "%.");
        else if (!modules.isEmpty()) out.add("You have completed every module.");
        if (out.isEmpty()) out.add("Complete a few activities to unlock personalised insights.");
        return out;
    }

    private static boolean inRange(Instant t, LocalDate from, LocalDate to) {
        LocalDate d = t.atZone(ZoneOffset.UTC).toLocalDate();
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
