package com.aiadaptive.security;

import com.aiadaptive.domain.Role;

import java.lang.annotation.*;

/** Role-based access control (NFR-SEC2). An empty list means "any authenticated user". */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Require {
    Role[] value() default {};
}
