package com.aiadaptive.factories;

import com.aiadaptive.repositories.UserRepository;
import com.aiadaptive.repositories.inMemory.InMemoryUserRepository;

/**
 * Factory kept from the original design (Creational pattern). Spring normally wires the repositories;
 * this factory is the extension point for swapping the storage technology (e.g. a JPA/PostgreSQL
 * implementation) without changing services.
 */
public class RepositoryFactory {

    public static UserRepository getUserRepository(String type) {
        return switch (type.toUpperCase()) {
            case "MEMORY" -> new InMemoryUserRepository();
            case "DATABASE" -> throw new UnsupportedOperationException("Database repositories are not implemented yet");
            default -> throw new IllegalArgumentException("Invalid repository type: " + type);
        };
    }
}
