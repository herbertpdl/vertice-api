package com.vertice.api.student;

import com.vertice.api.generated.model.StudentRequest;
import com.vertice.api.generated.model.StudentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    Student toEntity(StudentRequest request);

    StudentResponse toResponse(Student student);

    void updateEntityFromRequest(StudentRequest request, @MappingTarget Student student);
}
