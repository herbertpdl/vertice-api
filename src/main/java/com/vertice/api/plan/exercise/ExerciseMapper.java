package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Comparator;

@Mapper(componentModel = "spring", uses = ProtoStrings.class)
public interface ExerciseMapper {

    Comparator<ExerciseMuscleGroup> PRIMARY_FIRST_THEN_BY_ID = Comparator
            .comparing(ExerciseMuscleGroup::isPrimary).reversed()
            .thenComparing(link -> link.getMuscleGroup().getId());

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "muscleGroups", ignore = true)
    Exercise toEntity(ExerciseRequest request);

    @Mapping(target = "description", qualifiedByName = "nullToEmpty")
    @Mapping(target = "videoUrl", qualifiedByName = "nullToEmpty")
    @Mapping(target = "isStarter", source = "starter")
    @Mapping(target = "muscleGroupsList", ignore = true)
    ExerciseResponse toResponse(Exercise exercise);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "muscleGroups", ignore = true)
    void updateEntityFromRequest(ExerciseRequest request, @MappingTarget Exercise exercise);

    MuscleGroupResponse toMuscleGroupResponse(MuscleGroup muscleGroup);

    @AfterMapping
    default void addMuscleGroups(Exercise exercise, @MappingTarget ExerciseResponse.Builder response) {
        exercise.getMuscleGroups().stream()
                .sorted(PRIMARY_FIRST_THEN_BY_ID)
                .map(link -> toMuscleGroupResponse(link.getMuscleGroup()))
                .forEach(response::addMuscleGroups);
    }
}
