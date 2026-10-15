package com.vertice.api.user;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.BusinessRuleViolationException;
import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ExportTooLargeException;
import com.vertice.api.common.exception.InvalidCredentialsException;
import com.vertice.api.common.exception.InvalidPasswordSetupTokenException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.user.v1.ConsentState;
import com.vertice.api.generated.grpc.user.v1.ConsentType;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenResponse;
import com.vertice.api.generated.grpc.user.v1.DeleteAccountRequest;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
import com.vertice.api.generated.grpc.user.v1.ExportUserDataRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersResponse;
import com.vertice.api.generated.grpc.user.v1.ListUsersWithoutPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.RecordConsentRequest;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.SetPasswordWithTokenRequest;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.VerifyCredentialsRequest;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.trainerclient.TrainerClientRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static com.vertice.api.grpc.GrpcAssertions.assertInvalidField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Validation, error mapping and the per-RPC authorization rules ({@link UserAuthorization}) over a
 * real gRPC call. The service layer is mocked; so are the two repositories the rules read, so each
 * test states the relationship or stored user it depends on.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19094", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class UserControllerTest {

    private static final String VALID_CPF = "11144477735";
    private static final String TERMS_VERSION = "2026-10-14";

    private static final long ADMIN_ID = 1L;
    private static final long TRAINER_ID = 10L;
    private static final long OTHER_TRAINER_ID = 11L;
    /** {@link #TRAINER_ID}'s active client, without a password yet. */
    private static final long CLIENT_ID = 20L;
    /** {@link #OTHER_TRAINER_ID}'s active client. */
    private static final long OTHER_CLIENT_ID = 21L;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PasswordSetupTokenService passwordSetupTokenService;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private UserDataExportService userDataExportService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TrainerClientRepository trainerClientRepository;

    @MockitoBean
    private TrainingPlanRepository trainingPlanRepository;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub anonymous;
    private UserServiceGrpc.UserServiceBlockingStub admin;
    private UserServiceGrpc.UserServiceBlockingStub trainer;
    private UserServiceGrpc.UserServiceBlockingStub client;
    private UserServiceGrpc.UserServiceBlockingStub bff;
    private UserServiceGrpc.UserServiceBlockingStub ops;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19094").usePlaintext().build();
        anonymous = UserServiceGrpc.newBlockingStub(channel);
        admin = TestJwts.asCaller(anonymous, ADMIN_ID, com.vertice.api.user.Role.ADMIN);
        trainer = TestJwts.asCaller(anonymous, TRAINER_ID, com.vertice.api.user.Role.TRAINER);
        client = TestJwts.asCaller(anonymous, CLIENT_ID, com.vertice.api.user.Role.CLIENT);
        bff = TestJwts.asService(anonymous, ServiceSubject.BFF);
        ops = TestJwts.asService(anonymous, ServiceSubject.OPS);

        when(trainerClientRepository.existsByTrainerIdAndClientIdAndClientRoleAndEndedAtIsNull(
                TRAINER_ID, CLIENT_ID, com.vertice.api.user.Role.CLIENT)).thenReturn(true);
        when(trainerClientRepository.existsByTrainerIdAndClientIdAndClientRoleAndEndedAtIsNull(
                OTHER_TRAINER_ID, OTHER_CLIENT_ID, com.vertice.api.user.Role.CLIENT)).thenReturn(true);
        // Every caller must still exist with its token's role (CallerIdentityResolver).
        when(userRepository.findRoleById(ADMIN_ID)).thenReturn(Optional.of(com.vertice.api.user.Role.ADMIN));
        stubStoredUser(TRAINER_ID, com.vertice.api.user.Role.TRAINER, "coach@vertice.com", "$2a$hash");
        stubStoredUser(CLIENT_ID, com.vertice.api.user.Role.CLIENT, "client@vertice.com", null);
        stubStoredUser(OTHER_CLIENT_ID, com.vertice.api.user.Role.CLIENT, "other@vertice.com", null);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listUsers_returnsTheServicePage() {
        ListUsersResponse page = ListUsersResponse.newBuilder()
                .addUsers(UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setRole(Role.TRAINER))
                .setNextPageToken("1")
                .build();
        when(userService.listUsers(null, UserService.MAX_PAGE_SIZE, 0L)).thenReturn(page);

        assertThat(admin.listUsers(ListUsersRequest.newBuilder().build())).isEqualTo(page);
    }

    @Test
    void listUsers_withRoleFilter_passesFilterThrough() {
        when(userService.listUsers(com.vertice.api.user.Role.TRAINER, UserService.MAX_PAGE_SIZE, 0L))
                .thenReturn(ListUsersResponse.getDefaultInstance());

        admin.listUsers(ListUsersRequest.newBuilder().setRole(Role.TRAINER).build());

        verify(userService).listUsers(com.vertice.api.user.Role.TRAINER, UserService.MAX_PAGE_SIZE, 0L);
    }

    @Test
    void listUsers_passesPageSizeAndTokenThrough() {
        when(userService.listUsers(null, 20, 42L)).thenReturn(ListUsersResponse.getDefaultInstance());

        admin.listUsers(ListUsersRequest.newBuilder().setPageSize(20).setPageToken("42").build());

        verify(userService).listUsers(null, 20, 42L);
    }

    @Test
    void listUsers_pageSizeAboveTheMaximum_isLoweredToIt() {
        when(userService.listUsers(null, UserService.MAX_PAGE_SIZE, 0L)).thenReturn(ListUsersResponse.getDefaultInstance());

        admin.listUsers(ListUsersRequest.newBuilder().setPageSize(UserService.MAX_PAGE_SIZE + 1).build());
        admin.listUsers(ListUsersRequest.newBuilder().setPageSize(Integer.MAX_VALUE).build());

        verify(userService, times(2)).listUsers(null, UserService.MAX_PAGE_SIZE, 0L);
    }

    @Test
    void listUsers_negativePageSize_isInvalidArgument() {
        assertInvalidArgument(() -> admin.listUsers(ListUsersRequest.newBuilder().setPageSize(-1).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void listUsers_malformedPageToken_isInvalidArgument() {
        for (String token : List.of("abc", "0", "-5", "1.5", "99999999999999999999")) {
            assertInvalidArgument(() -> admin.listUsers(ListUsersRequest.newBuilder().setPageToken(token).build()));
        }
        verifyNoInteractions(userService);
    }

    @Test
    void getUser_whenExists_returnsUser() {
        UserResponse user = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.getUser(1L)).thenReturn(user);

        UserResponse response = admin.getUser(GetUserRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(user);
    }

    @Test
    void getUser_whenMissing_throwsNotFound() {
        when(userService.getUser(99L)).thenThrow(new ResourceNotFoundException("User", 99L));

        assertThatThrownBy(() -> admin.getUser(GetUserRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    /** The BFF only needs the email for the new session: the contact view, no CPF or CREF. */
    @Test
    void createUser_withValidRequest_returnsTheContactView() {
        UserResponse created = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).setCref("123456-G/SP").build();
        when(userService.createUser(any())).thenReturn(created);

        UserResponse response = bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).setCref("123456-G/SP").build());

        assertThat(response).isEqualTo(UserResponse.newBuilder()
                .setId(1L).setName("Coach").setEmail("coach@vertice.com").setRole(Role.TRAINER).build());
    }

    @Test
    void createUser_passesTheAcceptedTermsVersionOn() {
        when(userService.createUser(any())).thenReturn(UserResponse.newBuilder().setId(1L).setRole(Role.TRAINER).build());

        bff.createUser(registration(Role.TRAINER));

        verify(userService).createUser(argThat(request -> request.getAcceptedTermsVersion().equals(TERMS_VERSION)));
    }

    /** No account without an accepted Terms / Privacy Policy version on record (P1-04). */
    @Test
    void createUser_withoutTermsVersion_throwsInvalidArgument() {
        assertThatThrownBy(() -> bff.createUser(registration(Role.TRAINER).toBuilder().clearAcceptedTermsVersion().build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(ex.getStatus().getDescription()).contains("accepted_terms_version");
                });
        verifyNoInteractions(userService);
    }

    @Test
    void createUser_withMissingRole_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createUser_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withMalformedEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("not-an-email").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("short").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf("00000000000").setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withDuplicateEmail_throwsAlreadyExists() {
        when(userService.createUser(any())).thenThrow(new DuplicateEmailException());

        assertThatThrownBy(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void createUser_withDuplicateEmailOrCpf_descriptionDoesNotEchoTheValue() {
        when(userService.createUser(any())).thenThrow(new DuplicateEmailException()).thenThrow(new DuplicateCpfException());
        UserCreateRequest request = UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        assertThat(List.of(descriptionOf(() -> bff.createUser(request)), descriptionOf(() -> bff.createUser(request))))
                .containsExactly("Email already in use", "CPF already in use")
                .allSatisfy(description -> assertThat(description).doesNotContainPattern("\\d").doesNotContain("@"));
    }

    @Test
    void createUser_withDuplicateCpf_throwsAlreadyExists() {
        when(userService.createUser(any())).thenThrow(new DuplicateCpfException());

        assertThatThrownBy(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void updateUser_whenExists_returnsUpdated() {
        UserResponse updated = UserResponse.newBuilder().setId(1L).setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();
        when(userService.updateUser(eq(1L), any())).thenReturn(updated);

        UserResponse response = admin.updateUser(UpdateUserRequest.newBuilder()
                .setId(1L)
                .setUser(UserRequest.newBuilder().setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateUser_withMissingRole_throwsInvalidArgument() {
        assertInvalidArgument(() -> admin.updateUser(UpdateUserRequest.newBuilder()
                .setId(1L)
                .setUser(UserRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build())
                .build()));
    }

    @Test
    void updateUser_whenMissing_throwsNotFound() {
        when(userService.updateUser(eq(99L), any())).thenThrow(new ResourceNotFoundException("User", 99L));

        assertThatThrownBy(() -> admin.updateUser(UpdateUserRequest.newBuilder()
                .setId(99L)
                .setUser(UserRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteUser_whenExists_succeeds() {
        Empty response = admin.deleteUser(DeleteUserRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteUser_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("User", 99L)).when(userService).deleteUser(99L);

        assertThatThrownBy(() -> admin.deleteUser(DeleteUserRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setUserPassword_whenExists_succeeds() {
        Empty response = admin.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword("brandNewPassword1").build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void setUserPassword_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("User", 99L))
                .when(userService).setPassword(99L, "brandNewPassword1");

        assertThatThrownBy(() -> admin.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(99L).setPassword("brandNewPassword1").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setUserPassword_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> admin.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword("short").build()));
    }

    @Test
    void createUser_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("x".repeat(73)).setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void createUser_withMixedCaseEmailAndSpaces_passesValidation() {
        UserResponse created = UserResponse.newBuilder().setId(1L).setEmail("coach@vertice.com").setRole(Role.TRAINER).build();
        when(userService.createUser(any())).thenReturn(created);

        assertThat(bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail(" Coach@Vertice.com ").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .isEqualTo(created);
    }

    @Test
    void createUser_withoutPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
    }

    @Test
    void setUserPassword_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> admin.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword("x".repeat(73)).build()));
    }

    /** 40 characters, 80 UTF-8 bytes: within a character limit of 72 but over bcrypt's 72 bytes. */
    private static final String MULTI_BYTE_PASSWORD = "é".repeat(40);

    @Test
    void createUser_withPasswordOver72Bytes_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword(MULTI_BYTE_PASSWORD).setCpf(VALID_CPF).setRole(Role.TRAINER).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void setUserPassword_withPasswordOver72Bytes_throwsInvalidArgument() {
        assertInvalidArgument(() -> admin.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(1L).setPassword(MULTI_BYTE_PASSWORD).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void setPasswordWithToken_withPasswordOver72Bytes_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword(MULTI_BYTE_PASSWORD).build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_withMultiBytePasswordOf72Bytes_succeeds() {
        bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("é".repeat(36)).build());

        verify(passwordSetupTokenService).setPasswordWithToken("abc", "é".repeat(36));
    }

    @Test
    void verifyCredentials_withValidCredentials_returnsTheContactView() {
        UserResponse user = UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com")
                .setCpf(VALID_CPF).setCref("123456-G/SP").setRole(Role.TRAINER).build();
        when(userService.verifyCredentials("coach@vertice.com", "supersecret1")).thenReturn(user);

        assertThat(bff.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").setPassword("supersecret1").build()))
                .isEqualTo(UserResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com")
                        .setRole(Role.TRAINER).build());
    }

    @Test
    void verifyCredentials_withInvalidCredentials_throwsUnauthenticatedWithUniformMessage() {
        when(userService.verifyCredentials(any(), any())).thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> bff.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").setPassword("wrong").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void verifyCredentials_withBlankEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail(" ").setPassword("supersecret1").build()));
    }

    @Test
    void verifyCredentials_withEmptyPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").build()));
    }

    @Test
    void createPasswordSetupToken_returnsToken() {
        CreatePasswordSetupTokenResponse token = CreatePasswordSetupTokenResponse.newBuilder()
                .setToken("abc").setExpiresAt("2026-10-16T12:00:00Z").build();
        when(passwordSetupTokenService.createToken(5L)).thenReturn(token);

        assertThat(ops.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().setUserId(5L).build()))
                .isEqualTo(token);
    }

    @Test
    void createPasswordSetupToken_withMissingUserId_throwsInvalidArgument() {
        assertInvalidArgument(() -> ops.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().build()));
    }

    @Test
    void createPasswordSetupToken_whenUserMissing_throwsNotFound() {
        when(passwordSetupTokenService.createToken(99L)).thenThrow(new ResourceNotFoundException("User", 99L));

        assertThatThrownBy(() -> ops.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().setUserId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setPasswordWithToken_withValidRequest_succeeds() {
        Empty response = bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("brandNewPassword1").build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
        verify(passwordSetupTokenService).setPasswordWithToken("abc", "brandNewPassword1");
    }

    @Test
    void setPasswordWithToken_withInvalidToken_throwsUnauthenticatedWithUniformMessage() {
        doThrow(new InvalidPasswordSetupTokenException())
                .when(passwordSetupTokenService).setPasswordWithToken("used", "brandNewPassword1");

        assertThatThrownBy(() -> bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("used").setPassword("brandNewPassword1").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Invalid or expired password setup token");
                });
    }

    @Test
    void setPasswordWithToken_withBlankToken_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setPassword("brandNewPassword1").build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("short").build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> bff.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("x".repeat(73)).build()));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void listUsersWithoutPassword_passesRoleFilterThrough() {
        UserResponse client = UserResponse.newBuilder().setId(3L).setRole(Role.CLIENT).build();
        when(userService.listUsersWithoutPassword(com.vertice.api.user.Role.CLIENT)).thenReturn(java.util.List.of(client));

        assertThat(admin.listUsersWithoutPassword(ListUsersWithoutPasswordRequest.newBuilder().setRole(Role.CLIENT).build()).getUsersList())
                .containsExactly(client);
    }

    // --- Authorization (P1-01). Each RPC: who is allowed, and who gets PERMISSION_DENIED. ---

    private static final ListUsersRequest ALL_USERS = ListUsersRequest.getDefaultInstance();
    private static final ListUsersWithoutPasswordRequest ALL_WITHOUT_PASSWORD = ListUsersWithoutPasswordRequest.getDefaultInstance();

    @Test
    void listUsers_asOps_isAllowed() {
        when(userService.listUsers(com.vertice.api.user.Role.CLIENT, UserService.MAX_PAGE_SIZE, 0L))
                .thenReturn(ListUsersResponse.getDefaultInstance());

        assertThat(ops.listUsers(ListUsersRequest.newBuilder().setRole(Role.CLIENT).build()).getUsersList()).isEmpty();
    }

    @Test
    void listUsers_anonymousUnderLocal_isAllowed() {
        // Only the local profile lets a call through without a token: the BFF's VERIFY_PASSWORDS=false dev login.
        when(userService.listUsers(null, UserService.MAX_PAGE_SIZE, 0L)).thenReturn(ListUsersResponse.getDefaultInstance());

        assertThat(anonymous.listUsers(ALL_USERS).getUsersList()).isEmpty();
    }

    @Test
    void listUsers_asTrainer_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.listUsers(ALL_USERS));
        verifyNoInteractions(userService);
    }

    @Test
    void listUsers_asClientOrBffService_isPermissionDenied() {
        assertPermissionDenied(() -> client.listUsers(ALL_USERS));
        assertPermissionDenied(() -> bff.listUsers(ALL_USERS));
        verifyNoInteractions(userService);
    }

    @Test
    void listUsersWithoutPassword_asOps_isPermissionDenied() {
        // The ops script lists clients through ListUsers; its token is scoped to that and CreatePasswordSetupToken.
        assertPermissionDenied(() -> ops.listUsersWithoutPassword(ALL_WITHOUT_PASSWORD));
        verifyNoInteractions(userService);
    }

    @Test
    void listUsersWithoutPassword_asTrainer_isScopedToTheirClients() {
        UserResponse own = UserResponse.newBuilder().setId(CLIENT_ID).setRole(Role.CLIENT).build();
        when(userService.listClientsWithoutPassword(TRAINER_ID, com.vertice.api.user.Role.CLIENT)).thenReturn(List.of(own));

        assertThat(trainer.listUsersWithoutPassword(ListUsersWithoutPasswordRequest.newBuilder().setRole(Role.CLIENT).build()).getUsersList())
                .containsExactly(own);
        verify(userService, never()).listUsersWithoutPassword(any());
    }

    @Test
    void listUsersWithoutPassword_asClientOrBffService_isPermissionDenied() {
        assertPermissionDenied(() -> client.listUsersWithoutPassword(ALL_WITHOUT_PASSWORD));
        assertPermissionDenied(() -> bff.listUsersWithoutPassword(ALL_WITHOUT_PASSWORD));
        verifyNoInteractions(userService);
    }

    @Test
    void getUser_self_isAllowed() {
        UserResponse self = UserResponse.newBuilder().setId(CLIENT_ID).build();
        when(userService.getUser(CLIENT_ID)).thenReturn(self);

        assertThat(client.getUser(GetUserRequest.newBuilder().setId(CLIENT_ID).build())).isEqualTo(self);
    }

    @Test
    void getUser_activeTrainerOfClient_isAllowed() {
        UserResponse own = UserResponse.newBuilder().setId(CLIENT_ID).build();
        when(userService.getUser(CLIENT_ID)).thenReturn(own);

        assertThat(trainer.getUser(GetUserRequest.newBuilder().setId(CLIENT_ID).build())).isEqualTo(own);
    }

    @Test
    void getUser_anotherTrainersClient_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.getUser(GetUserRequest.newBuilder().setId(OTHER_CLIENT_ID).build()));
        verifyNoInteractions(userService);
    }

    /** The dashboard names the client of every plan a trainer wrote, including clients who left. */
    @Test
    void getUser_trainerWithAPlanForAFormerClient_getsIdNameAndRoleOnly() {
        when(trainingPlanRepository.existsByTrainerIdAndClientId(TRAINER_ID, OTHER_CLIENT_ID)).thenReturn(true);
        when(userService.getUser(OTHER_CLIENT_ID)).thenReturn(UserResponse.newBuilder()
                .setId(OTHER_CLIENT_ID).setName("Former").setEmail("former@vertice.com").setCpf(VALID_CPF)
                .setRole(Role.CLIENT).build());

        UserResponse response = trainer.getUser(GetUserRequest.newBuilder().setId(OTHER_CLIENT_ID).build());

        assertThat(response).isEqualTo(UserResponse.newBuilder()
                .setId(OTHER_CLIENT_ID).setName("Former").setRole(Role.CLIENT).build());
    }

    @Test
    void getUser_clientWithAPlanFromTheTarget_isStillPermissionDenied() {
        when(trainingPlanRepository.existsByTrainerIdAndClientId(CLIENT_ID, TRAINER_ID)).thenReturn(true);

        assertPermissionDenied(() -> client.getUser(GetUserRequest.newBuilder().setId(TRAINER_ID).build()));
    }

    @Test
    void getUser_clientReadingTheirTrainerOrAService_isPermissionDenied() {
        assertPermissionDenied(() -> client.getUser(GetUserRequest.newBuilder().setId(TRAINER_ID).build()));
        assertPermissionDenied(() -> bff.getUser(GetUserRequest.newBuilder().setId(CLIENT_ID).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void getUser_anonymous_isUnauthenticated() {
        assertCode(() -> anonymous.getUser(GetUserRequest.newBuilder().setId(CLIENT_ID).build()), Status.Code.UNAUTHENTICATED);
    }

    private static UserCreateRequest registration(Role role) {
        return UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(role).build();
    }

    @Test
    void createUser_asAnyUserOrOps_isPermissionDenied() {
        assertPermissionDenied(() -> admin.createUser(registration(Role.TRAINER)));
        assertPermissionDenied(() -> trainer.createUser(registration(Role.TRAINER)));
        assertPermissionDenied(() -> ops.createUser(registration(Role.TRAINER)));
        verifyNoInteractions(userService);
    }

    @Test
    void createUser_asBffService_withAdminRole_isPermissionDenied() {
        assertPermissionDenied(() -> bff.createUser(registration(Role.ADMIN)));
        verifyNoInteractions(userService);
    }

    private static UpdateUserRequest update(long id, Role role) {
        return UpdateUserRequest.newBuilder()
                .setId(id)
                .setUser(UserRequest.newBuilder().setName("Renamed").setEmail("renamed@vertice.com").setCpf(VALID_CPF).setRole(role).build())
                .build();
    }

    @Test
    void updateUser_selfKeepingRole_isAllowed() {
        when(userService.updateUser(eq(CLIENT_ID), any())).thenReturn(UserResponse.newBuilder().setId(CLIENT_ID).build());

        client.updateUser(update(CLIENT_ID, Role.CLIENT));

        verify(userService).updateUser(eq(CLIENT_ID), any());
    }

    @Test
    void updateUser_selfChangingRole_isPermissionDenied() {
        assertPermissionDenied(() -> client.updateUser(update(CLIENT_ID, Role.ADMIN)));
        assertPermissionDenied(() -> trainer.updateUser(update(TRAINER_ID, Role.ADMIN)));
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_trainerEditingOwnClient_isAllowed() {
        // The BFF's PATCH /clients/:id, which always sends role CLIENT.
        when(userService.updateUser(eq(CLIENT_ID), any())).thenReturn(UserResponse.newBuilder().setId(CLIENT_ID).build());

        trainer.updateUser(update(CLIENT_ID, Role.CLIENT));

        verify(userService).updateUser(eq(CLIENT_ID), any());
    }

    @Test
    void updateUser_trainerChangingOwnClientsRole_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.updateUser(update(CLIENT_ID, Role.TRAINER)));
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_anotherTrainersClientOrAnotherUser_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.updateUser(update(OTHER_CLIENT_ID, Role.CLIENT)));
        assertPermissionDenied(() -> client.updateUser(update(TRAINER_ID, Role.TRAINER)));
        assertPermissionDenied(() -> bff.updateUser(update(CLIENT_ID, Role.CLIENT)));
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_adminChangingRole_isAllowed() {
        when(userService.updateUser(eq(CLIENT_ID), any())).thenReturn(UserResponse.newBuilder().setId(CLIENT_ID).build());

        admin.updateUser(update(CLIENT_ID, Role.TRAINER));

        verify(userService).updateUser(eq(CLIENT_ID), any());
    }

    @Test
    void deleteUser_self_isAllowed() {
        client.deleteUser(DeleteUserRequest.newBuilder().setId(CLIENT_ID).build());

        verify(userService).deleteUser(CLIENT_ID);
    }

    @Test
    void deleteUser_trainerDeletingOwnClientOrAnotherUser_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.deleteUser(DeleteUserRequest.newBuilder().setId(CLIENT_ID).build()));
        assertPermissionDenied(() -> client.deleteUser(DeleteUserRequest.newBuilder().setId(TRAINER_ID).build()));
        assertPermissionDenied(() -> ops.deleteUser(DeleteUserRequest.newBuilder().setId(CLIENT_ID).build()));
        verifyNoInteractions(userService);
    }

    /** The request has no id: whoever calls deletes their own account, ADMIN included, and nobody else's. */
    @Test
    void deleteAccount_deletesTheCallersOwnAccount() {
        client.deleteAccount(DeleteAccountRequest.getDefaultInstance());
        trainer.deleteAccount(DeleteAccountRequest.getDefaultInstance());
        admin.deleteAccount(DeleteAccountRequest.getDefaultInstance());

        verify(accountDeletionService).deleteAccount(CLIENT_ID, CLIENT_ID);
        verify(accountDeletionService).deleteAccount(TRAINER_ID, TRAINER_ID);
        verify(accountDeletionService).deleteAccount(ADMIN_ID, ADMIN_ID);
    }

    @Test
    void deleteAccount_serviceIdentities_arePermissionDenied() {
        assertPermissionDenied(() -> bff.deleteAccount(DeleteAccountRequest.getDefaultInstance()));
        assertPermissionDenied(() -> ops.deleteAccount(DeleteAccountRequest.getDefaultInstance()));
        verifyNoInteractions(accountDeletionService);
    }

    @Test
    void deleteAccount_trainerWithActiveClients_isFailedPrecondition() {
        doThrow(new BusinessRuleViolationException("End your client relationships first"))
                .when(accountDeletionService).deleteAccount(TRAINER_ID, TRAINER_ID);

        assertThatThrownBy(() -> trainer.deleteAccount(DeleteAccountRequest.getDefaultInstance()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    /** The request has no id: whoever calls gets their own data, ADMIN included, never anyone else's. */
    @Test
    void exportUserData_returnsTheCallersOwnExport() {
        when(userDataExportService.exportUserData(CLIENT_ID)).thenReturn("{\"formatVersion\":1}");
        when(userDataExportService.exportUserData(TRAINER_ID)).thenReturn("{}");
        when(userDataExportService.exportUserData(ADMIN_ID)).thenReturn("{}");

        assertThat(client.exportUserData(ExportUserDataRequest.getDefaultInstance()).getJson()).isEqualTo("{\"formatVersion\":1}");
        trainer.exportUserData(ExportUserDataRequest.getDefaultInstance());
        admin.exportUserData(ExportUserDataRequest.getDefaultInstance());

        verify(userDataExportService).exportUserData(CLIENT_ID);
        verify(userDataExportService).exportUserData(TRAINER_ID);
        verify(userDataExportService).exportUserData(ADMIN_ID);
    }

    @Test
    void exportUserData_serviceIdentities_arePermissionDenied() {
        assertPermissionDenied(() -> bff.exportUserData(ExportUserDataRequest.getDefaultInstance()));
        assertPermissionDenied(() -> ops.exportUserData(ExportUserDataRequest.getDefaultInstance()));
        verifyNoInteractions(userDataExportService);
    }

    @Test
    void exportUserData_tooLarge_isResourceExhausted() {
        when(userDataExportService.exportUserData(CLIENT_ID))
                .thenThrow(new ExportTooLargeException("plans exceed 20000 rows"));

        assertThatThrownBy(() -> client.exportUserData(ExportUserDataRequest.getDefaultInstance()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    @Test
    void setUserPassword_self_isAllowed() {
        // The BFF's POST /auth/password, with the user's own token.
        trainer.setUserPassword(SetUserPasswordRequest.newBuilder().setId(TRAINER_ID).setPassword("brandNewPassword1").build());

        verify(userService).setPassword(TRAINER_ID, "brandNewPassword1");
    }

    @Test
    void setUserPassword_forSomeoneElse_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(CLIENT_ID).setPassword("brandNewPassword1").build()));
        assertPermissionDenied(() -> bff.setUserPassword(SetUserPasswordRequest.newBuilder()
                .setId(CLIENT_ID).setPassword("brandNewPassword1").build()));
        verifyNoInteractions(userService);
    }

    @Test
    void verifyCredentials_userCheckingOwnEmail_isAllowed() {
        // The BFF's POST /auth/password re-checks the current password with the user's token.
        UserResponse self = UserResponse.newBuilder().setId(TRAINER_ID).build();
        when(userService.verifyCredentials(" Coach@Vertice.com", "supersecret1")).thenReturn(self);

        assertThat(trainer.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail(" Coach@Vertice.com").setPassword("supersecret1").build()))
                .isEqualTo(self);
    }

    @Test
    void verifyCredentials_userCheckingAnotherEmail_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("client@vertice.com").setPassword("guess").build()));
        verifyNoInteractions(userService);
    }

    @Test
    void verifyCredentials_asOps_isPermissionDenied() {
        assertPermissionDenied(() -> ops.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail("coach@vertice.com").setPassword("guess").build()));
        verifyNoInteractions(userService);
    }

    private static CreatePasswordSetupTokenRequest setupTokenFor(long userId) {
        return CreatePasswordSetupTokenRequest.newBuilder().setUserId(userId).build();
    }

    @Test
    void createPasswordSetupToken_asAdmin_isAllowed() {
        admin.createPasswordSetupToken(setupTokenFor(CLIENT_ID));

        verify(passwordSetupTokenService).createToken(CLIENT_ID);
    }

    @Test
    void createPasswordSetupToken_trainerForOwnClientWithoutPassword_isAllowed() {
        // The BFF's POST /clients (right after CreateClientForTrainer) and POST /clients/:id/setup-token.
        trainer.createPasswordSetupToken(setupTokenFor(CLIENT_ID));

        verify(passwordSetupTokenService).createToken(CLIENT_ID);
    }

    @Test
    void createPasswordSetupToken_trainerForOwnClientWithPassword_isPermissionDenied() {
        stubStoredUser(CLIENT_ID, com.vertice.api.user.Role.CLIENT, "client@vertice.com", "$2a$hash");

        assertPermissionDenied(() -> trainer.createPasswordSetupToken(setupTokenFor(CLIENT_ID)));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void createPasswordSetupToken_trainerForAnotherTrainersClient_isPermissionDenied() {
        assertPermissionDenied(() -> trainer.createPasswordSetupToken(setupTokenFor(OTHER_CLIENT_ID)));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void createPasswordSetupToken_asClientOrBffService_isPermissionDenied() {
        assertPermissionDenied(() -> client.createPasswordSetupToken(setupTokenFor(CLIENT_ID)));
        assertPermissionDenied(() -> bff.createPasswordSetupToken(setupTokenFor(CLIENT_ID)));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_asUserOrOps_isPermissionDenied() {
        SetPasswordWithTokenRequest request = SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("brandNewPassword1").build();

        assertPermissionDenied(() -> client.setPasswordWithToken(request));
        assertPermissionDenied(() -> admin.setPasswordWithToken(request));
        assertPermissionDenied(() -> ops.setPasswordWithToken(request));
        verifyNoInteractions(passwordSetupTokenService);
    }

    @Test
    void setPasswordWithToken_anonymous_isUnauthenticated() {
        assertCode(() -> anonymous.setPasswordWithToken(SetPasswordWithTokenRequest.newBuilder()
                .setToken("abc").setPassword("brandNewPassword1").build()), Status.Code.UNAUTHENTICATED);
    }

    // --- P1-04: consent ---

    private static final ConsentState CONSENT = ConsentState.newBuilder()
            .setAcceptedTermsVersion("2026-10-14").setTermsAcceptedAt("2026-10-14T12:00:00Z").build();

    /** Registration until vertice-bff sends the version: accepted without it (the switch is off). */
    @Test
    void createUser_withoutTermsVersion_isAcceptedWhileTheSwitchIsOff() {
        when(userService.createUser(any())).thenReturn(UserResponse.newBuilder().setId(1L).setRole(Role.TRAINER).build());

        bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).build());

        verify(userService).createUser(any());
    }

    @Test
    void createUser_withTermsVersion_passesItToTheService() {
        when(userService.createUser(any())).thenReturn(UserResponse.newBuilder().setId(1L).setRole(Role.TRAINER).build());
        UserCreateRequest request = UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION).setName("Coach").setEmail("coach@vertice.com")
                .setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER).setAcceptedTermsVersion("2026-10-14").build();

        bff.createUser(request);

        verify(userService).createUser(request);
    }

    @Test
    void createUser_withMalformedTermsVersion_throwsInvalidArgument() {
        UserCreateRequest.Builder request = UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION).setName("Coach").setEmail("coach@vertice.com")
                .setPassword("supersecret1").setCpf(VALID_CPF).setRole(Role.TRAINER);

        assertInvalidArgument(() -> bff.createUser(request.setAcceptedTermsVersion(" ").build()));
        assertInvalidArgument(() -> bff.createUser(request.setAcceptedTermsVersion("v".repeat(33)).build()));
        verifyNoInteractions(userService);
    }

    @Test
    void createUser_clientWithoutCpf_passesValidation() {
        when(userService.createUser(any())).thenReturn(UserResponse.newBuilder().setId(1L).setRole(Role.CLIENT).build());

        bff.createUser(UserCreateRequest.newBuilder().setAcceptedTermsVersion(TERMS_VERSION)
                .setName("Client").setEmail("client@vertice.com").setPassword("supersecret1").setRole(Role.CLIENT).build());

        verify(userService).createUser(any());
    }

    @Test
    void getUser_self_includesTheConsentState() {
        UserResponse stored = UserResponse.newBuilder().setId(CLIENT_ID).setConsent(CONSENT).build();
        when(userService.getUser(CLIENT_ID)).thenReturn(stored);

        assertThat(client.getUser(GetUserRequest.newBuilder().setId(CLIENT_ID).build()).getConsent()).isEqualTo(CONSENT);
    }

    @Test
    void getUser_adminOrActiveTrainer_doNotSeeTheConsentState() {
        when(userService.getUser(CLIENT_ID)).thenReturn(UserResponse.newBuilder().setId(CLIENT_ID).setCpf(VALID_CPF).setConsent(CONSENT).build());

        for (var caller : List.of(admin, trainer)) {
            UserResponse response = caller.getUser(GetUserRequest.newBuilder().setId(CLIENT_ID).build());
            assertThat(response.hasConsent()).isFalse();
            assertThat(response.getCpf()).isEqualTo(VALID_CPF);
        }
    }

    @Test
    void updateUser_responseCarriesNoConsentState() {
        when(userService.updateUser(eq(CLIENT_ID), any())).thenReturn(UserResponse.newBuilder().setId(CLIENT_ID).setConsent(CONSENT).build());

        UserResponse response = client.updateUser(UpdateUserRequest.newBuilder().setId(CLIENT_ID)
                .setUser(UserRequest.newBuilder().setName("Client").setEmail("client@vertice.com").setRole(Role.CLIENT)).build());

        assertThat(response.hasConsent()).isFalse();
    }

    @Test
    void recordConsent_recordsForTheCallerThemself() {
        when(userService.recordConsent(CLIENT_ID, ConsentType.HEALTH_DATA, "2026-10-14")).thenReturn(CONSENT);

        ConsentState state = client.recordConsent(RecordConsentRequest.newBuilder()
                .setType(ConsentType.HEALTH_DATA).setVersion("2026-10-14").build());

        assertThat(state).isEqualTo(CONSENT);
    }

    /** There is no user id to aim at: an ADMIN's call records the ADMIN's own consent. */
    @Test
    void recordConsent_asAdmin_recordsTheAdminsOwn() {
        when(userService.recordConsent(ADMIN_ID, ConsentType.TERMS, "2026-10-14")).thenReturn(CONSENT);

        admin.recordConsent(RecordConsentRequest.newBuilder().setType(ConsentType.TERMS).setVersion("2026-10-14").build());

        verify(userService).recordConsent(ADMIN_ID, ConsentType.TERMS, "2026-10-14");
    }

    @Test
    void recordConsent_asAService_isPermissionDenied_andAnonymousUnauthenticated() {
        RecordConsentRequest request = RecordConsentRequest.newBuilder().setType(ConsentType.TERMS).setVersion("2026-10-14").build();

        assertPermissionDenied(() -> bff.recordConsent(request));
        assertPermissionDenied(() -> ops.recordConsent(request));
        assertCode(() -> anonymous.recordConsent(request), Status.Code.UNAUTHENTICATED);
        verifyNoInteractions(userService);
    }

    @Test
    void recordConsent_withoutTypeOrVersion_throwsInvalidArgument() {
        assertInvalidArgument(() -> client.recordConsent(RecordConsentRequest.newBuilder().setVersion("2026-10-14").build()));
        assertInvalidArgument(() -> client.recordConsent(RecordConsentRequest.newBuilder().setType(ConsentType.TERMS).build()));
        assertInvalidArgument(() -> client.recordConsent(RecordConsentRequest.newBuilder()
                .setType(ConsentType.TERMS).setVersion("2026 10 14").build()));
        verifyNoInteractions(userService);
    }

    private void assertInvalidArgument(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    private void assertCode(ThrowingCallable callable, Status.Code code) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(code);
    }

    private static String descriptionOf(ThrowingCallable callable) {
        return catchThrowableOfType(StatusRuntimeException.class, callable).getStatus().getDescription();
    }

    private void assertPermissionDenied(ThrowingCallable callable) {
        assertCode(callable, Status.Code.PERMISSION_DENIED);
    }

    private void stubStoredUser(long id, com.vertice.api.user.Role role, String email, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.findRoleById(id)).thenReturn(Optional.of(role));
    }

    // Upper bounds (P1-09): users.name and email are VARCHAR(255), cref VARCHAR(20). Both addresses
    // are valid for @Email (64-character local part, labels of at most 63), so only the length decides.
    private static final String EMAIL_255 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com";
    private static final String EMAIL_256 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(59) + ".com";

    @Test
    void createUser_withEveryTextFieldAtItsBound_isAccepted() {
        when(userService.createUser(any())).thenReturn(UserResponse.newBuilder().setId(1L).setRole(Role.TRAINER).build());

        bff.createUser(registration(Role.TRAINER).toBuilder()
                .setName("n".repeat(255)).setEmail(EMAIL_255).setCref("c".repeat(20)).build());
    }

    @Test
    void createUser_pastABound_throwsInvalidArgument() {
        UserCreateRequest valid = registration(Role.TRAINER);

        assertInvalidField(() -> bff.createUser(valid.toBuilder().setName("n".repeat(256)).build()), "name");
        assertInvalidField(() -> bff.createUser(valid.toBuilder().setEmail(EMAIL_256).build()), "email");
        assertInvalidField(() -> bff.createUser(valid.toBuilder().setCref("c".repeat(21)).build()), "cref");
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_pastABound_throwsInvalidArgument() {
        UserRequest valid = UserRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        assertInvalidField(() -> admin.updateUser(UpdateUserRequest.newBuilder().setId(1L)
                .setUser(valid.toBuilder().setName("n".repeat(256))).build()), "name");
        assertInvalidField(() -> admin.updateUser(UpdateUserRequest.newBuilder().setId(1L)
                .setUser(valid.toBuilder().setEmail(EMAIL_256)).build()), "email");
        assertInvalidField(() -> admin.updateUser(UpdateUserRequest.newBuilder().setId(1L)
                .setUser(valid.toBuilder().setCref("c".repeat(21))).build()), "cref");
        verify(userService, never()).updateUser(any(), any());
    }
}
