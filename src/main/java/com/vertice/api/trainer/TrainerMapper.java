package com.vertice.api.trainer;

import com.vertice.api.generated.model.TrainerRequest;
import com.vertice.api.generated.model.TrainerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TrainerMapper {

    @Mapping(target = "id", ignore = true)
    Trainer toEntity(TrainerRequest request);

    TrainerResponse toResponse(Trainer trainer);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(TrainerRequest request, @MappingTarget Trainer trainer);
}
