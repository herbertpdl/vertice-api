package com.vertice.api.plan.workout;

import com.vertice.api.generated.grpc.plan.v1.ExerciseSetCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import com.vertice.api.grpc.ProtoDecimals;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ValueMapping;

/**
 * {@code strategy} follows the same {@code @ValueMapping} treatment
 * {@code WorkoutMapper#mapDayOfWeek}/{@code TrainingPlanMapper#mapLevel} use: proto3's
 * {@code SetStrategy} carries a zero value ({@code SET_STRATEGY_UNSPECIFIED}) with no matching
 * {@link SetStrategy} constant; {@code ExerciseSetController} rejects it before the request
 * reaches the mapper, but MapStruct requires every source constant accounted for.
 */
@Mapper(componentModel = "spring", uses = {ProtoStrings.class, ProtoDecimals.class})
public interface ExerciseSetMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workoutExercise", ignore = true)
    @Mapping(target = "weight", qualifiedByName = "stringToDecimal")
    @Mapping(target = "loadPercentage", qualifiedByName = "stringToDecimal")
    ExerciseSet toEntity(ExerciseSetCreateRequest request);

    @Mapping(target = "workoutExerciseId", source = "workoutExercise.id")
    @Mapping(target = "notes", qualifiedByName = "nullToEmpty")
    @Mapping(target = "weight", qualifiedByName = "decimalToString")
    @Mapping(target = "loadPercentage", qualifiedByName = "decimalToString")
    ExerciseSetResponse toResponse(ExerciseSet exerciseSet);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workoutExercise", ignore = true)
    @Mapping(target = "weight", qualifiedByName = "stringToDecimal")
    @Mapping(target = "loadPercentage", qualifiedByName = "stringToDecimal")
    void updateEntityFromRequest(ExerciseSetRequest request, @MappingTarget ExerciseSet exerciseSet);

    @ValueMapping(source = "SET_STRATEGY_UNSPECIFIED", target = MappingConstants.NULL)
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
    SetStrategy mapStrategy(com.vertice.api.generated.grpc.plan.v1.SetStrategy strategy);
}
