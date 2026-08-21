package com.vertice.api.grpc;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import io.grpc.Status;
import jakarta.validation.ConstraintViolationException;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

import java.util.stream.Collectors;

/**
 * The gRPC-native equivalent of {@link com.vertice.api.common.exception.GlobalExceptionHandler}
 * for REST — same exception classes, same "no stack traces leak to the client" rule, mapped to
 * {@link Status} codes instead of {@code ProblemDetail}. Auth-related exceptions
 * ({@code AuthenticationException}, {@code AccessDeniedException}) are already handled by Spring
 * gRPC's own {@code SecurityGrpcExceptionHandler} — nothing to add here for those.
 */
@GrpcAdvice
public class GrpcExceptionAdvice {

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public Status handleResourceNotFound(ResourceNotFoundException ex) {
        return Status.NOT_FOUND.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(DuplicateEmailException.class)
    public Status handleDuplicateEmail(DuplicateEmailException ex) {
        return Status.ALREADY_EXISTS.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(ConstraintViolationException.class)
    public Status handleValidation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining("; "));
        return Status.INVALID_ARGUMENT.withDescription(detail);
    }
}
