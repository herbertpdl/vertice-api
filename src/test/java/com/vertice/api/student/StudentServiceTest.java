package com.vertice.api.student;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.model.StudentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(studentRepository, Mappers.getMapper(StudentMapper.class));
    }

    @Test
    void createStudent_rejectsDuplicateEmail() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setEmail("student@vertice.com");
        when(studentRepository.findByEmail("student@vertice.com")).thenReturn(Optional.of(existing));

        StudentRequest request = new StudentRequest("New Student", "student@vertice.com");

        assertThatThrownBy(() -> service.createStudent(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_allowsKeepingOwnEmail() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setEmail("student@vertice.com");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.findByEmail("student@vertice.com")).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentRequest request = new StudentRequest("New Name", "student@vertice.com");

        var response = service.updateStudent(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("student@vertice.com");
    }

    @Test
    void updateStudent_rejectsEmailOwnedByAnotherStudent() {
        Student target = new Student();
        target.setId(1L);
        target.setEmail("student1@vertice.com");

        Student other = new Student();
        other.setId(2L);
        other.setEmail("student2@vertice.com");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(target));
        when(studentRepository.findByEmail("student2@vertice.com")).thenReturn(Optional.of(other));

        StudentRequest request = new StudentRequest("Student One", "student2@vertice.com");

        assertThatThrownBy(() -> service.updateStudent(1L, request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void getStudent_throwsWhenMissing() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudent(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteStudent_throwsWhenMissing() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteStudent(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
