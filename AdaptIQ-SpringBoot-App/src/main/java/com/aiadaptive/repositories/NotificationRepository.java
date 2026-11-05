package com.aiadaptive.repositories;

import com.aiadaptive.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public interface NotificationRepository extends Repository<Notification, String> {
    default List<Notification> findByUser(String userId) {
        return findAll().stream().filter(n -> userId.equals(n.getUserId()))
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed()).collect(Collectors.toList());
    }
}
