package com.aiadaptive.domain;

import java.time.Instant;

/** A learner activity event: viewing/completing content, optionally with an assessment score. */
public class Interaction implements Identifiable {
    private String id;
    private String studentId;
    private String contentId;
    private String moduleId;
    private Integer score;          // 0-100, null when not assessed
    private boolean completed;
    private int minutes;
    private ContentFormat formatUsed;
    private Instant timestamp = Instant.now();

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public String getModuleId() { return moduleId; }
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public int getMinutes() { return minutes; }
    public void setMinutes(int minutes) { this.minutes = minutes; }
    public ContentFormat getFormatUsed() { return formatUsed; }
    public void setFormatUsed(ContentFormat formatUsed) { this.formatUsed = formatUsed; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
