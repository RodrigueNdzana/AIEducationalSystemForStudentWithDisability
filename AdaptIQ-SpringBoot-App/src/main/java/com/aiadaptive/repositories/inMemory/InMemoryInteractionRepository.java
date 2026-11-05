package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Interaction;
import com.aiadaptive.repositories.InteractionRepository;

@org.springframework.stereotype.Repository
public class InMemoryInteractionRepository extends InMemoryRepository<Interaction> implements InteractionRepository {
}
