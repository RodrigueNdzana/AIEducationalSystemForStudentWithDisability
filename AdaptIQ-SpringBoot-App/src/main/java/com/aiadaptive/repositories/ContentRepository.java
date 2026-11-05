package com.aiadaptive.repositories;

import com.aiadaptive.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public interface ContentRepository extends Repository<Content, String> {
    default List<Content> findPublished() {
        return findAll().stream().filter(Content::isPublished).collect(Collectors.toList());
    }
}
