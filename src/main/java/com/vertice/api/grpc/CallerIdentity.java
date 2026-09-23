package com.vertice.api.grpc;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.user.Role;

import java.util.Arrays;

/** Who is calling, as stated by the BFF's JWT ({@code id} and {@code role} claims). */
public record CallerIdentity(Long userId, Role role) {

    /**
     * @param action completes "Role X is not allowed to ...", e.g. {@code "list exercises"}
     */
    public void requireRole(String action, Role... allowed) {
        if (Arrays.stream(allowed).noneMatch(r -> r == role)) {
            throw PermissionDeniedException.role(role, action);
        }
    }
}
