package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ValueMapping;

@Mapper(componentModel = "spring", uses = ProtoStrings.class)
public interface ExerciseMapper {

    @Mapping(target = "id", ignore = true)
    Exercise toEntity(ExerciseRequest request);

    @Mapping(target = "description", qualifiedByName = "nullToEmpty")
    @Mapping(target = "videoUrl", qualifiedByName = "nullToEmpty")
    ExerciseResponse toResponse(Exercise exercise);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(ExerciseRequest request, @MappingTarget Exercise exercise);

    /**
     * Same {@code @ValueMapping} treatment {@code WorkoutMapper#mapDayOfWeek} uses: proto3's
     * {@code MuscleGroup} carries a zero value ({@code MUSCLE_GROUP_UNSPECIFIED}) with no matching
     * {@link MuscleGroup} constant; {@code ExerciseController} rejects it before the request
     * reaches the mapper, but MapStruct requires every source constant accounted for.
     */
    @ValueMapping(source = "MUSCLE_GROUP_UNSPECIFIED", target = MappingConstants.NULL)
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
    MuscleGroup mapMuscleGroup(com.vertice.api.generated.grpc.exercise.v1.MuscleGroup muscleGroup);
}
