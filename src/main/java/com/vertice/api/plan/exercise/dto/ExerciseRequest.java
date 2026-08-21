package com.vertice.api.plan.exercise.dto;

import jakarta.validation.constraints.NotBlank;

public record ExerciseRequest(
        @NotBlank String name,
        String description
) {}
