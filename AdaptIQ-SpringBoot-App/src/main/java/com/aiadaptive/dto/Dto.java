package com.aiadaptive.dto;

import com.aiadaptive.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Request/response records used by the REST controllers and services. */
public final class Dto {
    private Dto() {}

    // ----- auth -----
    public record RegisterRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record CreateUserRequest(String name, String email, String password, Role role) {}
    public record UserView(String id, String name, String email, Role role, boolean locked) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isLocked());
        }
    }
    public record LoginResult(String token, Instant expiresAt, UserView user) {}

    // ----- profile -----
    public record ProfileRequest(Set<DisabilityCategory> disabilityCategories, List<String> assistiveTech,
                                 List<ContentFormat> preferredFormats, String goals, Boolean extraTime,
                                 Double fontScale, String theme, Boolean reduceMotion, String colourFilter) {}

    // ----- content / adaptation -----
    public record ValidationReport(boolean passed, List<String> errors, List<String> warnings, Instant checkedAt) {}
    public record UploadResult(Content content, ValidationReport report, boolean published) {}
    public record CaptionSegment(double startSeconds, double endSeconds, String text) {}
    public record AdaptedContent(String contentId, String title, String mode, String text,
                                 String audioScript, Integer estimatedAudioSeconds,
                                 List<CaptionSegment> captions, Double gradeBefore, Double gradeAfter,
                                 String note) {}

    // ----- interactions / recommendations -----
    public record InteractionRequest(String contentId, Integer score, Boolean completed, Integer minutes,
                                     ContentFormat formatUsed) {}
    public record RatingRequest(Rating rating) {}
    public record RecommendationView(Recommendation recommendation, String title, ContentFormat format,
                                     Difficulty difficulty, String moduleId) {}

    // ----- dashboards -----
    public record ModuleProgress(String moduleId, int contentCount, boolean completed, Integer bestScore) {}
    public record ScorePoint(Instant date, int score, String contentTitle) {}
    public record StudentDashboard(String studentId, String name, double completionPercent,
                                   double expectedPercent, Double averageScore, int totalMinutes,
                                   List<ModuleProgress> modules, List<ScorePoint> timeline,
                                   List<String> insights) {}
    public record StudentRow(String studentId, String name, Set<DisabilityCategory> disabilityCategories,
                             double completionPercent, Double averageScore, boolean atRisk,
                             long openAlerts, Instant lastActivity) {}
    public record EducatorDashboard(double expectedPercent, int totalStudents, int atRiskCount,
                                    List<StudentRow> students) {}

    // ----- alerts / feedback -----
    public record AlertAction(String action) {}
    public record FeedbackRequest(String studentId, FeedbackType type, String text, Integer audioSeconds,
                                  String attachmentName) {}
    public record FeedbackView(Feedback feedback, String renderedFormat, String renderedText, String audioScript) {}
    public record AcknowledgeRequest(String response) {}

    // ----- reports / models -----
    public record CategoryStats(int students, Double averageScore, double averageCompletion) {}
    public record PlatformReport(Instant from, Instant to, int totalStudents, int activeStudents,
                                 int totalInteractions, Double averageScore, double averageCompletionPercent,
                                 Map<String, CategoryStats> byDisability, int publishedContent,
                                 int compliantContent, double compliancePercent,
                                 Map<String, Integer> accommodationUsage, int atRiskCount,
                                 List<String> atRiskStudents) {}
    public record CategoryRelevance(String category, int ratedCount, Double relevance, boolean flagged) {}
    public record ModelMetrics(int ratedRecommendations, Double overallRelevance, List<CategoryRelevance> byCategory,
                               Double fairnessGapPoints, boolean biasAlert, int currentVersion,
                               double similarityWeight, double preferenceWeight, List<String> notes) {}
    public record ModelVersion(int version, double similarityWeight, double preferenceWeight, Instant trainedAt,
                               String trainedBy, Double alignment, long durationMs, String changelog) {}
}
