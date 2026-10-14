package com.vertice.api.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @Test
    void acceptsEightToSeventyTwoAsciiCharacters() {
        assertThat(validator.isValid("x".repeat(8), null)).isTrue();
        assertThat(validator.isValid("x".repeat(72), null)).isTrue();
    }

    @Test
    void rejectsFewerThanEightCharacters() {
        assertThat(validator.isValid("x".repeat(7), null)).isFalse();
    }

    @Test
    void limitIsSeventyTwoUtf8Bytes_notCharacters() {
        // "é" is 2 bytes in UTF-8: 36 of them is exactly 72 bytes, 40 is 80 bytes in 40 characters.
        assertThat(validator.isValid("é".repeat(36), null)).isTrue();
        assertThat(validator.isValid("é".repeat(40), null)).isFalse();
        assertThat(validator.isValid("x".repeat(73), null)).isFalse();
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
