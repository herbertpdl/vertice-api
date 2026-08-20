package com.vertice.api.student;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.model.StudentCreateRequest;
import com.vertice.api.generated.model.StudentRequest;
import com.vertice.api.generated.model.StudentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StudentResponse> listStudents() {
        return studentRepository.findAll().stream()
                .map(studentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse getStudent(Long id) {
        return studentMapper.toResponse(findByIdOrThrow(id));
    }

    public StudentResponse createStudent(StudentCreateRequest request) {
        assertEmailAvailable(request.getEmail(), null);
        Student student = studentMapper.toEntity(request);
        student.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return studentMapper.toResponse(studentRepository.save(student));
    }

    public StudentResponse updateStudent(Long id, StudentRequest request) {
        Student student = findByIdOrThrow(id);
        assertEmailAvailable(request.getEmail(), id);
        studentMapper.updateEntityFromRequest(request, student);
        return studentMapper.toResponse(studentRepository.save(student));
    }

    public void deleteStudent(Long id) {
        Student student = findByIdOrThrow(id);
        studentRepository.delete(student);
    }

    public void setPassword(Long id, String rawPassword) {
        Student student = findByIdOrThrow(id);
        student.setPasswordHash(passwordEncoder.encode(rawPassword));
        studentRepository.save(student);
    }

    private Student findByIdOrThrow(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    private void assertEmailAvailable(String email, Long excludingId) {
        studentRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateEmailException(email);
                });
    }
}
