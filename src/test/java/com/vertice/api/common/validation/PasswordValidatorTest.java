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
    void rejectsBlankPasswordsOfAnyLength() {
        assertThat(validator.isValid(" ".repeat(8), null)).isFalse();
        assertThat(validator.isValid("\t \n".repeat(4), null)).isFalse();
    }

    /**
     * Spaces are part of a password: only an all-whitespace one is blank. Note that {@code @NotBlank}
     * (which sits next to {@code @Password} where a password is required) uses {@code trim()}, so it
     * also refuses an all-control-character value that {@code isBlank()} here would let through, and
     * {@code isBlank()} refuses Unicode spaces such as U+2003 that {@code trim()} keeps: together they
     * refuse both.
     */
    @Test
    void acceptsLeadingTrailingAndInternalSpaces() {
        assertThat(validator.isValid(" leading1", null)).isTrue();
        assertThat(validator.isValid("trailing1 ", null)).isTrue();
        assertThat(validator.isValid("in ter nal", null)).isTrue();
        assertThat(validator.isValid("  two  words  ", null)).isTrue();
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
