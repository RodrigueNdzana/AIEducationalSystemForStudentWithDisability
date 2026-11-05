package com.aiadaptive.domain;

/** Anything stored in an in-memory repository. */
public interface Identifiable {
    String getId();
    void setId(String id);
}
