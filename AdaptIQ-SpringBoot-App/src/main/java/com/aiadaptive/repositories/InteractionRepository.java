package com.aiadaptive.repositories;

import com.aiadaptive.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public interface InteractionRepository extends Repository<Interaction, String> {
    default List<Interaction> findByStudent(String studentId) {
        return findAll().stream().filter(i -> studentId.equals(i.getStudentId()))
                .sorted(Comparator.comparing(Interaction::getTimestamp)).collect(Collectors.toList());
    }
}
