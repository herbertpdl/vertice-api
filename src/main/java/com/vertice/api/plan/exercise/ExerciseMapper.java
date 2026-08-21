package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ExerciseMapper {

    @Mapping(target = "id", ignore = true)
    Exercise toEntity(ExerciseRequest request);

    ExerciseResponse toResponse(Exercise exercise);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(ExerciseRequest request, @MappingTarget Exercise exercise);
}
