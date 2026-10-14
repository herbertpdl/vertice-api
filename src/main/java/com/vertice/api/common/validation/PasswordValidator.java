package com.vertice.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<Password, String> {

    static final int MIN_CHARS = 8;
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return !value.isBlank()
                && value.length() >= MIN_CHARS
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
