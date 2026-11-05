package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.User;
import com.aiadaptive.repositories.UserRepository;

@org.springframework.stereotype.Repository
public class InMemoryUserRepository extends InMemoryRepository<User> implements UserRepository {
}
