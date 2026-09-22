package com.vertice.api.grpc;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.common.exception.WorkoutExerciseHasRecordedDataException;
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

    @GrpcExceptionHandler(DuplicateCpfException.class)
    public Status handleDuplicateCpf(DuplicateCpfException ex) {
        return Status.ALREADY_EXISTS.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(WorkoutExerciseHasRecordedDataException.class)
    public Status handleWorkoutExerciseHasRecordedData(WorkoutExerciseHasRecordedDataException ex) {
        return Status.FAILED_PRECONDITION.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(UnauthenticatedException.class)
    public Status handleUnauthenticated(UnauthenticatedException ex) {
        return Status.UNAUTHENTICATED.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(PermissionDeniedException.class)
    public Status handlePermissionDenied(PermissionDeniedException ex) {
        return Status.PERMISSION_DENIED.withDescription(ex.getMessage());
    }

    /**
     * Hand-thrown {@code new ConstraintViolationException("field: message", Set.of())} carries its
     * description in the message and no violations, so fall back to the message in that case.
     */
    @GrpcExceptionHandler(ConstraintViolationException.class)
    public Status handleValidation(ConstraintViolationException ex) {
        if (ex.getConstraintViolations() == null || ex.getConstraintViolations().isEmpty()) {
            return Status.INVALID_ARGUMENT.withDescription(ex.getMessage());
        }
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining("; "));
        return Status.INVALID_ARGUMENT.withDescription(detail);
    }
}
