package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Identifiable;
import com.aiadaptive.repositories.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe, insertion-ordered in-memory store shared by all entity repositories. */
public abstract class InMemoryRepository<T extends Identifiable> implements Repository<T, String> {

    private final Map<String, T> storage = new LinkedHashMap<>();

    @Override
    public synchronized T save(T entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString().substring(0, 8));
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public synchronized Optional<T> findById(String id) {
        return Optional.ofNullable(id == null ? null : storage.get(id));
    }

    @Override
    public synchronized List<T> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public synchronized void delete(String id) {
        storage.remove(id);
    }
}
