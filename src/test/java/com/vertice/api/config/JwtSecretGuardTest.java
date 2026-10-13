package com.vertice.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretGuardTest {

    private static final String VALID_SECRET = "x".repeat(JwtSecretGuard.MIN_SECRET_BYTES);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtSecretGuard.class)
            .withPropertyValues("vertice.jwt.secret=" + JwtSecretGuard.DEV_SECRET);

    @Test
    void shortSecret_fails() {
        assertRefused("a-secret-under-32-bytes", "JWT_SECRET must be at least 32 bytes");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "vertice-dev-jwt-secret-do-not-use-in-production",
            "VERTICE-DEV-JWT-SECRET-DO-NOT-USE-IN-PRODUCTION",
            "Vertice-Dev-JWT-Secret-Do-Not-Use-In-Production"})
    void placeholderSecret_fails(String secret) {
        assertRefused(secret, "JWT_SECRET is a publicly known placeholder/dev value; set a random secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev-secret-change-me", "change-me-in-production"})
    void shortPlaceholderSecret_fails(String secret) {
        // The older placeholders are under 32 bytes, so the length check rejects them first.
        assertRefused(secret, "JWT_SECRET must be at least 32 bytes");
    }

    @Test
    void paddedSecret_fails() {
        assertRefused(" " + VALID_SECRET + "\n", "JWT_SECRET must not have leading or trailing whitespace");
    }

    @Test
    void paddedPlaceholderSecret_fails() {
        assertThatThrownBy(new JwtSecretGuard("  " + JwtSecretGuard.DEV_SECRET)::checkSecret)
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankSecret_fails(String secret) {
        assertRefused(secret, "JWT_SECRET is required outside the local profile");
    }

    @Test
    void validSecret_passes() {
        assertThatCode(new JwtSecretGuard(VALID_SECRET)::checkSecret).doesNotThrowAnyException();
    }

    @Test
    void devDefault_nonLocalProfile_refusesToStart() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void devDefault_localProfile_starts() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JwtSecretGuard.class));
    }

    private static void assertRefused(String secret, String message) {
        assertThatThrownBy(new JwtSecretGuard(secret)::checkSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
    }
}
