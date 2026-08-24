package com.vertice.api.student;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.student.v1.StudentCreateRequest;
import com.vertice.api.generated.grpc.student.v1.StudentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(studentRepository, Mappers.getMapper(StudentMapper.class), passwordEncoder);
    }

    @Test
    void createStudent_hashesPasswordBeforeSaving() {
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentCreateRequest request = StudentCreateRequest.newBuilder()
                .setName("New Student")
                .setEmail("student@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        service.createStudent(request);

        var captor = org.mockito.ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        Student saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isNotEqualTo("supersecret1");
        assertThat(passwordEncoder.matches("supersecret1", saved.getPasswordHash())).isTrue();
    }

    @Test
    void createStudent_rejectsDuplicateEmail() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setEmail("student@vertice.com");
        when(studentRepository.findByEmail("student@vertice.com")).thenReturn(Optional.of(existing));

        StudentCreateRequest request = StudentCreateRequest.newBuilder()
                .setName("New Student")
                .setEmail("student@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        assertThatThrownBy(() -> service.createStudent(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_rejectsDuplicateCpf() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setCpf("11144477735");
        when(studentRepository.findByCpf("11144477735")).thenReturn(Optional.of(existing));

        StudentCreateRequest request = StudentCreateRequest.newBuilder()
                .setName("New Student")
                .setEmail("student@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        assertThatThrownBy(() -> service.createStudent(request))
                .isInstanceOf(DuplicateCpfException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_allowsKeepingOwnEmailAndCpf() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setEmail("student@vertice.com");
        existing.setCpf("11144477735");
        existing.setPasswordHash("$2a$10$existingHash");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.findByEmail("student@vertice.com")).thenReturn(Optional.of(existing));
        when(studentRepository.findByCpf("11144477735")).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentRequest request = StudentRequest.newBuilder()
                .setName("New Name")
                .setEmail("student@vertice.com")
                .setCpf("11144477735")
                .build();

        var response = service.updateStudent(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("student@vertice.com");
        assertThat(response.getCpf()).isEqualTo("11144477735");
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$existingHash");
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

        StudentRequest request = StudentRequest.newBuilder()
                .setName("Student One")
                .setEmail("student2@vertice.com")
                .build();

        assertThatThrownBy(() -> service.updateStudent(1L, request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_rejectsCpfOwnedByAnotherStudent() {
        Student target = new Student();
        target.setId(1L);
        target.setEmail("student1@vertice.com");
        target.setCpf("52998224725");

        Student other = new Student();
        other.setId(2L);
        other.setEmail("student1@vertice.com");
        other.setCpf("11144477735");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(target));
        when(studentRepository.findByEmail("student1@vertice.com")).thenReturn(Optional.of(target));
        when(studentRepository.findByCpf("11144477735")).thenReturn(Optional.of(other));

        StudentRequest request = StudentRequest.newBuilder()
                .setName("Student One")
                .setEmail("student1@vertice.com")
                .setCpf("11144477735")
                .build();

        assertThatThrownBy(() -> service.updateStudent(1L, request))
                .isInstanceOf(DuplicateCpfException.class);
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

    @Test
    void setPassword_hashesAndSaves() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setPasswordHash("$2a$10$oldHash");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setPassword(1L, "brandNewPassword1");

        assertThat(existing.getPasswordHash()).isNotEqualTo("$2a$10$oldHash");
        assertThat(existing.getPasswordHash()).isNotEqualTo("brandNewPassword1");
        assertThat(passwordEncoder.matches("brandNewPassword1", existing.getPasswordHash())).isTrue();
    }

    @Test
    void setPassword_throwsWhenMissing() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPassword(99L, "brandNewPassword1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
