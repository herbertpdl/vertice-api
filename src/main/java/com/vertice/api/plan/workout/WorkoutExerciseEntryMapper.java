package com.vertice.api.plan.workout;

import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.grpc.ProtoDecimals;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

/**
 * Maps the nested {@code CreateWorkoutWithExercises}/{@code ReplaceWorkoutExercises} entry
 * messages to entities. Deliberately separate from {@link ExerciseSetMapper}: unlike
 * {@code ExerciseSetMapper#mapStrategy} (which maps an omitted {@code SET_STRATEGY_UNSPECIFIED}
 * to {@code null}, relying on {@code ExerciseSetController} to reject it first), this path
 * defaults it to {@code STRAIGHT} — see docs/specs/create-workout-with-exercises/spec.md §0/F1.
 * {@code order}/{@code setNumber} are always ignored here — neither field exists on the entry
 * messages; the service sets them from list position.
 */
@Mapper(componentModel = "spring", uses = {ProtoStrings.class, ProtoDecimals.class})
public interface WorkoutExerciseEntryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workout", ignore = true)
    @Mapping(target = "exercise", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "exerciseSets", ignore = true)
    WorkoutExercise toEntity(WorkoutExerciseEntry entry);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workoutExercise", ignore = true)
    @Mapping(target = "setNumber", ignore = true)
    @Mapping(target = "weight", qualifiedByName = "stringToDecimal")
    @Mapping(target = "loadPercentage", qualifiedByName = "stringToDecimal")
    ExerciseSet toEntity(ExerciseSetEntry entry);

    @ValueMapping(source = "SET_STRATEGY_UNSPECIFIED", target = "STRAIGHT")
    @ValueMapping(source = "UNRECOGNIZED", target = "STRAIGHT")
    SetStrategy mapStrategy(com.vertice.api.generated.grpc.plan.v1.SetStrategy strategy);
}
