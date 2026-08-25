package com.vertice.api.user;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19094", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class UserControllerTest {

    private static final String VALID_CPF = "11144477735";

    @MockitoBean
    private UserService userService;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19094").usePlaintext().build();
        stub = UserServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listUsers_returnsAll() {
        UserResponse user = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.listUsers(null)).thenReturn(java.util.List.of(user));

        var response = stub.listUsers(ListUsersRequest.newBuilder().build());

        assertThat(response.getUsersList()).containsExactly(user);
    }

    @Test
    void listUsers_withRoleFilter_passesFilterThrough() {
        UserResponse trainer = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.listUsers(com.vertice.api.user.Role.TRAINER)).thenReturn(java.util.List.of(trainer));

        var response = stub.listUsers(ListUsersRequest.newBuilder().setRole(Role.TRAINER).build());

        assertThat(response.getUsersList()).containsExactly(trainer);
    }

    @Test
    void getUser_whenExists_returnsUser() {
        UserResponse user = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.getUser(1L)).thenReturn(user);

        UserResponse response = stub.getUser(GetUserRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(user);
    }

    @Test
    void getUser_whenMissing_throwsNotFound() {
        when(userService.getUser(99L)).thenThrow(new ResourceNotFoundException("User", 99L));

        assertThatThrownBy(() -> stub.getUser(GetUserRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createUser_withValidRequest_returnsCreated() {
        UserResponse created = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.createUser(any())).thenReturn(created);

        UserResponse response = stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createUser_withCref_returnsCreated() {
        UserResponse created = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).setCref("123456-G/SP").build();
        when(userService.createUser(any())).thenReturn(created);

        UserResponse response = stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).setCref("123456-G/SP").build());

        assertThat(response.getCref()).isEqualTo("123456-G/SP");
    }

    @Test
    void createUser_withMissingRole_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createUser_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withMalformedEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("not-an-email").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("short").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf("00000000000").setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withDuplicateEmail_throwsAlreadyExists() {
        when(userService.createUser(any())).thenThrow(new DuplicateEmailException("coach@vertice.com"));

        assertThatThrownBy(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void createUser_withDuplicateCpf_throwsAlreadyExists() {
        when(userService.createUser(any())).thenThrow(new DuplicateCpfException(VALID_CPF));

        assertThatThrownBy(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void updateUser_whenExists_returnsUpdated() {
        UserResponse updated = UserResponse.newBuilder().setId(1L).setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.updateUser(eq(1L), any())).thenReturn(updated);

        UserResponse response = stub.updateUser(UpdateUserRequest.newBuilder()
                .setId(1L)
                .setUser(UserRequest.newBuilder().setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateUser_withMissingRole_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.updateUser(UpdateUserRequest.newBuilder()
                .setId(1L)
                .setUser(UserRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build())
                .build()));
    }

    @Test
    void updateUser_whenMissing_throwsNotFound() {
        when(userService.updateUser(eq(99L), any())).thenThrow(new ResourceNotFoundException("User", 99L));

        assertThatThrownBy(() -> stub.updateUser(UpdateUserRequest.newBuilder()
                .setId(99L)
                .setUser(UserRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteUser_whenExists_succeeds() {
        Empty response = stub.deleteUser(DeleteUserRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteUser_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("User", 99L)).when(userService).deleteUser(99L);

        assertThatThrownBy(() -> stub.deleteUser(DeleteUserRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setUserPassword_whenExists_succeeds() {
        Empty response = stub.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword("brandNewPassword1").build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void setUserPassword_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("User", 99L))
                .when(userService).setPassword(99L, "brandNewPassword1");

        assertThatThrownBy(() -> stub.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(99L).setPassword("brandNewPassword1").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setUserPassword_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword("short").build()));
    }

    private void assertInvalidArgument(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
