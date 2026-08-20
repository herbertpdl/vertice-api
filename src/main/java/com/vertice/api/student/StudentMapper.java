package com.vertice.api.student;

import com.vertice.api.generated.model.StudentCreateRequest;
import com.vertice.api.generated.model.StudentRequest;
import com.vertice.api.generated.model.StudentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    Student toEntity(StudentCreateRequest request);

    StudentResponse toResponse(Student student);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    void updateEntityFromRequest(StudentRequest request, @MappingTarget Student student);
}
