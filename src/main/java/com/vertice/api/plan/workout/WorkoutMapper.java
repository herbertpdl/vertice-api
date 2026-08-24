package com.vertice.api.plan.workout;

import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WorkoutMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainingPlan", ignore = true)
    @Mapping(target = "workoutExercises", ignore = true)
    Workout toEntity(WorkoutCreateRequest request);

    @Mapping(target = "trainingPlanId", source = "trainingPlan.id")
    WorkoutResponse toResponse(Workout workout);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainingPlan", ignore = true)
    @Mapping(target = "workoutExercises", ignore = true)
    void updateEntityFromRequest(WorkoutRequest request, @MappingTarget Workout workout);
}
