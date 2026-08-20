package com.vertice.api.trainer;

import com.vertice.api.generated.model.TrainerRequest;
import com.vertice.api.generated.model.TrainerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TrainerMapper {

    Trainer toEntity(TrainerRequest request);

    TrainerResponse toResponse(Trainer trainer);

    void updateEntityFromRequest(TrainerRequest request, @MappingTarget Trainer trainer);
}
