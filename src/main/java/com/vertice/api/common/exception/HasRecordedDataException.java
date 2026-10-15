package com.vertice.api.common.exception;

/**
 * A delete refused because other rows still point at the target: a client trained with it, or the
 * user owns training data (REL-API-003). Same idea as {@link WorkoutExerciseHasRecordedDataException}
 * for the granular deletes, checked up front so the foreign key never surfaces as a raw error. Maps
 * to {@code FAILED_PRECONDITION}: the same request can't succeed until that data is gone.
 */
public class HasRecordedDataException extends RuntimeException {

    /** E.g. {@code ("Workout", 5, "workout logs")} → "Workout 5 has workout logs and cannot be deleted". */
    public HasRecordedDataException(String resource, Long id, String recordedData) {
        super("%s %d has %s and cannot be deleted".formatted(resource, id, recordedData));
    }
}
