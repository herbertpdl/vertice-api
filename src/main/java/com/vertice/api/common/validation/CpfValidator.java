package com.vertice.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<Cpf, String> {

    private static final int LENGTH = 11;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (isAllSameDigit(value)) {
            return false;
        }

        int[] digits = value.chars().map(c -> c - '0').toArray();

        return digits[9] == checkDigit(digits, 9, 10) && digits[10] == checkDigit(digits, 10, 11);
    }

    private boolean isAllSameDigit(String value) {
        return value.chars().allMatch(c -> c == value.charAt(0));
    }

    private int checkDigit(int[] digits, int count, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += digits[i] * (firstWeight - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
