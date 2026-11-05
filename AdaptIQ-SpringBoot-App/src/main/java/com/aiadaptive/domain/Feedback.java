package com.aiadaptive.domain;

import java.time.Instant;

/** FR-08: educator feedback to a student. For AUDIO feedback {@code text} holds the transcript. */
public class Feedback implements Identifiable {
    private String id;
    private String educatorId;
    private String educatorName;
    private String studentId;
    private FeedbackType type;
    private String text;
    private Integer audioSeconds;
    private String attachmentName;
    private Instant createdAt = Instant.now();
    private boolean acknowledged;
    private String studentResponse;

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    public String getEducatorId() { return educatorId; }
    public void setEducatorId(String educatorId) { this.educatorId = educatorId; }
    public String getEducatorName() { return educatorName; }
    public void setEducatorName(String educatorName) { this.educatorName = educatorName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public FeedbackType getType() { return type; }
    public void setType(FeedbackType type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getAudioSeconds() { return audioSeconds; }
    public void setAudioSeconds(Integer audioSeconds) { this.audioSeconds = audioSeconds; }
    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    public String getStudentResponse() { return studentResponse; }
    public void setStudentResponse(String studentResponse) { this.studentResponse = studentResponse; }
}
