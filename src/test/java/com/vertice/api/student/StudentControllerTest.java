package com.vertice.api.student;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.student.v1.DeleteStudentRequest;
import com.vertice.api.generated.grpc.student.v1.GetStudentRequest;
import com.vertice.api.generated.grpc.student.v1.ListStudentsRequest;
import com.vertice.api.generated.grpc.student.v1.ListStudentsResponse;
import com.vertice.api.generated.grpc.student.v1.SetStudentPasswordRequest;
import com.vertice.api.generated.grpc.student.v1.StudentCreateRequest;
import com.vertice.api.generated.grpc.student.v1.StudentRequest;
import com.vertice.api.generated.grpc.student.v1.StudentResponse;
import com.vertice.api.generated.grpc.student.v1.StudentServiceGrpc;
import com.vertice.api.generated.grpc.student.v1.UpdateStudentRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.grpc.server.port=19094")
@ActiveProfiles("local")
class StudentControllerTest {

    private static final String VALID_CPF = "11144477735";

    @MockitoBean
    private StudentService studentService;

    private ManagedChannel channel;
    private StudentServiceGrpc.StudentServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19094").usePlaintext().build();
        stub = StudentServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listStudents_returnsAll() {
        StudentResponse student = StudentResponse.newBuilder().setId(1L).setName("Student").setEmail("student@vertice.com").setCpf(VALID_CPF).build();
        when(studentService.listStudents()).thenReturn(java.util.List.of(student));

        ListStudentsResponse response = stub.listStudents(ListStudentsRequest.newBuilder().build());

        assertThat(response.getStudentsList()).containsExactly(student);
    }

    @Test
    void getStudent_whenExists_returnsStudent() {
        StudentResponse student = StudentResponse.newBuilder().setId(1L).setName("Student").setEmail("student@vertice.com").setCpf(VALID_CPF).build();
        when(studentService.getStudent(1L)).thenReturn(student);

        StudentResponse response = stub.getStudent(GetStudentRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(student);
    }

    @Test
    void getStudent_whenMissing_throwsNotFound() {
        when(studentService.getStudent(99L)).thenThrow(new ResourceNotFoundException("Student", 99L));

        assertThatThrownBy(() -> stub.getStudent(GetStudentRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createStudent_withValidRequest_returnsCreated() {
        StudentResponse created = StudentResponse.newBuilder().setId(1L).setName("Student").setEmail("student@vertice.com").setCpf(VALID_CPF).build();
        when(studentService.createStudent(any())).thenReturn(created);

        StudentResponse response = stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("student@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createStudent_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("").setEmail("student@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createStudent_withMalformedEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("not-an-email").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createStudent_withBlankEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createStudent_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("student@vertice.com").setPassword("short").setCpf(VALID_CPF).build()));
    }

    @Test
    void createStudent_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("student@vertice.com").setPassword("supersecret1").setCpf("00000000000").build()));
    }

    @Test
    void createStudent_withDuplicateEmail_throwsAlreadyExists() {
        when(studentService.createStudent(any())).thenThrow(new DuplicateEmailException("student@vertice.com"));

        assertThatThrownBy(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("student@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void createStudent_withDuplicateCpf_throwsAlreadyExists() {
        when(studentService.createStudent(any())).thenThrow(new DuplicateCpfException(VALID_CPF));

        assertThatThrownBy(() -> stub.createStudent(StudentCreateRequest.newBuilder()
                .setName("Student").setEmail("student@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void updateStudent_whenExists_returnsUpdated() {
        StudentResponse updated = StudentResponse.newBuilder().setId(1L).setName("New Name").setEmail("student@vertice.com").setCpf(VALID_CPF).build();
        when(studentService.updateStudent(eq(1L), any())).thenReturn(updated);

        StudentResponse response = stub.updateStudent(UpdateStudentRequest.newBuilder()
                .setId(1L)
                .setStudent(StudentRequest.newBuilder().setName("New Name").setEmail("student@vertice.com").setCpf(VALID_CPF).build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateStudent_whenMissing_throwsNotFound() {
        when(studentService.updateStudent(eq(99L), any())).thenThrow(new ResourceNotFoundException("Student", 99L));

        assertThatThrownBy(() -> stub.updateStudent(UpdateStudentRequest.newBuilder()
                .setId(99L)
                .setStudent(StudentRequest.newBuilder().setName("Student").setEmail("student@vertice.com").setCpf(VALID_CPF).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateStudent_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.updateStudent(UpdateStudentRequest.newBuilder()
                .setId(1L)
                .setStudent(StudentRequest.newBuilder().setName("").setEmail("student@vertice.com").setCpf(VALID_CPF).build())
                .build()));
    }

    @Test
    void updateStudent_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.updateStudent(UpdateStudentRequest.newBuilder()
                .setId(1L)
                .setStudent(StudentRequest.newBuilder().setName("Student").setEmail("student@vertice.com").setCpf("not-a-cpf").build())
                .build()));
    }

    @Test
    void deleteStudent_whenExists_succeeds() {
        Empty response = stub.deleteStudent(DeleteStudentRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteStudent_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Student", 99L)).when(studentService).deleteStudent(99L);

        assertThatThrownBy(() -> stub.deleteStudent(DeleteStudentRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setStudentPassword_whenExists_succeeds() {
        Empty response = stub.setStudentPassword(SetStudentPasswordRequest.newBuilder()
                .setId(1L).setPassword("brandNewPassword1").build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void setStudentPassword_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Student", 99L))
                .when(studentService).setPassword(99L, "brandNewPassword1");

        assertThatThrownBy(() -> stub.setStudentPassword(SetStudentPasswordRequest.newBuilder()
                .setId(99L).setPassword("brandNewPassword1").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setStudentPassword_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setStudentPassword(SetStudentPasswordRequest.newBuilder()
                .setId(1L).setPassword("short").build()));
    }

    private void assertInvalidArgument(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
