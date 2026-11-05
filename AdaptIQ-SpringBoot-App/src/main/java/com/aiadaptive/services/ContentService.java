package com.aiadaptive.services;

import com.aiadaptive.domain.Content;
import com.aiadaptive.dto.Dto.UploadResult;
import com.aiadaptive.dto.Dto.ValidationReport;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.ContentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** FR-10: content management with the automated accessibility gate. */
@Service
public class ContentService {

    private final ContentRepository contentRepo;
    private final AccessibilityValidator validator;

    public ContentService(ContentRepository contentRepo, AccessibilityValidator validator) {
        this.contentRepo = contentRepo;
        this.validator = validator;
    }

    public Content createContent(Content content) {
        return contentRepo.save(content);
    }

    public List<Content> getAllContent() {
        return contentRepo.findAll();
    }

    public List<Content> getPublishedContent() {
        return contentRepo.findPublished();
    }

    public Content get(String id) {
        return contentRepo.findById(id).orElseThrow(() -> ApiException.notFound("Content"));
    }

    public ValidationReport validate(Content c) {
        return validator.validate(c);
    }

    /**
     * Validates and publishes. Content that fails WCAG checks is blocked (HTTP 422) unless an
     * administrator overrides.
     */
    public UploadResult upload(Content c, String uploadedBy, boolean adminOverride) {
        ValidationReport report = validator.validate(c);
        if (!report.passed() && !adminOverride) {
            throw new ApiException(422, "Content failed the WCAG 2.1 AA accessibility check and cannot be published", report);
        }
        c.setId(null);
        c.setUploadedBy(uploadedBy);
        c.setPublished(true);
        if (c.getTags() == null) c.setTags(new ArrayList<>());
        return new UploadResult(contentRepo.save(c), report, true);
    }

    public Content updateContent(String id, Content updated) {
        Content existing = get(id);
        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getFormat() != null) existing.setFormat(updated.getFormat());
        if (updated.getDifficulty() != null) existing.setDifficulty(updated.getDifficulty());
        if (updated.getBody() != null) existing.setBody(updated.getBody());
        if (updated.getTranscript() != null) existing.setTranscript(updated.getTranscript());
        if (updated.getTopic() != null) existing.setTopic(updated.getTopic());
        if (updated.getModuleId() != null) existing.setModuleId(updated.getModuleId());
        existing.setHasCaptions(updated.isHasCaptions());
        existing.setHasTranscript(updated.isHasTranscript());
        existing.setHasAltText(updated.isHasAltText());
        existing.setHasImages(updated.isHasImages());
        existing.setHasAudioNarration(updated.isHasAudioNarration());
        existing.setRequiresFineMotor(updated.isRequiresFineMotor());
        if (existing.isPublished()) {
            ValidationReport r = validator.validate(existing);
            if (!r.passed()) {
                throw new ApiException(422, "The update would make published content inaccessible", r);
            }
        }
        return contentRepo.save(existing);
    }

    public void deleteContent(String id) {
        get(id);
        contentRepo.delete(id);
    }
}
