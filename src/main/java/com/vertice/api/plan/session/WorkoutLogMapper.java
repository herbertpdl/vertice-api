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
}
