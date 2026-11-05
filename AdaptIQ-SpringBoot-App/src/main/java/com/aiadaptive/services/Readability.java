package com.aiadaptive.services;

import java.util.ArrayList;
import java.util.List;

/** Flesch-Kincaid grade-level maths used by text simplification and content validation. */
public final class Readability {
    private Readability() {}

    public static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String s : text.trim().split("(?<=[.!?])\\s+")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : text.split("\\s+")) {
            String cleaned = w.replaceAll("[^A-Za-z']", "");
            if (!cleaned.isEmpty()) out.add(cleaned);
        }
        return out;
    }

    public static int syllables(String word) {
        String w = word.toLowerCase().replaceAll("[^a-z]", "");
        if (w.isEmpty()) return 0;
        int count = 0;
        boolean prevVowel = false;
        for (char c : w.toCharArray()) {
            boolean v = "aeiouy".indexOf(c) >= 0;
            if (v && !prevVowel) count++;
            prevVowel = v;
        }
        if (w.endsWith("e") && !w.endsWith("le") && count > 1) count--;
        return Math.max(1, count);
    }

    /** Flesch-Kincaid grade level; 0 for empty text. */
    public static double gradeLevel(String text) {
        List<String> words = words(text);
        List<String> sentences = sentences(text);
        if (words.isEmpty() || sentences.isEmpty()) return 0;
        int syl = 0;
        for (String w : words) syl += syllables(w);
        double g = 0.39 * ((double) words.size() / sentences.size())
                + 11.8 * ((double) syl / words.size()) - 15.59;
        return Math.max(0, Math.round(g * 10.0) / 10.0);
    }
}
