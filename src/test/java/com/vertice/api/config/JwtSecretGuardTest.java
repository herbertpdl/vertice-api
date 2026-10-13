package com.vertice.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\uFEFF", "\u202F", "\u2028"})
    void unicodePaddedSecret_fails(String padding) {
        assertRefused(padding + VALID_SECRET, "JWT_SECRET must not have leading or trailing whitespace");
        assertRefused(VALID_SECRET + padding, "JWT_SECRET must not have leading or trailing whitespace");
    }

    @Test
    void secretWithInformationSeparatorEdges_passes() {
        // JS trim() keeps U+001C–U+001F (Java's strip() would remove them); the BFF accepts this too.
        assertThatCode(new JwtSecretGuard("\u001C" + VALID_SECRET + "\u001F")::checkSecret)
                .doesNotThrowAnyException();
    }

    @Test
    void paddedPlaceholderSecret_fails() {
        // The whitespace check runs before the placeholder check, as in the BFF.
        assertRefused("  " + JwtSecretGuard.DEV_SECRET, "JWT_SECRET must not have leading or trailing whitespace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankSecret_fails(String secret) {
        assertRefused(secret, "JWT_SECRET is required outside the local profile");
    }

    @Test
    void allNbspSecret_fails() {
        assertRefused("\u00A0".repeat(JwtSecretGuard.MIN_SECRET_BYTES), "JWT_SECRET is required outside the local profile");
    }

    @Test
    void jsTrim_trimsExactlyTheJavaScriptTrimSet() {
        // Every BMP char that `(c + "x").trim() === "x"` holds for in Node.js.
        Set<Integer> jsTrimmed = Set.of(0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0xA0, 0x1680, 0x2000, 0x2001,
                0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x202F,
                0x205F, 0x3000, 0xFEFF);
        Set<Integer> javaTrimmed = IntStream.rangeClosed(0, 0xFFFF)
                .filter(c -> JwtSecretGuard.jsTrim((char) c + "x").equals("x"))
                .boxed()
                .collect(Collectors.toSet());

        assertThat(javaTrimmed).isEqualTo(jsTrimmed);
    }

    @Test
    void jsTrim_matchesJavaScriptTrim() {
        assertThat(JwtSecretGuard.jsTrim("\uFEFF\u00A0\t secret \n\u3000")).isEqualTo("secret");
        assertThat(JwtSecretGuard.jsTrim("\u001Csecret\u001F")).isEqualTo("\u001Csecret\u001F");
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
