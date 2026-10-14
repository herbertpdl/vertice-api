package com.vertice.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password policy for any password a user sets: at least 8 characters and at most 72 UTF-8
 * bytes — bcrypt only reads 72 bytes and Spring's encoder refuses longer input, so the limit is in
 * bytes, not characters. {@code null} is valid (the standard Bean Validation convention); combine
 * with {@code @NotBlank} where the password is required.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    String message() default "must be at least 8 characters and at most 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
