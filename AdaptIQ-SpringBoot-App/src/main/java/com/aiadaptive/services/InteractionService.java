package com.aiadaptive.services;

import com.aiadaptive.domain.Content;
import com.aiadaptive.domain.Interaction;
import com.aiadaptive.dto.Dto.InteractionRequest;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.ContentRepository;
import com.aiadaptive.repositories.InteractionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Records learner activity and triggers recommendation refresh + early-difficulty detection. */
@Service
public class InteractionService {

    private final InteractionRepository interactions;
    private final ContentRepository contentRepo;
    private final RecommendationService recommendations;
    private final AlertService alerts;

    public InteractionService(InteractionRepository interactions, ContentRepository contentRepo,
                              RecommendationService recommendations, AlertService alerts) {
        this.interactions = interactions;
        this.contentRepo = contentRepo;
        this.recommendations = recommendations;
        this.alerts = alerts;
    }

    public Interaction log(String studentId, InteractionRequest r) {
        if (r == null || r.contentId() == null) throw ApiException.badRequest("contentId is required");
        Content c = contentRepo.findById(r.contentId()).orElseThrow(() -> ApiException.notFound("Content"));
        if (r.score() != null && (r.score() < 0 || r.score() > 100)) {
            throw ApiException.badRequest("score must be between 0 and 100");
        }
        if (r.minutes() != null && r.minutes() < 0) throw ApiException.badRequest("minutes cannot be negative");

        Interaction i = new Interaction();
        i.setStudentId(studentId);
        i.setContentId(c.getId());
        i.setModuleId(c.getModuleId());
        i.setScore(r.score());
        i.setCompleted(Boolean.TRUE.equals(r.completed()));
        i.setMinutes(r.minutes() == null ? 0 : r.minutes());
        i.setFormatUsed(r.formatUsed() == null ? c.getFormat() : r.formatUsed());
        i = interactions.save(i);

        alerts.evaluate(studentId);
        if (i.isCompleted()) recommendations.refresh(studentId);   // refresh as soon as a module is completed
        return i;
    }

    public List<Interaction> activity(String studentId) {
        return interactions.findByStudent(studentId);
    }
}
