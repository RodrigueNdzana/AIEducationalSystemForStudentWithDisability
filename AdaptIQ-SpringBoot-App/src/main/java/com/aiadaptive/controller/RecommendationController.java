package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.Recommendation;
import com.aiadaptive.domain.Role;
import com.aiadaptive.dto.Dto.RatingRequest;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.Require;
import com.aiadaptive.services.RecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    /** Students see only their own recommendations; staff see all. */
    @GetMapping
    public List<Recommendation> getAll(@Parameter(hidden = true) AuthContext ctx) {
        List<Recommendation> all = service.getAllRecommendations();
        if (ctx.role() == Role.STUDENT) {
            return all.stream().filter(r -> ctx.userId().equals(r.getStudentId())).collect(Collectors.toList());
        }
        if (!ctx.isStaff()) throw com.aiadaptive.exception.ApiException.forbidden("Not allowed");
        return all;
    }

    /** Thumbs up / down on a recommendation (feeds retraining). */
    @Require({Role.STUDENT})
    @PostMapping("/{id}/rating")
    public Recommendation rate(@PathVariable String id, @RequestBody RatingRequest r, @Parameter(hidden = true) AuthContext ctx) {
        return service.rate(id, ctx.userId(), r.rating());
    }
}
