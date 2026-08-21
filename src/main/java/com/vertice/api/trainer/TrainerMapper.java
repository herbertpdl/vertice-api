package com.vertice.api.trainer;

import com.vertice.api.generated.grpc.trainer.v1.TrainerCreateRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TrainerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    Trainer toEntity(TrainerCreateRequest request);

    TrainerResponse toResponse(Trainer trainer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    void updateEntityFromRequest(TrainerRequest request, @MappingTarget Trainer trainer);
}
