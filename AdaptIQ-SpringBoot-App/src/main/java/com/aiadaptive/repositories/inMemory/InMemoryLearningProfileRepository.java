package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.LearningProfile;
import com.aiadaptive.repositories.LearningProfileRepository;

@org.springframework.stereotype.Repository
public class InMemoryLearningProfileRepository extends InMemoryRepository<LearningProfile> implements LearningProfileRepository {
}
