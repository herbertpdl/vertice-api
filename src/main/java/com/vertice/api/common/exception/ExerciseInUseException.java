package com.vertice.api.common.exception;

public class ExerciseInUseException extends RuntimeException {

    public ExerciseInUseException(Long exerciseId) {
        super("Exercise %d is used by a workout and cannot be deleted".formatted(exerciseId));
    }
}
