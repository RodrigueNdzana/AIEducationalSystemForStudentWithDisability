package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.Alert;
import com.aiadaptive.domain.AlertStatus;
import com.aiadaptive.domain.Feedback;
import com.aiadaptive.domain.Interaction;
import com.aiadaptive.domain.Role;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.Require;
import com.aiadaptive.services.AlertService;
import com.aiadaptive.services.DashboardService;
import com.aiadaptive.services.FeedbackService;
import com.aiadaptive.services.InteractionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-06 monitoring dashboard, FR-07 alerts, FR-08 feedback (educator side). */
@RestController
@RequestMapping("/api/educator")
@Require({Role.EDUCATOR, Role.DISABILITY_SUPPORT_OFFICER, Role.ADMIN})
public class EducatorController {

    private final DashboardService dashboards;
    private final AlertService alerts;
    private final FeedbackService feedback;
    private final InteractionService interactions;

    public EducatorController(DashboardService dashboards, AlertService alerts, FeedbackService feedback,
                              InteractionService interactions) {
        this.dashboards = dashboards;
        this.alerts = alerts;
        this.feedback = feedback;
        this.interactions = interactions;
    }

    @GetMapping("/dashboard")
    public EducatorDashboard dashboard(@RequestParam(required = false) String course,
                                       @RequestParam(required = false) String disability,
                                       @RequestParam(required = false) String progressLevel,
                                       @RequestParam(required = false) Boolean riskOnly) {
        return dashboards.educator(course, disability, progressLevel, riskOnly);
    }

    @GetMapping("/students/{id}/activity")
    public List<Interaction> activity(@PathVariable String id) {
        return interactions.activity(id);
    }

    @GetMapping("/alerts")
    public List<Alert> alerts(@RequestParam(required = false) String status) {
        AlertStatus s = null;
        if (status != null && !status.isBlank()) {
            try {
                s = AlertStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown status: " + status);
            }
        }
        return alerts.list(s);
    }

    @PatchMapping("/alerts/{id}")
    public Alert act(@PathVariable String id, @RequestBody AlertAction a, @Parameter(hidden = true) AuthContext ctx) {
        return alerts.act(id, a.action(), ctx.userId());
    }

    @Require({Role.EDUCATOR, Role.ADMIN})
    @PostMapping("/feedback")
    public Feedback submitFeedback(@RequestBody FeedbackRequest r, @Parameter(hidden = true) AuthContext ctx) {
        return feedback.submit(ctx.userId(), r);
    }
}
