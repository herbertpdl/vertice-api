package com.vertice.api.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The document {@code ExportUserData} returns as JSON (P1-05, LGPD art. 18 II and V): everything
 * vertice-api stores about the caller, per docs/data-inventory.md. Field names are the JSON keys;
 * {@link #FORMAT_VERSION} changes whenever a key is renamed or removed.
 *
 * <p>Left out on purpose: the password hash and password setup tokens (credentials, never returned
 * by any RPC) and {@code deleted_at} (always empty for an account that can still call). For a
 * trainer, their clients' logs and feedback are those clients' data and are not included.
 */
public record UserDataExport(
        int formatVersion,
        Instant exportedAt,
        Profile profile,
        Consent consent,
        List<Relationship> relationships,
        List<Plan> plans,
        List<WorkoutLog> workoutLogs,
        List<Exercise> exercises) {

    public static final int FORMAT_VERSION = 1;

    public record Profile(long id, String name, String email, String cpf, String cref, String role,
                          Instant createdAt, Instant updatedAt) {
    }

    public record Consent(String acceptedTermsVersion, Instant termsAcceptedAt,
                          String healthDataConsentVersion, Instant healthDataConsentAt) {
    }

    /** {@code as} is the caller's side of it: {@code TRAINER} or {@code CLIENT}. */
    public record Relationship(long id, String as, Party trainer, Party client, Instant startedAt, Instant endedAt) {
    }

    /** The other people a record names: id and name only. */
    public record Party(long id, String name) {
    }

    public record Plan(long id, String as, Party trainer, Party client, String name, String description,
                       LocalDate startDate, LocalDate endDate, String level, List<Workout> workouts) {
    }

    public record Workout(long id, String name, String dayOfWeek, List<WorkoutExercise> exercises) {
    }

    public record WorkoutExercise(long id, long exerciseId, String exerciseName, Integer order,
                                  Integer restSecondsBetweenSets, String notes, List<ExerciseSet> sets) {
    }

    public record ExerciseSet(long id, Integer setNumber, Integer reps, Integer durationSeconds, BigDecimal weight,
                              BigDecimal loadPercentage, String strategy, Integer restSeconds, String notes) {
    }

    public record WorkoutLog(long id, long workoutId, long planId, LocalDate weekStartDate, Instant startedAt,
                             Instant completedAt, List<SetLog> setLogs, List<Feedback> feedback) {
    }

    public record SetLog(long id, long exerciseSetId, BigDecimal weight, Integer reps, Instant recordedAt) {
    }

    public record Feedback(long id, String text, Instant createdAt) {
    }

    /** The caller's private exercises (a trainer's own catalogue). */
    public record Exercise(long id, String name, String description, String videoUrl) {
    }
}
