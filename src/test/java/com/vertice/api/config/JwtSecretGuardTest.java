package com.vertice.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretGuardTest {

    @Test
    void shortSecret_fails() {
        JwtSecretGuard guard = new JwtSecretGuard("dev-secret-change-me");

        assertThatThrownBy(guard::checkSecretLength)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must be at least 32 bytes");
    }

    @Test
    void longSecret_passes() {
        JwtSecretGuard guard = new JwtSecretGuard("x".repeat(JwtSecretGuard.MIN_SECRET_BYTES));

        assertThatCode(guard::checkSecretLength).doesNotThrowAnyException();
    }
}
