package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import com.vertice.api.grpc.ProtoInstants;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProtoInstants.class)
public interface WorkoutFeedbackMapper {

    @Mapping(target = "workoutLogId", source = "workoutLog.id")
    @Mapping(target = "workoutId", source = "workoutLog.workout.id")
    @Mapping(target = "trainingPlanId", source = "workoutLog.workout.trainingPlan.id")
    @Mapping(target = "clientId", source = "workoutLog.client.id")
    @Mapping(target = "createdAt", qualifiedByName = "instantToString")
    WorkoutFeedbackResponse toResponse(WorkoutFeedback workoutFeedback);
}
