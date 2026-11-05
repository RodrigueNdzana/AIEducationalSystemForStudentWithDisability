package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.Notification;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.services.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Notification> mine(@Parameter(hidden = true) AuthContext ctx) {
        return service.forUser(ctx.userId());
    }

    @PostMapping("/{id}/read")
    public Notification read(@PathVariable String id, @Parameter(hidden = true) AuthContext ctx) {
        return service.markRead(id, ctx.userId());
    }
}
