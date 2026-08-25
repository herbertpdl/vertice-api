package com.vertice.api.plan;

import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.grpc.ProtoDates;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ValueMapping;

/**
 * {@code level} follows the same {@code @ValueMapping} treatment {@code UserMapper#mapRole} uses:
 * proto3's {@code PlanLevel} carries a zero value ({@code PLAN_LEVEL_UNSPECIFIED}) with no
 * matching {@link PlanLevel} constant; {@code TrainingPlanController} rejects it before the
 * request reaches the mapper, but MapStruct requires every source constant accounted for.
 */
@Mapper(componentModel = "spring", uses = {ProtoStrings.class, ProtoDates.class})
public interface TrainingPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "workouts", ignore = true)
    @Mapping(target = "startDate", source = "startDate", qualifiedByName = "stringToDate")
    @Mapping(target = "endDate", source = "endDate", qualifiedByName = "stringToDate")
    TrainingPlan toEntity(TrainingPlanCreateRequest request);

    @Mapping(target = "trainerId", source = "trainer.id")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "description", qualifiedByName = "nullToEmpty")
    @Mapping(target = "startDate", source = "startDate", qualifiedByName = "dateToString")
    @Mapping(target = "endDate", source = "endDate", qualifiedByName = "dateToString")
    TrainingPlanResponse toResponse(TrainingPlan trainingPlan);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "workouts", ignore = true)
    @Mapping(target = "startDate", source = "startDate", qualifiedByName = "stringToDate")
    @Mapping(target = "endDate", source = "endDate", qualifiedByName = "stringToDate")
    void updateEntityFromRequest(TrainingPlanRequest request, @MappingTarget TrainingPlan trainingPlan);

    @ValueMapping(source = "PLAN_LEVEL_UNSPECIFIED", target = MappingConstants.NULL)
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
    PlanLevel mapLevel(com.vertice.api.generated.grpc.plan.v1.PlanLevel level);
}
