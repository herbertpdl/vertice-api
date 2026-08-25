package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = ProtoStrings.class)
public interface ExerciseMapper {

    @Mapping(target = "id", ignore = true)
    Exercise toEntity(ExerciseRequest request);

    @Mapping(target = "description", qualifiedByName = "nullToEmpty")
    @Mapping(target = "videoUrl", qualifiedByName = "nullToEmpty")
    ExerciseResponse toResponse(Exercise exercise);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(ExerciseRequest request, @MappingTarget Exercise exercise);
}
