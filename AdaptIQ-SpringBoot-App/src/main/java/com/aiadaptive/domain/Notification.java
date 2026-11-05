package com.aiadaptive.domain;

import java.time.Instant;

public class Notification implements Identifiable {
    private String id;
    private String userId;
    private String message;
    private String channel = "IN_APP";
    private Instant createdAt = Instant.now();
    private boolean read;

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
