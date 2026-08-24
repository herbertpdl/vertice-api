package com.vertice.api.grpc;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * gRPC handler methods build their request objects by hand from proto fields, so nothing runs
 * Bean Validation on them the way {@code @Valid} does automatically for REST's
 * {@code @RequestBody} params. Callers (grpc-trainer, grpc-student, ...) run the mapped request
 * through this immediately before delegating to the business service, same spot {@code @Valid}
 * would run for REST.
 */
@Component
@RequiredArgsConstructor
public class GrpcRequestValidator {

    private final Validator validator;

    public <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
