package com.vertice.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a Brazilian CPF (individual taxpayer ID): exactly 11 digits, not one of the ten
 * all-same-digit sequences, and passes the official two check-digit algorithm.
 */
@Documented
@Constraint(validatedBy = CpfValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cpf {

    String message() default "must be a valid CPF";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
