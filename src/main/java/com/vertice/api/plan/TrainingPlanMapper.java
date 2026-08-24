package com.vertice.api.plan;

import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = ProtoStrings.class)
public interface TrainingPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "workouts", ignore = true)
    TrainingPlan toEntity(TrainingPlanCreateRequest request);

    @Mapping(target = "trainerId", source = "trainer.id")
    @Mapping(target = "description", qualifiedByName = "nullToEmpty")
    TrainingPlanResponse toResponse(TrainingPlan trainingPlan);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "workouts", ignore = true)
    void updateEntityFromRequest(TrainingPlanRequest request, @MappingTarget TrainingPlan trainingPlan);
}
