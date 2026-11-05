package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.CategoryStats;
import com.aiadaptive.dto.Dto.PlatformReport;
import com.aiadaptive.repositories.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** FR-11: platform-wide engagement, accessibility-compliance, at-risk and usage reporting with CSV/PDF export. */
@Service
public class ReportService {

    private final UserRepository users;
    private final InteractionRepository interactions;
    private final ContentRepository contentRepo;
    private final LearningProfileRepository profiles;
    private final AccessibilityValidator validator;
    private final DashboardService dashboards;

    public ReportService(UserRepository users, InteractionRepository interactions, ContentRepository contentRepo,
                         LearningProfileRepository profiles, AccessibilityValidator validator,
                         DashboardService dashboards) {
        this.users = users;
        this.interactions = interactions;
        this.contentRepo = contentRepo;
        this.profiles = profiles;
        this.validator = validator;
        this.dashboards = dashboards;
    }

    public PlatformReport build(Instant from, Instant to) {
        List<User> students = users.findByRole(Role.STUDENT);
        List<Interaction> inRange = interactions.findAll().stream()
                .filter(i -> (from == null || !i.getTimestamp().isBefore(from)) && (to == null || !i.getTimestamp().isAfter(to)))
                .collect(Collectors.toList());

        Set<String> active = inRange.stream().map(Interaction::getStudentId).collect(Collectors.toSet());
        Double avg = avgScore(inRange);

        Map<String, Double> completion = new HashMap<>();
        for (User s : students) completion.put(s.getId(), dashboards.completionFor(s.getId()));
        double avgCompletion = completion.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        Map<String, CategoryStats> byCat = new LinkedHashMap<>();
        for (DisabilityCategory cat : DisabilityCategory.values()) {
            List<String> ids = students.stream().filter(s -> profiles.findById(s.getId())
                    .map(LearningProfile::getDisabilityCategories).orElse(EnumSet.of(DisabilityCategory.NONE)).contains(cat))
                    .map(User::getId).collect(Collectors.toList());
            if (ids.isEmpty()) continue;
            List<Interaction> mine = inRange.stream().filter(i -> ids.contains(i.getStudentId())).collect(Collectors.toList());
            double comp = ids.stream().mapToDouble(completion::get).average().orElse(0);
            byCat.put(cat.name(), new CategoryStats(ids.size(), avgScore(mine), round1(comp)));
        }

        List<Content> published = contentRepo.findPublished();
        int compliant = (int) published.stream().filter(c -> validator.validate(c).passed()).count();

        Map<String, Integer> usage = new TreeMap<>();
        for (User s : students) {
            profiles.findById(s.getId()).ifPresent(p -> {
                for (String t : p.getAssistiveTech()) usage.merge("assistive:" + t, 1, Integer::sum);
                usage.merge("theme:" + p.getTheme(), 1, Integer::sum);
                if (p.isExtraTime()) usage.merge("accommodation:extra-time", 1, Integer::sum);
                if (p.isReduceMotion()) usage.merge("accommodation:reduce-motion", 1, Integer::sum);
            });
        }

        List<String> atRisk = students.stream().filter(s -> dashboards.isAtRisk(completion.get(s.getId())))
                .map(User::getName).sorted().collect(Collectors.toList());

        return new PlatformReport(from, to, students.size(), active.size(), inRange.size(), avg, round1(avgCompletion),
                byCat, published.size(), compliant,
                published.isEmpty() ? 100.0 : round1(100.0 * compliant / published.size()),
                usage, atRisk.size(), atRisk);
    }

    public String toCsv(PlatformReport r) {
        StringBuilder sb = new StringBuilder("section,metric,value\n");
        row(sb, "engagement", "total_students", r.totalStudents());
        row(sb, "engagement", "active_students", r.activeStudents());
        row(sb, "engagement", "total_interactions", r.totalInteractions());
        row(sb, "engagement", "average_score", r.averageScore() == null ? "n/a" : r.averageScore());
        row(sb, "engagement", "average_completion_percent", r.averageCompletionPercent());
        for (Map.Entry<String, CategoryStats> e : r.byDisability().entrySet()) {
            row(sb, "disability:" + e.getKey(), "students", e.getValue().students());
            row(sb, "disability:" + e.getKey(), "average_score", e.getValue().averageScore() == null ? "n/a" : e.getValue().averageScore());
            row(sb, "disability:" + e.getKey(), "average_completion_percent", e.getValue().averageCompletion());
        }
        row(sb, "accessibility", "published_content", r.publishedContent());
        row(sb, "accessibility", "wcag_compliant_content", r.compliantContent());
        row(sb, "accessibility", "compliance_percent", r.compliancePercent());
        for (Map.Entry<String, Integer> e : r.accommodationUsage().entrySet()) row(sb, "usage", e.getKey(), e.getValue());
        row(sb, "at_risk", "count", r.atRiskCount());
        for (String n : r.atRiskStudents()) row(sb, "at_risk", "student", n);
        return sb.toString();
    }

    public byte[] toPdf(PlatformReport r) {
        List<String> lines = new ArrayList<>();
        lines.add("Period: " + (r.from() == null ? "start" : r.from()) + " to " + (r.to() == null ? "now" : r.to()));
        lines.add("");
        lines.add("ENGAGEMENT");
        lines.add("Students: " + r.totalStudents() + " (active in period: " + r.activeStudents() + ")");
        lines.add("Interactions: " + r.totalInteractions() + "   Average score: " + (r.averageScore() == null ? "n/a" : r.averageScore() + "%"));
        lines.add("Average module completion: " + r.averageCompletionPercent() + "%");
        lines.add("");
        lines.add("BY DISABILITY CATEGORY");
        for (Map.Entry<String, CategoryStats> e : r.byDisability().entrySet()) {
            lines.add(e.getKey() + ": " + e.getValue().students() + " students, avg score "
                    + (e.getValue().averageScore() == null ? "n/a" : e.getValue().averageScore() + "%")
                    + ", completion " + e.getValue().averageCompletion() + "%");
        }
        lines.add("");
        lines.add("ACCESSIBILITY COMPLIANCE");
        lines.add("Published content: " + r.publishedContent() + ", WCAG-compliant: " + r.compliantContent()
                + " (" + r.compliancePercent() + "%)");
        lines.add("");
        lines.add("ACCOMMODATION AND ASSISTIVE-TECH USAGE");
        for (Map.Entry<String, Integer> e : r.accommodationUsage().entrySet()) lines.add(e.getKey() + ": " + e.getValue());
        lines.add("");
        lines.add("AT-RISK STUDENTS (" + r.atRiskCount() + ")");
        for (String n : r.atRiskStudents()) lines.add("- " + n);
        return SimplePdf.write("AdaptIQ Platform Report", lines);
    }

    private static void row(StringBuilder sb, String section, String metric, Object value) {
        sb.append(csv(section)).append(',').append(csv(metric)).append(',').append(csv(String.valueOf(value))).append('\n');
    }

    private static String csv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static Double avgScore(List<Interaction> list) {
        OptionalDouble d = list.stream().filter(i -> i.getScore() != null).mapToInt(Interaction::getScore).average();
        return d.isPresent() ? round1(d.getAsDouble()) : null;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
