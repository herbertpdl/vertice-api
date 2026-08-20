package com.vertice.api.student;

import com.vertice.api.generated.api.StudentsApi;
import com.vertice.api.generated.model.SetPasswordRequest;
import com.vertice.api.generated.model.StudentCreateRequest;
import com.vertice.api.generated.model.StudentRequest;
import com.vertice.api.generated.model.StudentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StudentController implements StudentsApi {

    private final StudentService studentService;

    @Override
    public ResponseEntity<List<StudentResponse>> listStudents() {
        return ResponseEntity.ok(studentService.listStudents());
    }

    @Override
    public ResponseEntity<StudentResponse> createStudent(StudentCreateRequest studentCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(studentCreateRequest));
    }

    @Override
    public ResponseEntity<StudentResponse> getStudent(Long id) {
        return ResponseEntity.ok(studentService.getStudent(id));
    }

    @Override
    public ResponseEntity<StudentResponse> updateStudent(Long id, StudentRequest studentRequest) {
        return ResponseEntity.ok(studentService.updateStudent(id, studentRequest));
    }

    @Override
    public ResponseEntity<Void> deleteStudent(Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> setStudentPassword(Long id, SetPasswordRequest setPasswordRequest) {
        studentService.setPassword(id, setPasswordRequest.getPassword());
        return ResponseEntity.noContent().build();
    }
}
