package com.vertice.api.common.exception;

public class WorkoutExerciseHasRecordedDataException extends RuntimeException {

    public WorkoutExerciseHasRecordedDataException(String exerciseName, Long exerciseId, Integer setNumber) {
        super("Cannot replace exercises: exercise '%s' (id %d) set %d has recorded workout data"
                .formatted(exerciseName, exerciseId, setNumber));
    }
}
