package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.Role;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.Public;
import com.aiadaptive.services.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** FR-01: registration, login, logout. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Public
    @PostMapping("/register")
    public UserView register(@RequestBody RegisterRequest r) {
        return auth.register(r.name(), r.email(), r.password());
    }

    @Public
    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader("Authorization") String header) {
        auth.logout(header.substring(7).trim());
        return Map.of("status", "logged out");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@Parameter(hidden = true) AuthContext ctx) {
        return Map.of("id", ctx.userId(), "name", ctx.name(), "role", ctx.role());
    }
}
