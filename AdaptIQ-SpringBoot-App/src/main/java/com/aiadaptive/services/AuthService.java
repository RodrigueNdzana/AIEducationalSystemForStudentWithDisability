package com.aiadaptive.services;

import com.aiadaptive.domain.Role;
import com.aiadaptive.domain.User;
import com.aiadaptive.dto.Dto.LoginResult;
import com.aiadaptive.dto.Dto.UserView;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.UserRepository;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** FR-01: registration, local login, account lockout after 5 failures, session tokens. */
@Service
public class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final int ITERATIONS = 65_536;

    public record Session(String token, String userId, String name, Role role, Instant expiresAt) {}

    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public UserView register(String name, String email, String password) {
        return create(name, email, password, Role.STUDENT);
    }

    /** Used by administrators to create staff accounts and by the demo data seeder. */
    public UserView create(String name, String email, String password, Role role) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Name is required");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("A valid email address is required");
        }
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*\\d.*")) {
            throw ApiException.badRequest("Password must be at least 8 characters and contain a letter and a digit");
        }
        if (role == null) role = Role.STUDENT;
        if (users.findByEmail(email).isPresent()) throw new ApiException(409, "Email is already registered");

        User u = new User(name.trim(), email.trim().toLowerCase(), role);
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        u.setSalt(Base64.getEncoder().encodeToString(salt));
        u.setPasswordHash(hash(password, salt));
        return UserView.of(users.save(u));
    }

    public LoginResult login(String email, String password) {
        if (email == null || password == null) throw ApiException.badRequest("Email and password are required");
        User u = users.findByEmail(email.trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (u.isLocked()) {
            throw new ApiException(423, "Account locked after " + MAX_FAILED_ATTEMPTS
                    + " failed attempts. Contact an administrator.");
        }
        byte[] salt = Base64.getDecoder().decode(u.getSalt());
        boolean ok = MessageDigest.isEqual(hash(password, salt).getBytes(), u.getPasswordHash().getBytes());
        if (!ok) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            if (u.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) u.setLocked(true);
            users.save(u);
            if (u.isLocked()) throw new ApiException(423, "Account locked after too many failed attempts");
            throw ApiException.unauthorized("Invalid email or password");
        }
        u.setFailedAttempts(0);
        users.save(u);

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Session s = new Session(token, u.getId(), u.getName(), u.getRole(), Instant.now().plus(SESSION_TTL));
        sessions.put(token, s);
        return new LoginResult(token, s.expiresAt(), UserView.of(u));
    }

    public Optional<Session> resolve(String token) {
        Session s = sessions.get(token);
        if (s == null) return Optional.empty();
        if (s.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void logout(String token) {
        sessions.remove(token);
    }

    public UserView unlock(String userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        u.setLocked(false);
        u.setFailedAttempts(0);
        return UserView.of(users.save(u));
    }

    private String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }
}
