package com.aiadaptive.domain;

public enum Difficulty {
    BEGINNER(1), INTERMEDIATE(2), ADVANCED(3);

    private final int level;
    Difficulty(int level) { this.level = level; }
    public int level() { return level; }
}
