package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Notification;
import com.aiadaptive.repositories.NotificationRepository;

@org.springframework.stereotype.Repository
public class InMemoryNotificationRepository extends InMemoryRepository<Notification> implements NotificationRepository {
}
