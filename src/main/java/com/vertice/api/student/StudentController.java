package com.vertice.api.student;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.student.v1.DeleteStudentRequest;
import com.vertice.api.generated.grpc.student.v1.GetStudentRequest;
import com.vertice.api.generated.grpc.student.v1.ListStudentsRequest;
import com.vertice.api.generated.grpc.student.v1.ListStudentsResponse;
import com.vertice.api.generated.grpc.student.v1.SetStudentPasswordRequest;
import com.vertice.api.generated.grpc.student.v1.StudentCreateRequest;
import com.vertice.api.generated.grpc.student.v1.StudentResponse;
import com.vertice.api.generated.grpc.student.v1.StudentServiceGrpc;
import com.vertice.api.generated.grpc.student.v1.UpdateStudentRequest;
import com.vertice.api.common.validation.Cpf;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class StudentController extends StudentServiceGrpc.StudentServiceImplBase {

    private final StudentService studentService;
    private final GrpcRequestValidator validator;

    @Override
    public void listStudents(ListStudentsRequest request, StreamObserver<ListStudentsResponse> responseObserver) {
        responseObserver.onNext(ListStudentsResponse.newBuilder()
                .addAllStudents(studentService.listStudents())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getStudent(GetStudentRequest request, StreamObserver<StudentResponse> responseObserver) {
        responseObserver.onNext(studentService.getStudent(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createStudent(StudentCreateRequest request, StreamObserver<StudentResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getName(), request.getEmail(), request.getPassword(), request.getCpf()));
        responseObserver.onNext(studentService.createStudent(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateStudent(UpdateStudentRequest request, StreamObserver<StudentResponse> responseObserver) {
        validator.validate(new UpdateValidation(request.getStudent().getName(), request.getStudent().getEmail(), request.getStudent().getCpf()));
        responseObserver.onNext(studentService.updateStudent(request.getId(), request.getStudent()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteStudent(DeleteStudentRequest request, StreamObserver<Empty> responseObserver) {
        studentService.deleteStudent(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void setStudentPassword(SetStudentPasswordRequest request, StreamObserver<Empty> responseObserver) {
        validator.validate(new PasswordValidation(request.getPassword()));
        studentService.setPassword(request.getId(), request.getPassword());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record CreateValidation(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @Cpf String cpf) {
    }

    private record UpdateValidation(
            @NotBlank String name,
            @NotBlank @Email String email,
            @Cpf String cpf) {
    }

    private record PasswordValidation(@NotBlank @Size(min = 8) String password) {
    }
}
