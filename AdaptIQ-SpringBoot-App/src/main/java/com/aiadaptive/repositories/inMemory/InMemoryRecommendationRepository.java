package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Recommendation;
import com.aiadaptive.repositories.RecommendationRepository;

@org.springframework.stereotype.Repository
public class InMemoryRecommendationRepository extends InMemoryRepository<Recommendation> implements RecommendationRepository {
}
