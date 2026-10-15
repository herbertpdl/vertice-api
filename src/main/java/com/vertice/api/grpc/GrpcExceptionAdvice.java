package com.vertice.api.grpc;

import com.vertice.api.common.exception.BusinessRuleViolationException;
import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ExerciseInUseException;
import com.vertice.api.common.exception.HasRecordedDataException;
import com.vertice.api.common.exception.InvalidCredentialsException;
import com.vertice.api.common.exception.InvalidPasswordSetupTokenException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.common.exception.WorkoutExerciseHasRecordedDataException;
import io.grpc.Status;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The gRPC-native equivalent of {@link com.vertice.api.common.exception.GlobalExceptionHandler}
 * for REST — same exception classes, same "no stack traces leak to the client" rule, mapped to
 * {@link Status} codes instead of {@code ProblemDetail}. Auth-related exceptions
 * ({@code AuthenticationException}, {@code AccessDeniedException}) are already handled by Spring
 * gRPC's own {@code SecurityGrpcExceptionHandler} — nothing to add here for those.
 */
@Slf4j
@GrpcAdvice
public class GrpcExceptionAdvice {

    /** SQL-standard SQLSTATE codes (class 23, integrity constraint violation). */
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    /**
     * Unique constraints a request can race into after the service's own check passed, with the
     * same fixed message that check gives. Anything else gets a generic one.
     */
    private static final Map<String, String> UNIQUE_CONSTRAINT_MESSAGES = Map.of(
            "uq_users_email_lower", DuplicateEmailException.MESSAGE,
            "uq_users_cpf", DuplicateCpfException.MESSAGE,
            "uq_trainer_clients_active_client", "Client already has an active trainer");

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

    @GrpcExceptionHandler(ExerciseInUseException.class)
    public Status handleExerciseInUse(ExerciseInUseException ex) {
        return Status.FAILED_PRECONDITION.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(HasRecordedDataException.class)
    public Status handleHasRecordedData(HasRecordedDataException ex) {
        return Status.FAILED_PRECONDITION.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(BusinessRuleViolationException.class)
    public Status handleBusinessRuleViolation(BusinessRuleViolationException ex) {
        return Status.FAILED_PRECONDITION.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(UnauthenticatedException.class)
    public Status handleUnauthenticated(UnauthenticatedException ex) {
        return Status.UNAUTHENTICATED.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(InvalidCredentialsException.class)
    public Status handleInvalidCredentials(InvalidCredentialsException ex) {
        return Status.UNAUTHENTICATED.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(InvalidPasswordSetupTokenException.class)
    public Status handleInvalidPasswordSetupToken(InvalidPasswordSetupTokenException ex) {
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

    /**
     * The fallback for a constraint the service didn't check first, or checked and then lost a
     * race on (REL-API-003/004): a known client error, never {@code INTERNAL}/{@code UNKNOWN}. The
     * driver's message can name the offending values (an email, a CPF), so it never reaches the
     * description or the log; only the SQLSTATE and the constraint name are logged. A unique or
     * foreign-key race is expected traffic (WARN); anything else means a validation gap, so it is
     * logged at ERROR to stay visible while the caller still gets {@code INVALID_ARGUMENT}.
     */
    @GrpcExceptionHandler(DataIntegrityViolationException.class)
    public Status handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String sqlState = causeOf(ex, SQLException.class).map(SQLException::getSQLState).orElse(null);
        String constraint = causeOf(ex, org.hibernate.exception.ConstraintViolationException.class)
                .map(org.hibernate.exception.ConstraintViolationException::getConstraintName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .orElse(null);
        if (UNIQUE_VIOLATION.equals(sqlState) || ex instanceof DuplicateKeyException) {
            log.warn("Unique constraint violation: sqlState={} constraint={}", sqlState, constraint);
            return Status.ALREADY_EXISTS.withDescription(Optional.ofNullable(constraint)
                    .map(UNIQUE_CONSTRAINT_MESSAGES::get)
                    .orElse("A record with the same unique value already exists"));
        }
        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            log.warn("Foreign key violation: sqlState={} constraint={}", sqlState, constraint);
            return Status.FAILED_PRECONDITION.withDescription(
                    "The change conflicts with related data: the record is still in use, or refers to one that does not exist");
        }
        // A NOT NULL or CHECK constraint (or no SQLSTATE at all): input the request validation should have refused.
        log.error("Data integrity violation not caught by request validation: sqlState={} constraint={}",
                sqlState, constraint);
        return Status.INVALID_ARGUMENT.withDescription("The request violates a data integrity rule");
    }

    private static <T extends Throwable> Optional<T> causeOf(Throwable ex, Class<T> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return Optional.of(type.cast(cause));
            }
        }
        return Optional.empty();
    }
}
