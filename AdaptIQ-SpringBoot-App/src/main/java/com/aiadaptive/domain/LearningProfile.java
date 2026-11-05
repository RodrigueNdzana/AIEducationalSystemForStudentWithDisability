package com.aiadaptive.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** FR-02 / FR-09: learning profile plus accessibility and display settings. The id is the student id. */
public class LearningProfile implements Identifiable {
    private String studentId;
    private Set<DisabilityCategory> disabilityCategories = EnumSet.of(DisabilityCategory.NONE);
    private List<String> assistiveTech = new ArrayList<>();
    private List<ContentFormat> preferredFormats = new ArrayList<>();
    private String goals = "";
    private boolean extraTime;
    private double fontScale = 1.0;
    private String theme = "default";
    private boolean reduceMotion;
    private String colourFilter = "none";

    public LearningProfile() {}
    public LearningProfile(String studentId) { this.studentId = studentId; }

    @Override public String getId() { return studentId; }
    @Override public void setId(String id) { this.studentId = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public Set<DisabilityCategory> getDisabilityCategories() { return disabilityCategories; }
    public void setDisabilityCategories(Set<DisabilityCategory> c) { this.disabilityCategories = c; }
    public List<String> getAssistiveTech() { return assistiveTech; }
    public void setAssistiveTech(List<String> assistiveTech) { this.assistiveTech = assistiveTech; }
    public List<ContentFormat> getPreferredFormats() { return preferredFormats; }
    public void setPreferredFormats(List<ContentFormat> p) { this.preferredFormats = p; }
    public String getGoals() { return goals; }
    public void setGoals(String goals) { this.goals = goals; }
    public boolean isExtraTime() { return extraTime; }
    public void setExtraTime(boolean extraTime) { this.extraTime = extraTime; }
    public double getFontScale() { return fontScale; }
    public void setFontScale(double fontScale) { this.fontScale = fontScale; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public boolean isReduceMotion() { return reduceMotion; }
    public void setReduceMotion(boolean reduceMotion) { this.reduceMotion = reduceMotion; }
    public String getColourFilter() { return colourFilter; }
    public void setColourFilter(String colourFilter) { this.colourFilter = colourFilter; }
}
