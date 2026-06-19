package com.vertice.api.plan.dto;

import com.vertice.api.plan.exercise.dto.ExerciseResponse;

import java.util.List;

public record TrainingPlanResponse(
        Long id,
        String name,
        String description,
        Long trainerId,
        List<ExerciseResponse> exercises
) {}
