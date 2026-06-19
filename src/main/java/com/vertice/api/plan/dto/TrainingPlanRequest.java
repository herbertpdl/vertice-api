package com.vertice.api.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TrainingPlanRequest(
        @NotBlank String name,
        String description,
        @NotNull Long trainerId
) {}
