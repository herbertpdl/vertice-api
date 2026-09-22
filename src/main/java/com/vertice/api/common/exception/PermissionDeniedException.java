package com.vertice.api.common.exception;

import com.vertice.api.user.Role;

/**
 * The caller is known but not allowed to do this — a cross-trainer access, a wrong role, or a
 * change to the shared starter set. A missing id stays {@link ResourceNotFoundException}.
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    public static PermissionDeniedException noAccess(String resource, Long id) {
        return new PermissionDeniedException("You do not have access to %s %d".formatted(resource, id));
    }

    public static PermissionDeniedException starterSet(Long exerciseId, String verb) {
        return new PermissionDeniedException(
                "Exercise %d belongs to the shared starter set and cannot be %s".formatted(exerciseId, verb));
    }

    public static PermissionDeniedException role(Role role, String action) {
        return new PermissionDeniedException("Role %s is not allowed to %s".formatted(role, action));
    }
}
