package com.aiadaptive.domain;

import java.util.ArrayList;
import java.util.List;

/** A learning resource (FR-04, FR-10). Accessibility metadata drives the hard accessibility filter. */
public class Content implements Identifiable {
    private String id;
    private String title;
    private ContentFormat format = ContentFormat.TEXT;
    private Difficulty difficulty = Difficulty.BEGINNER;
    private String course = "CS101";
    private String moduleId;
    private String topic = "";
    private String body = "";
    private String transcript = "";
    private String fileName;
    private boolean hasCaptions;
    private boolean hasTranscript;
    private boolean hasAltText;
    private boolean hasImages;
    private boolean hasAudioNarration;
    private boolean requiresFineMotor;
    private boolean published;
    private List<String> tags = new ArrayList<>();
    private String uploadedBy;

    public Content() {}

    public Content(String id, String title, String format, String difficulty) {
        this.id = id;
        this.title = title;
        this.format = ContentFormat.valueOf(format.toUpperCase());
        this.difficulty = Difficulty.valueOf(difficulty.toUpperCase());
    }

    /** Non-bean helpers (deliberately not named getX/isX so they are not serialised). */
    public boolean captionsAvailable() { return hasCaptions; }
    public boolean transcriptAvailable() {
        return hasTranscript || (transcript != null && !transcript.isBlank());
    }

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ContentFormat getFormat() { return format; }
    public void setFormat(ContentFormat format) { this.format = format; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }
    public String getModuleId() { return moduleId; }
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public boolean isHasCaptions() { return hasCaptions; }
    public void setHasCaptions(boolean v) { this.hasCaptions = v; }
    public boolean isHasTranscript() { return hasTranscript; }
    public void setHasTranscript(boolean v) { this.hasTranscript = v; }
    public boolean isHasAltText() { return hasAltText; }
    public void setHasAltText(boolean v) { this.hasAltText = v; }
    public boolean isHasImages() { return hasImages; }
    public void setHasImages(boolean v) { this.hasImages = v; }
    public boolean isHasAudioNarration() { return hasAudioNarration; }
    public void setHasAudioNarration(boolean v) { this.hasAudioNarration = v; }
    public boolean isRequiresFineMotor() { return requiresFineMotor; }
    public void setRequiresFineMotor(boolean v) { this.requiresFineMotor = v; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
