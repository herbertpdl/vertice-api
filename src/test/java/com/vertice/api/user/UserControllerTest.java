package com.vertice.api.user;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.InvalidCredentialsException;
import com.vertice.api.common.exception.InvalidPasswordSetupTokenException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenResponse;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.SetPasswordWithTokenRequest;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.VerifyCredentialsRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19094", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class UserControllerTest {

    private static final String VALID_CPF = "11144477735";

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PasswordSetupTokenService passwordSetupTokenService;

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

    @Test
    void createUser_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("x".repeat(73)).setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withMixedCaseEmailAndSpaces_passesValidation() {
        UserResponse created = UserResponse.newBuilder().setId(1L).setEmail("coach@vertice.com").setRole(Role.TRAINER).build();
        when(userService.createUser(any())).thenReturn(created);

        assertThat(stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail(" Coach@Vertice.com ").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .isEqualTo(created);
    }

    @Test
    void createUser_withoutPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void setUserPassword_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword("x".repeat(73)).build()));
    }

    /** 40 characters, 80 UTF-8 bytes: within a character limit of 72 but over bcrypt's 72 bytes. */
    private static final String MULTI_BYTE_PASSWORD = "é".repeat(40);

    @Test
    void createUser_withPasswordOver72Bytes_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createUser(UserCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword(MULTI_BYTE_PASSWORD).setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void setUserPassword_withPasswordOver72Bytes_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword(MULTI_BYTE_PASSWORD).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void setPasswordWithToken_withPasswordOver72Bytes_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword(MULTI_BYTE_PASSWORD).build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_withMultiBytePasswordOf72Bytes_succeeds() {
        stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("é".repeat(36)).build());

        verify(passwordSetupTokenService).setPasswordWithToken("abc", "é".repeat(36));
    }

    @Test
    void verifyCredentials_withValidCredentials_returnsUser() {
        UserResponse user = UserResponse.newBuilder().setId(1L).setEmail("coach@vertice.com").setRole(Role.TRAINER).build();
        when(userService.verifyCredentials("coach@vertice.com", "supersecret1")).thenReturn(user);

        assertThat(stub.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").setPassword("supersecret1").build()))
                .isEqualTo(user);
    }

    @Test
    void verifyCredentials_withInvalidCredentials_throwsUnauthenticatedWithUniformMessage() {
        when(userService.verifyCredentials(any(), any())).thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> stub.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").setPassword("wrong").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void verifyCredentials_withBlankEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail(" ").setPassword("supersecret1").build()));
    }

    @Test
    void verifyCredentials_withEmptyPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").build()));
    }

    @Test
    void createPasswordSetupToken_returnsToken() {
        CreatePasswordSetupTokenResponse token = CreatePasswordSetupTokenResponse.newBuilder()
                .setToken("abc").setExpiresAt("2026-10-16T12:00:00Z").build();
        when(passwordSetupTokenService.createToken(5L)).thenReturn(token);

        assertThat(stub.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().setUserId(5L).build()))
                .isEqualTo(token);
    }

    @Test
    void createPasswordSetupToken_withMissingUserId_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().build()));
    }

    @Test
    void createPasswordSetupToken_whenUserMissing_throwsNotFound() {
        when(passwordSetupTokenService.createToken(99L)).thenThrow(new ResourceNotFoundException("User", 99L));

        assertThatThrownBy(() -> stub.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().setUserId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setPasswordWithToken_withValidRequest_succeeds() {
        Empty response = stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("brandNewPassword1").build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
        verify(passwordSetupTokenService).setPasswordWithToken("abc", "brandNewPassword1");
    }

    @Test
    void setPasswordWithToken_withInvalidToken_throwsUnauthenticatedWithUniformMessage() {
        doThrow(new InvalidPasswordSetupTokenException())
                .when(passwordSetupTokenService).setPasswordWithToken("used", "brandNewPassword1");

        assertThatThrownBy(() -> stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("used").setPassword("brandNewPassword1").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Invalid or expired password setup token");
                });
    }

    @Test
    void setPasswordWithToken_withBlankToken_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setPassword("brandNewPassword1").build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("short").build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("x".repeat(73)).build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void listUsersWithoutPassword_passesRoleFilterThrough() {
        UserResponse client = UserResponse.newBuilder().setId(3L).setRole(Role.CLIENT).build();
        when(userService.listUsersWithoutPassword(com.vertice.api.user.Role.CLIENT)).thenReturn(java.util.List.of(client));

        assertThat(stub.listUsersWithoutPassword(ListUsersRequest.newBuilder().setRole(Role.CLIENT).build()).getUsersList())
                .containsExactly(client);
    }

    private void assertInvalidArgument(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
