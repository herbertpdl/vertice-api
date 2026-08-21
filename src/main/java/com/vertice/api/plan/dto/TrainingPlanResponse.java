package com.vertice.api.plan.dto;

public record TrainingPlanResponse(
        Long id,
        String name,
        String description,
        Long trainerId
) {}
