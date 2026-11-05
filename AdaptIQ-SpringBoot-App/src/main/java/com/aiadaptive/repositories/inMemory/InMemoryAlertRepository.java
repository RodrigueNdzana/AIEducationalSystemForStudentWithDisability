package com.aiadaptive.repositories.inMemory;

import com.aiadaptive.domain.Alert;
import com.aiadaptive.repositories.AlertRepository;

@org.springframework.stereotype.Repository
public class InMemoryAlertRepository extends InMemoryRepository<Alert> implements AlertRepository {
}
