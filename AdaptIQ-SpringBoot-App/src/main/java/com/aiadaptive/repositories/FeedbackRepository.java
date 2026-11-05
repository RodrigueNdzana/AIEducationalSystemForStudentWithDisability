package com.aiadaptive.repositories;

import com.aiadaptive.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public interface FeedbackRepository extends Repository<Feedback, String> {
    default List<Feedback> findByStudent(String studentId) {
        return findAll().stream().filter(f -> studentId.equals(f.getStudentId())).collect(Collectors.toList());
    }
}
