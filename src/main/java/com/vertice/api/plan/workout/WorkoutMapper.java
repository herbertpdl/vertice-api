package com.vertice.api.plan.workout;

import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ValueMapping;

/**
 * {@code day_of_week} follows the same {@code @ValueMapping} treatment
 * {@code TrainingPlanMapper#mapLevel}/{@code UserMapper#mapRole} use: proto3's {@code DayOfWeek}
 * carries a zero value ({@code DAY_OF_WEEK_UNSPECIFIED}) with no matching {@link DayOfWeek}
 * constant; {@code WorkoutController} rejects it before the request reaches the mapper, but
 * MapStruct requires every source constant accounted for.
 */
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

    @ValueMapping(source = "DAY_OF_WEEK_UNSPECIFIED", target = MappingConstants.NULL)
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
    DayOfWeek mapDayOfWeek(com.vertice.api.generated.grpc.plan.v1.DayOfWeek dayOfWeek);
}
