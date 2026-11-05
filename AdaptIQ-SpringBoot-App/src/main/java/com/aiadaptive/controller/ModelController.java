package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.Role;
import com.aiadaptive.dto.Dto.ModelMetrics;
import com.aiadaptive.dto.Dto.ModelVersion;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.Require;
import com.aiadaptive.services.ModelMonitoringService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-12: AI model monitoring and retraining. */
@RestController
@RequestMapping("/api/models")
@Require({Role.ML_ENGINEER, Role.ADMIN})
public class ModelController {

    private final ModelMonitoringService monitoring;

    public ModelController(ModelMonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/metrics")
    public ModelMetrics metrics() {
        return monitoring.metrics();
    }

    @GetMapping("/versions")
    public List<ModelVersion> versions() {
        return monitoring.versions();
    }

    @PostMapping("/retrain")
    public ModelVersion retrain(@Parameter(hidden = true) AuthContext ctx) {
        return monitoring.retrain(ctx.name());
    }
}
