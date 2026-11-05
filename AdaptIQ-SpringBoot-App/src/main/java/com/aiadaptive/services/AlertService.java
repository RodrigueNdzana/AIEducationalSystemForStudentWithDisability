package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.AlertRepository;
import com.aiadaptive.repositories.InteractionRepository;
import com.aiadaptive.repositories.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * FR-07: early-difficulty detection. A student is flagged when the mean of their last three assessed
 * scores is more than 15% below their personal baseline (all earlier scores), or when they have been
 * inactive for 14 days. Evaluated immediately after each interaction and hourly, so alerts reach
 * educators well inside the 48-hour requirement.
 */
@Service
public class AlertService {

    public static final double DROP_THRESHOLD = 0.15;
    public static final int RECENT_WINDOW = 3;
    public static final int MIN_BASELINE_SCORES = 2;
    public static final Duration INACTIVITY = Duration.ofDays(14);

    private final AlertRepository alerts;
    private final InteractionRepository interactions;
    private final UserRepository users;
    private final NotificationService notifications;

    public AlertService(AlertRepository alerts, InteractionRepository interactions, UserRepository users,
                        NotificationService notifications) {
        this.alerts = alerts;
        this.interactions = interactions;
        this.users = users;
        this.notifications = notifications;
    }

    @Scheduled(fixedRate = 3_600_000L, initialDelay = 60_000L)
    public void scanAll() {
        for (User s : users.findByRole(Role.STUDENT)) evaluate(s.getId());
    }

    /** Returns any alerts newly raised for this student. */
    public List<Alert> evaluate(String studentId) {
        User student = users.findById(studentId).orElse(null);
        if (student == null) return List.of();
        List<Interaction> all = interactions.findByStudent(studentId);
        List<Alert> raised = new java.util.ArrayList<>();

        List<Integer> scores = all.stream().map(Interaction::getScore).filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (scores.size() >= RECENT_WINDOW + MIN_BASELINE_SCORES) {
            List<Integer> recent = scores.subList(scores.size() - RECENT_WINDOW, scores.size());
            List<Integer> base = scores.subList(0, scores.size() - RECENT_WINDOW);
            double recentAvg = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
            double baseAvg = base.stream().mapToInt(Integer::intValue).average().orElse(0);
            if (baseAvg > 0 && recentAvg < baseAvg * (1 - DROP_THRESHOLD)) {
                double dropPct = (baseAvg - recentAvg) / baseAvg * 100.0;
                String msg = String.format(Locale.ROOT,
                        "Average of last %d scores (%.1f%%) is %.1f%% below %s's personal baseline (%.1f%%).",
                        RECENT_WINDOW, recentAvg, dropPct, student.getName(), baseAvg);
                raise(student, AlertType.PERFORMANCE_DROP, msg).ifPresent(raised::add);
            }
        }

        if (!all.isEmpty()) {
            Instant last = all.get(all.size() - 1).getTimestamp();
            if (last.isBefore(Instant.now().minus(INACTIVITY))) {
                long days = Duration.between(last, Instant.now()).toDays();
                raise(student, AlertType.LOW_ENGAGEMENT,
                        student.getName() + " has had no learning activity for " + days + " days.")
                        .ifPresent(raised::add);
            }
        }
        return raised;
    }

    private java.util.Optional<Alert> raise(User student, AlertType type, String message) {
        boolean open = alerts.findAll().stream().anyMatch(a -> a.getStudentId().equals(student.getId())
                && a.getType() == type && a.getStatus() == AlertStatus.OPEN);
        if (open) return java.util.Optional.empty();
        Alert a = new Alert();
        a.setStudentId(student.getId());
        a.setStudentName(student.getName());
        a.setType(type);
        a.setMessage(message);
        a = alerts.save(a);
        notifications.notifyRole(Role.EDUCATOR, "Early-difficulty alert: " + message, true);
        return java.util.Optional.of(a);
    }

    public List<Alert> list(AlertStatus status) {
        return alerts.findAll().stream()
                .filter(a -> status == null || a.getStatus() == status)
                .sorted(Comparator.comparing(Alert::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** Acknowledge, dismiss or escalate with a single action. */
    public Alert act(String alertId, String action, String userId) {
        Alert a = alerts.findById(alertId).orElseThrow(() -> ApiException.notFound("Alert"));
        if (action == null) throw ApiException.badRequest("action is required");
        switch (action.toUpperCase()) {
            case "ACKNOWLEDGE":
                a.setStatus(AlertStatus.ACKNOWLEDGED);
                break;
            case "DISMISS":
                a.setStatus(AlertStatus.DISMISSED);
                break;
            case "ESCALATE":
                a.setStatus(AlertStatus.ESCALATED);
                notifications.notifyRole(Role.DISABILITY_SUPPORT_OFFICER, "Escalated alert: " + a.getMessage(), true);
                notifications.notifyRole(Role.ADMIN, "Escalated alert: " + a.getMessage(), false);
                break;
            default:
                throw ApiException.badRequest("action must be ACKNOWLEDGE, DISMISS or ESCALATE");
        }
        a.setResolvedBy(userId);
        return alerts.save(a);
    }

    public long openCountFor(String studentId) {
        return alerts.findAll().stream()
                .filter(a -> a.getStudentId().equals(studentId) && a.getStatus() == AlertStatus.OPEN).count();
    }
}
