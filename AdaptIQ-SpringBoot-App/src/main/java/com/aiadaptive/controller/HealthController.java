package com.aiadaptive.controller;

import com.aiadaptive.security.Public;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @Public
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "system", "AdaptIQ");
    }
}
