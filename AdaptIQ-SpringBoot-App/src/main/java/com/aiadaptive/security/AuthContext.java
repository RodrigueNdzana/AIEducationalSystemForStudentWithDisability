package com.aiadaptive.security;

import com.aiadaptive.domain.Role;
import com.aiadaptive.exception.ApiException;

/** The authenticated caller, resolved from the Bearer token by {@link AuthInterceptor}. */
public record AuthContext(String userId, String name, Role role) {

    public boolean isStaff() {
        return role == Role.EDUCATOR || role == Role.DISABILITY_SUPPORT_OFFICER || role == Role.ADMIN;
    }

    /** Students may only access their own data; educators, DSOs and admins may access any student. */
    public void requireAccessToStudent(String studentId) {
        boolean ok = (role == Role.STUDENT && userId.equals(studentId)) || isStaff();
        if (!ok) throw ApiException.forbidden("You are not allowed to access this student's data");
    }
}
