package com.aiadaptive.security;

import com.aiadaptive.exception.ApiException;
import com.aiadaptive.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/** Authenticates every /api request via "Authorization: Bearer token" and enforces {@link Require}. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "adaptiq.auth";

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (!(handler instanceof HandlerMethod hm)) return true;
        if (hm.hasMethodAnnotation(Public.class)) return true;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Authentication required");
        }
        AuthService.Session s = auth.resolve(header.substring(7).trim())
                .orElseThrow(() -> ApiException.unauthorized("Session expired or invalid"));
        AuthContext ctx = new AuthContext(s.userId(), s.name(), s.role());
        request.setAttribute(ATTR, ctx);

        Require req = hm.getMethodAnnotation(Require.class);
        if (req == null) req = hm.getBeanType().getAnnotation(Require.class);
        if (req != null && req.value().length > 0 && !Arrays.asList(req.value()).contains(ctx.role())) {
            throw ApiException.forbidden("Your role (" + ctx.role() + ") may not perform this action");
        }
        return true;
    }
}
