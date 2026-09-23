package com.vertice.api.grpc;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.user.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerIdentityTest {

    @Test
    void requireRole_withAllowedRole_doesNotThrow() {
        CallerIdentity caller = new CallerIdentity(1L, Role.TRAINER);

        assertThatCode(() -> caller.requireRole("list exercises", Role.TRAINER, Role.CLIENT))
                .doesNotThrowAnyException();
    }

    @Test
    void requireRole_withDisallowedRole_throwsPermissionDenied() {
        CallerIdentity caller = new CallerIdentity(1L, Role.CLIENT);

        assertThatThrownBy(() -> caller.requireRole("list exercises", Role.TRAINER))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Role CLIENT is not allowed to list exercises");
    }
}
