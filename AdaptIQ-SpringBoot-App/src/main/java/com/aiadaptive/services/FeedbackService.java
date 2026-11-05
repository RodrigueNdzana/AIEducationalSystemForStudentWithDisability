package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.FeedbackRequest;
import com.aiadaptive.dto.Dto.FeedbackView;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.FeedbackRepository;
import com.aiadaptive.repositories.LearningProfileRepository;
import com.aiadaptive.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** FR-08: educator feedback delivered to students in their preferred accessible format. */
@Service
public class FeedbackService {

    public static final int MAX_AUDIO_SECONDS = 300;   // 5 minutes (DEF-003 fix: enforced server-side)

    private final FeedbackRepository feedback;
    private final UserRepository users;
    private final LearningProfileRepository profiles;
    private final NotificationService notifications;
    private final ContentAdaptationService adaptation;

    public FeedbackService(FeedbackRepository feedback, UserRepository users, LearningProfileRepository profiles,
                           NotificationService notifications, ContentAdaptationService adaptation) {
        this.feedback = feedback;
        this.users = users;
        this.profiles = profiles;
        this.notifications = notifications;
        this.adaptation = adaptation;
    }

    public Feedback submit(String educatorId, FeedbackRequest r) {
        if (r == null || r.studentId() == null || r.type() == null) {
            throw ApiException.badRequest("studentId and type are required");
        }
        User student = users.findById(r.studentId()).orElseThrow(() -> ApiException.notFound("Student"));
        if (student.getRole() != Role.STUDENT) throw ApiException.badRequest("Feedback can only be sent to students");
        User educator = users.findById(educatorId).orElseThrow(() -> ApiException.notFound("Educator"));

        switch (r.type()) {
            case TEXT:
                if (r.text() == null || r.text().isBlank()) throw ApiException.badRequest("Feedback text is required");
                break;
            case AUDIO:
                if (r.audioSeconds() == null || r.audioSeconds() <= 0) {
                    throw ApiException.badRequest("audioSeconds (recording length) is required for audio feedback");
                }
                if (r.audioSeconds() > MAX_AUDIO_SECONDS) {
                    throw ApiException.badRequest("Audio feedback is limited to 5 minutes (300 seconds)");
                }
                if (r.text() == null || r.text().isBlank()) {
                    throw ApiException.badRequest("Audio feedback needs a transcript in 'text' so every student can access it");
                }
                break;
            case FILE:
                if (r.attachmentName() == null || r.attachmentName().isBlank()) {
                    throw ApiException.badRequest("attachmentName is required for file feedback");
                }
                break;
            default:
                break;
        }

        Feedback f = new Feedback();
        f.setEducatorId(educatorId);
        f.setEducatorName(educator.getName());
        f.setStudentId(r.studentId());
        f.setType(r.type());
        f.setText(r.text());
        f.setAudioSeconds(r.audioSeconds());
        f.setAttachmentName(r.attachmentName());
        f = feedback.save(f);

        notifications.notifyWithEmail(r.studentId(), "New " + r.type().name().toLowerCase()
                + " feedback from " + educator.getName() + ".");
        return f;
    }

    public List<FeedbackView> inbox(String studentId) {
        LearningProfile p = profiles.findById(studentId).orElseGet(() -> new LearningProfile(studentId));
        return feedback.findByStudent(studentId).stream()
                .sorted(Comparator.comparing(Feedback::getCreatedAt).reversed())
                .map(f -> render(f, p)).collect(Collectors.toList());
    }

    /** Chooses the delivery format from the student's accessibility profile. */
    FeedbackView render(Feedback f, LearningProfile p) {
        String text = f.getText() == null ? "" : f.getText();
        if (p.getDisabilityCategories().contains(DisabilityCategory.VISUAL)
                || p.getPreferredFormats().contains(ContentFormat.AUDIO)) {
            return new FeedbackView(f, "AUDIO_TTS", text, adaptation.audioScript(text));
        }
        if (p.getDisabilityCategories().contains(DisabilityCategory.COGNITIVE)) {
            return new FeedbackView(f, "SIMPLIFIED_TEXT", adaptation.simplify(text).simplified(), null);
        }
        if (p.getDisabilityCategories().contains(DisabilityCategory.HEARING)) {
            return new FeedbackView(f, "TEXT_TRANSCRIPT", text, null);
        }
        return new FeedbackView(f, "TEXT", text, null);
    }

    public Feedback acknowledge(String feedbackId, String studentId, String response) {
        Feedback f = feedback.findById(feedbackId).orElseThrow(() -> ApiException.notFound("Feedback"));
        if (!f.getStudentId().equals(studentId)) throw ApiException.forbidden("Not your feedback");
        f.setAcknowledged(true);
        if (response != null && !response.isBlank()) {
            f.setStudentResponse(response.trim());
            notifications.notify(f.getEducatorId(), "A student responded to your feedback: " + response.trim());
        }
        return feedback.save(f);
    }
}
