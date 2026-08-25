package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.SetLogResponse;
import com.vertice.api.grpc.ProtoDecimals;
import com.vertice.api.grpc.ProtoInstants;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {ProtoDecimals.class, ProtoInstants.class})
public interface SetLogMapper {

    @Mapping(target = "workoutLogId", source = "workoutLog.id")
    @Mapping(target = "exerciseSetId", source = "exerciseSet.id")
    @Mapping(target = "weight", qualifiedByName = "decimalToString")
    @Mapping(target = "recordedAt", qualifiedByName = "instantToString")
    SetLogResponse toResponse(SetLog setLog);
}
