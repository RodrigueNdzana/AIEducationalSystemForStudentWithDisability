package com.aiadaptive.services;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.ProfileRequest;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.LearningProfileRepository;
import com.aiadaptive.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** FR-02 + FR-09: learning profile and accessibility settings. */
@Service
public class ProfileService {

    /** At least five themes, including WCAG AA high-contrast options (FR-09). */
    public static final List<String> THEMES = List.of(
            "default", "high-contrast", "high-contrast-yellow", "dark", "sepia", "colour-blind-safe");
    public static final List<String> COLOUR_FILTERS = List.of("none", "protanopia", "deuteranopia", "tritanopia", "greyscale");

    private final LearningProfileRepository profiles;
    private final UserRepository users;

    public ProfileService(LearningProfileRepository profiles, UserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    public LearningProfile get(String studentId) {
        requireStudent(studentId);
        return profiles.findById(studentId).orElseGet(() -> new LearningProfile(studentId));
    }

    public LearningProfile save(String studentId, ProfileRequest r) {
        requireStudent(studentId);
        LearningProfile p = profiles.findById(studentId).orElseGet(() -> new LearningProfile(studentId));

        if (r.disabilityCategories() != null) {
            Set<DisabilityCategory> cats = r.disabilityCategories().isEmpty()
                    ? EnumSet.of(DisabilityCategory.NONE) : EnumSet.copyOf(r.disabilityCategories());
            if (cats.size() > 1) cats.remove(DisabilityCategory.NONE);
            p.setDisabilityCategories(cats);
        }
        if (r.assistiveTech() != null) p.setAssistiveTech(new ArrayList<>(r.assistiveTech()));
        if (r.preferredFormats() != null) p.setPreferredFormats(new ArrayList<>(r.preferredFormats()));
        if (r.goals() != null) p.setGoals(r.goals().trim());
        if (r.extraTime() != null) p.setExtraTime(r.extraTime());
        if (r.reduceMotion() != null) p.setReduceMotion(r.reduceMotion());
        if (r.fontScale() != null) {
            if (r.fontScale() < 0.8 || r.fontScale() > 2.5) {
                throw ApiException.badRequest("fontScale must be between 0.8 and 2.5");
            }
            p.setFontScale(r.fontScale());
        }
        if (r.theme() != null) {
            if (!THEMES.contains(r.theme())) throw ApiException.badRequest("Unknown theme. Use one of " + THEMES);
            p.setTheme(r.theme());
        }
        if (r.colourFilter() != null) {
            if (!COLOUR_FILTERS.contains(r.colourFilter())) {
                throw ApiException.badRequest("Unknown colour filter. Use one of " + COLOUR_FILTERS);
            }
            p.setColourFilter(r.colourFilter());
        }
        return profiles.save(p);
    }

    private void requireStudent(String studentId) {
        User u = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (u.getRole() != Role.STUDENT) throw ApiException.badRequest("User is not a student");
    }
}
