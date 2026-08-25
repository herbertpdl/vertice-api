package com.vertice.api.plan.workout;

import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseResponse;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = ProtoStrings.class)
public interface WorkoutExerciseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workout", ignore = true)
    @Mapping(target = "exercise", ignore = true)
    @Mapping(target = "exerciseSets", ignore = true)
    WorkoutExercise toEntity(WorkoutExerciseCreateRequest request);

    @Mapping(target = "workoutId", source = "workout.id")
    @Mapping(target = "exerciseId", source = "exercise.id")
    @Mapping(target = "notes", qualifiedByName = "nullToEmpty")
    WorkoutExerciseResponse toResponse(WorkoutExercise workoutExercise);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workout", ignore = true)
    @Mapping(target = "exercise", ignore = true)
    @Mapping(target = "exerciseSets", ignore = true)
    void updateEntityFromRequest(WorkoutExerciseRequest request, @MappingTarget WorkoutExercise workoutExercise);
}
