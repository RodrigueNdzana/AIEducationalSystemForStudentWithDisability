package com.aiadaptive.domain;

import java.time.Instant;

/** FR-03: an AI recommendation with a 0-100 match score and the score components used (for retraining/audit). */
public class Recommendation implements Identifiable {
    private String id;
    private String studentId;
    private String contentId;
    private int matchScore;         // 0-100
    private double similarityScore; // collaborative component, 0-1
    private double preferenceScore; // content-based component, 0-1
    private Rating rating;          // thumbs up / down, null until rated
    private String reason;
    private int modelVersion;
    private boolean archived;       // rated recommendations from earlier batches are kept for training/audit
    private Instant createdAt = Instant.now();

    public Recommendation() {}

    public Recommendation(String id, String studentId, String contentId, int matchScore) {
        this.id = id;
        this.studentId = studentId;
        this.contentId = contentId;
        this.matchScore = matchScore;
    }

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }
    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double s) { this.similarityScore = s; }
    public double getPreferenceScore() { return preferenceScore; }
    public void setPreferenceScore(double s) { this.preferenceScore = s; }
    public Rating getRating() { return rating; }
    public void setRating(Rating rating) { this.rating = rating; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getModelVersion() { return modelVersion; }
    public void setModelVersion(int modelVersion) { this.modelVersion = modelVersion; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
