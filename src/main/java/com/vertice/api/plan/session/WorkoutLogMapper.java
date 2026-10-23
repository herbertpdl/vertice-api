package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.grpc.ProtoDates;
import com.vertice.api.grpc.ProtoInstants;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {ProtoDates.class, ProtoInstants.class})
public interface WorkoutLogMapper {

    @Mapping(target = "workoutId", source = "workout.id")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "weekStartDate", qualifiedByName = "dateToString")
    @Mapping(target = "startedAt", qualifiedByName = "instantToString")
    @Mapping(target = "completedAt", qualifiedByName = "instantToString")
    WorkoutLogResponse toResponse(WorkoutLog workoutLog);

    default com.vertice.api.generated.grpc.session.v1.WorkoutLogStatus toProto(WorkoutLogStatus status) {
        if (status == null) {
            return com.vertice.api.generated.grpc.session.v1.WorkoutLogStatus.WORKOUT_LOG_STATUS_UNSPECIFIED;
        }
        return switch (status) {
            case IN_PROGRESS -> com.vertice.api.generated.grpc.session.v1.WorkoutLogStatus.WORKOUT_LOG_STATUS_IN_PROGRESS;
            case COMPLETED -> com.vertice.api.generated.grpc.session.v1.WorkoutLogStatus.WORKOUT_LOG_STATUS_COMPLETED;
            case SUPERSEDED -> com.vertice.api.generated.grpc.session.v1.WorkoutLogStatus.WORKOUT_LOG_STATUS_SUPERSEDED;
        };
    }

    default com.vertice.api.generated.grpc.session.v1.CompletionKind toProto(CompletionKind kind) {
        if (kind == null) {
            return com.vertice.api.generated.grpc.session.v1.CompletionKind.COMPLETION_KIND_UNSPECIFIED;
        }
        return switch (kind) {
            case CLIENT -> com.vertice.api.generated.grpc.session.v1.CompletionKind.COMPLETION_KIND_CLIENT;
            case AUTO -> com.vertice.api.generated.grpc.session.v1.CompletionKind.COMPLETION_KIND_AUTO;
        };
    }
}
