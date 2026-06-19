package com.vertice.api.plan.exercise.dto;

public record ExerciseResponse(
        Long id,
        String name,
        String description,
        Integer sets,
        Integer reps
) {}
