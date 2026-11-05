package com.aiadaptive.domain;

import java.time.Instant;

/** FR-07: early-difficulty alert raised for educators. */
public class Alert implements Identifiable {
    private String id;
    private String studentId;
    private String studentName;
    private AlertType type;
    private String message;
    private AlertStatus status = AlertStatus.OPEN;
    private Instant createdAt = Instant.now();
    private String resolvedBy;

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public AlertType getType() { return type; }
    public void setType(AlertType type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
