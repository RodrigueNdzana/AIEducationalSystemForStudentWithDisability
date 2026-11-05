package com.aiadaptive.repositories;

import com.aiadaptive.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public interface RecommendationRepository extends Repository<Recommendation, String> {
    default List<Recommendation> findByStudent(String studentId) {
        return findAll().stream().filter(r -> studentId.equals(r.getStudentId())).collect(Collectors.toList());
    }
}
