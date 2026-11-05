package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Content;
import com.aiadaptive.repositories.ContentRepository;

@org.springframework.stereotype.Repository
public class InMemoryContentRepository extends InMemoryRepository<Content> implements ContentRepository {
}
