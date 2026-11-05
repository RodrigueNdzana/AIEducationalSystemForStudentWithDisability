package com.aiadaptive.repositories;

import com.aiadaptive.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public interface UserRepository extends Repository<User, String> {
    default Optional<User> findByEmail(String email) {
        return findAll().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
    }
    default List<User> findByRole(Role role) {
        return findAll().stream().filter(u -> u.getRole() == role).collect(Collectors.toList());
    }
}
