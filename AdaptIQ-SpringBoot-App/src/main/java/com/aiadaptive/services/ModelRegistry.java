package com.aiadaptive.services;

import com.aiadaptive.dto.Dto.ModelVersion;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** FR-12: versioned registry of recommender weights with changelogs (traceability). */
@Component
public class ModelRegistry {

    private final List<ModelVersion> versions = new ArrayList<>();

    public ModelRegistry() {
        versions.add(new ModelVersion(1, 0.6, 0.4, Instant.now(), "system", null, 0,
                "Initial weights from the design phase (0.6 collaborative similarity / 0.4 preference match)."));
    }

    public synchronized ModelVersion current() {
        return versions.get(versions.size() - 1);
    }

    public synchronized ModelVersion register(double wSim, Double alignment, long ms, String by, String changelog) {
        ModelVersion v = new ModelVersion(current().version() + 1, round(wSim), round(1 - wSim), Instant.now(),
                by, alignment, ms, changelog);
        versions.add(v);
        return v;
    }

    public synchronized List<ModelVersion> all() {
        return new ArrayList<>(versions);
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
