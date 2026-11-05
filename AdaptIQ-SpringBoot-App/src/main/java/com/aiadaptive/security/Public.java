package com.aiadaptive.security;

import java.lang.annotation.*;

/** Marks an endpoint that does not need a session token (login, registration). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Public {
}
