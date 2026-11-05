package com.aiadaptive.services;

import com.aiadaptive.domain.Content;
import com.aiadaptive.domain.ContentFormat;
import com.aiadaptive.dto.Dto.ValidationReport;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** FR-10: automated accessibility gate (WCAG 2.1 AA oriented checks) applied before content is published. */
@Component
public class AccessibilityValidator {

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "mp4", "mp3", "html");

    public ValidationReport validate(Content c) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (c.getTitle() == null || c.getTitle().isBlank()) errors.add("A title is required (WCAG 2.4.2).");
        if (c.getModuleId() == null || c.getModuleId().isBlank()) errors.add("A module id is required so progress can be tracked.");
        if (c.getDifficulty() == null) errors.add("A difficulty level is required.");
        if (c.getFormat() == null) errors.add("A content format is required.");

        if (c.getFileName() != null && !c.getFileName().isBlank()) {
            int dot = c.getFileName().lastIndexOf('.');
            String ext = dot < 0 ? "" : c.getFileName().substring(dot + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                errors.add("Unsupported file type '." + ext + "'. Allowed: PDF, DOCX, MP4, MP3, HTML.");
            } else if (c.getFormat() != null) {
                if (ext.equals("mp4") && c.getFormat() != ContentFormat.VIDEO) errors.add("An MP4 file must have format VIDEO.");
                if (ext.equals("mp3") && c.getFormat() != ContentFormat.AUDIO) errors.add("An MP3 file must have format AUDIO.");
            }
        }

        if (c.getFormat() == ContentFormat.VIDEO && !c.captionsAvailable()) {
            errors.add("Video must include captions (WCAG 1.2.2).");
        }
        if (c.getFormat() == ContentFormat.AUDIO && !c.transcriptAvailable()) {
            errors.add("Audio must include a transcript (WCAG 1.2.1).");
        }
        if (c.isHasImages() && !c.isHasAltText()) {
            errors.add("Images must have descriptive alt text (WCAG 1.1.1).");
        }
        if (c.getFormat() == ContentFormat.INTERACTIVE && !c.transcriptAvailable()) {
            warnings.add("Interactive content should provide a text alternative for keyboard and screen-reader users.");
        }
        if (c.getFormat() == ContentFormat.VIDEO && !c.isHasAudioNarration()) {
            warnings.add("Video without audio description is not fully usable by blind students.");
        }
        if (c.getBody() != null && !c.getBody().isBlank()) {
            double grade = Readability.gradeLevel(c.getBody());
            if (grade > 12) warnings.add("Reading level is grade " + grade + "; consider simplifying (WCAG 3.1.5).");
        }
        return new ValidationReport(errors.isEmpty(), errors, warnings, Instant.now());
    }
}
