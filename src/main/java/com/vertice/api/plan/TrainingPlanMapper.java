package com.vertice.api.plan;

import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TrainingPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "workouts", ignore = true)
    TrainingPlan toEntity(TrainingPlanCreateRequest request);

    @Mapping(target = "trainerId", source = "trainer.id")
    TrainingPlanResponse toResponse(TrainingPlan trainingPlan);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "workouts", ignore = true)
    void updateEntityFromRequest(TrainingPlanRequest request, @MappingTarget TrainingPlan trainingPlan);
}
