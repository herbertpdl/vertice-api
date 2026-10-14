package com.vertice.api.grpc;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.user.Role;

import java.util.Arrays;

/** A user calling through the BFF, as stated by its JWT ({@code sub} and {@code role} claims). */
public record CallerIdentity(Long userId, Role role) implements Caller {

    /**
     * @param action completes "Role X is not allowed to ...", e.g. {@code "list exercises"}
     */
    public void requireRole(String action, Role... allowed) {
        if (Arrays.stream(allowed).noneMatch(r -> r == role)) {
            throw PermissionDeniedException.role(role, action);
        }
    }
}
