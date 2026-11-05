package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Feedback;
import com.aiadaptive.repositories.FeedbackRepository;

@org.springframework.stereotype.Repository
public class InMemoryFeedbackRepository extends InMemoryRepository<Feedback> implements FeedbackRepository {
}
