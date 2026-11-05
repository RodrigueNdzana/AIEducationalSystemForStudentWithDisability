package com.aiadaptive.services;

import com.aiadaptive.domain.Notification;
import com.aiadaptive.domain.Role;
import com.aiadaptive.domain.User;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.repositories.NotificationRepository;
import com.aiadaptive.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * In-platform notifications. E-mail is simulated by a log line + a second notification on the EMAIL
 * channel; plug a JavaMailSender in {@link #email(User, String)} for real delivery.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final UserRepository users;

    public NotificationService(NotificationRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    public Notification notify(String userId, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setMessage(message);
        return repo.save(n);
    }

    /** In-platform notification plus a (simulated) e-mail. */
    public void notifyWithEmail(String userId, String message) {
        notify(userId, message);
        users.findById(userId).ifPresent(u -> email(u, message));
    }

    public void notifyRole(Role role, String message, boolean alsoEmail) {
        for (User u : users.findByRole(role)) {
            if (alsoEmail) notifyWithEmail(u.getId(), message);
            else notify(u.getId(), message);
        }
    }

    private void email(User u, String message) {
        log.info("EMAIL to {}: {}", u.getEmail(), message);
        Notification n = new Notification();
        n.setUserId(u.getId());
        n.setMessage(message);
        n.setChannel("EMAIL");
        repo.save(n);
    }

    public List<Notification> forUser(String userId) {
        return repo.findByUser(userId);
    }

    public Notification markRead(String id, String userId) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Notification"));
        if (!n.getUserId().equals(userId)) throw ApiException.forbidden("Not your notification");
        n.setRead(true);
        return repo.save(n);
    }
}
