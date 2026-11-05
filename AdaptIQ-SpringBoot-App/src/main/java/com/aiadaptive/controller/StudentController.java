package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.repositories.UserRepository;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.Require;
import com.aiadaptive.services.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Student-facing endpoints: profile (FR-02/09), dashboard (FR-05), recommendations (FR-03), activity, feedback (FR-08). */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final UserRepository users;
    private final ProfileService profiles;
    private final DashboardService dashboards;
    private final RecommendationService recommendations;
    private final InteractionService interactions;
    private final FeedbackService feedback;

    public StudentController(UserRepository users, ProfileService profiles, DashboardService dashboards,
                             RecommendationService recommendations, InteractionService interactions,
                             FeedbackService feedback) {
        this.users = users;
        this.profiles = profiles;
        this.dashboards = dashboards;
        this.recommendations = recommendations;
        this.interactions = interactions;
        this.feedback = feedback;
    }

    @Require({Role.EDUCATOR, Role.DISABILITY_SUPPORT_OFFICER, Role.ADMIN})
    @GetMapping
    public List<UserView> getAll() {
        return users.findByRole(Role.STUDENT).stream().map(UserView::of).collect(Collectors.toList());
    }

    @GetMapping("/{id}/profile")
    public LearningProfile profile(@PathVariable String id, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        return profiles.get(id);
    }

    @PutMapping("/{id}/profile")
    public LearningProfile saveProfile(@PathVariable String id, @RequestBody ProfileRequest r, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        return profiles.save(id, r);
    }

    @GetMapping("/{id}/dashboard")
    public StudentDashboard dashboard(@PathVariable String id,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        return dashboards.student(id, from, to);
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendationView> recommendations(@PathVariable String id, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        List<RecommendationView> existing = recommendations.forStudent(id);
        if (existing.isEmpty()) {
            recommendations.refresh(id);
            return recommendations.forStudent(id);
        }
        return existing;
    }

    @PostMapping("/{id}/recommendations/refresh")
    public List<RecommendationView> refresh(@PathVariable String id, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        recommendations.refresh(id);
        return recommendations.forStudent(id);
    }

    @Require({Role.STUDENT})
    @PostMapping("/{id}/interactions")
    public Interaction logInteraction(@PathVariable String id, @RequestBody InteractionRequest r, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        return interactions.log(id, r);
    }

    @GetMapping("/{id}/feedback")
    public List<FeedbackView> inbox(@PathVariable String id, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        return feedback.inbox(id);
    }

    @Require({Role.STUDENT})
    @PostMapping("/{id}/feedback/{feedbackId}/acknowledge")
    public Feedback acknowledge(@PathVariable String id, @PathVariable String feedbackId,
                                @RequestBody(required = false) AcknowledgeRequest r, @Parameter(hidden = true) AuthContext ctx) {
        ctx.requireAccessToStudent(id);
        return feedback.acknowledge(feedbackId, id, r == null ? null : r.response());
    }
}
