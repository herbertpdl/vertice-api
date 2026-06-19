package com.vertice.api.plan.exercise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ExerciseRequest(
        @NotBlank String name,
        String description,
        @Positive Integer sets,
        @Positive Integer reps
) {}
