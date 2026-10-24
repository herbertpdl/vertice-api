package com.vertice.api.user;

import com.google.protobuf.Empty;
import com.vertice.api.common.validation.Cpf;
import com.vertice.api.common.validation.Password;
import com.vertice.api.generated.grpc.user.v1.ConsentState;
import com.vertice.api.generated.grpc.user.v1.ConsentType;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenResponse;
import com.vertice.api.generated.grpc.user.v1.DeleteAccountRequest;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
import com.vertice.api.generated.grpc.user.v1.ExportUserDataRequest;
import com.vertice.api.generated.grpc.user.v1.ExportUserDataResponse;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersResponse;
import com.vertice.api.generated.grpc.user.v1.ListUsersWithoutPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.RecordConsentRequest;
import com.vertice.api.generated.grpc.user.v1.RedeemPasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.SetPasswordWithTokenRequest;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.VerifyCredentialsRequest;
import com.vertice.api.grpc.Caller;
import com.vertice.api.grpc.CallerIdentityResolver;
import com.vertice.api.grpc.GrpcRequestValidator;
import com.vertice.api.grpc.ProtoStrings;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Set;

/**
 * Every RPC resolves the caller and checks it with {@link UserAuthorization} before delegating
 * (P1-01); request validation runs first, so a malformed request is INVALID_ARGUMENT for anyone.
 */
@GrpcService
@RequiredArgsConstructor
public class UserController extends UserServiceGrpc.UserServiceImplBase {

    /** A consent text version: what the client was shown, e.g. {@code 2026-10-14} or {@code v1.2}. */
    private static final String CONSENT_VERSION = "[A-Za-z0-9._-]{1,32}";

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final UserDataExportService userDataExportService;
    private final PasswordSetupTokenService passwordSetupTokenService;
    private final GrpcRequestValidator validator;
    private final CallerIdentityResolver callerIdentityResolver;
    private final UserAuthorization authorization;

    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        validator.validate(new PageValidation(request.getPageSize()));
        long afterId = afterId(request.getPageToken());
        authorization.checkListUsers(callerIdentityResolver.resolve());
        responseObserver.onNext(userService.listUsers(roleFilter(request.getRole()), pageSize(request.getPageSize()), afterId));
        responseObserver.onCompleted();
    }

    @Override
    public void listUsersWithoutPassword(ListUsersWithoutPasswordRequest request,
                                         StreamObserver<ListUsersResponse> responseObserver) {
        Caller caller = callerIdentityResolver.resolve();
        com.vertice.api.user.Role roleFilter = roleFilter(request.getRole());
        List<UserResponse> users = authorization.checkListUsersWithoutPassword(caller)
                .map(trainerId -> userService.listClientsWithoutPassword(trainerId, roleFilter))
                .orElseGet(() -> userService.listUsersWithoutPassword(roleFilter));
        responseObserver.onNext(ListUsersResponse.newBuilder().addAllUsers(users).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        UserView view = authorization.checkGetUser(callerIdentityResolver.resolve(), request.getId());
        responseObserver.onNext(view.restrict(userService.getUser(request.getId())));
        responseObserver.onCompleted();
    }

    @Override
    public void createUser(UserCreateRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getName(), Emails.normalize(request.getEmail()), request.getPassword(),
                request.getCpf(), request.getCref(), ProtoStrings.emptyToNull(request.getAcceptedTermsVersion())));
        requireRole(request.getRole());
        // No account without an accepted Terms / Privacy Policy version on record (P1-04).
        if (request.getAcceptedTermsVersion().isEmpty()) {
            throw new ConstraintViolationException("accepted_terms_version: must be set", Set.of());
        }
        authorization.checkCreateUser(callerIdentityResolver.resolve(), domainRole(request.getRole()));
        // The BFF only needs the email for the new session; the CPF it just sent stays out.
        responseObserver.onNext(UserView.CONTACT.restrict(userService.createUser(request)));
        responseObserver.onCompleted();
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new UpdateValidation(request.getUser().getName(), Emails.normalize(request.getUser().getEmail()),
                request.getUser().getCpf(), request.getUser().getCref(), request.getUser().getWhatsapp(),
                request.getUser().getInstagram()));
        requireRole(request.getUser().getRole());
        authorization.checkUpdateUser(callerIdentityResolver.resolve(), request.getId(), domainRole(request.getUser().getRole()));
        responseObserver.onNext(UserView.DETAIL.restrict(userService.updateUser(request.getId(), request.getUser())));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<Empty> responseObserver) {
        authorization.checkDeleteUser(callerIdentityResolver.resolve(), request.getId());
        userService.deleteUser(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /** Self only: the caller is resolved first and is the account deleted (the request has no id). */
    @Override
    public void deleteAccount(DeleteAccountRequest request, StreamObserver<Empty> responseObserver) {
        long userId = authorization.checkDeleteAccount(callerIdentityResolver.resolve());
        accountDeletionService.deleteAccount(userId, userId);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /** Self only: the caller is resolved first and is whose data is exported (the request has no id). */
    @Override
    public void exportUserData(ExportUserDataRequest request, StreamObserver<ExportUserDataResponse> responseObserver) {
        long userId = authorization.checkExportUserData(callerIdentityResolver.resolve());
        responseObserver.onNext(ExportUserDataResponse.newBuilder().setJson(userDataExportService.exportUserData(userId)).build());
        responseObserver.onCompleted();
    }

    @Override
    public void setUserPassword(SetUserPasswordRequest request, StreamObserver<Empty> responseObserver) {
        validator.validate(new PasswordValidation(request.getPassword()));
        authorization.checkSetUserPassword(callerIdentityResolver.resolve(), request.getId());
        userService.setPassword(request.getId(), request.getPassword());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void verifyCredentials(VerifyCredentialsRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new CredentialsValidation(request.getEmail(), request.getPassword()));
        authorization.checkVerifyCredentials(callerIdentityResolver.resolve(), request.getEmail());
        responseObserver.onNext(UserView.CONTACT.restrict(userService.verifyCredentials(request.getEmail(), request.getPassword())));
        responseObserver.onCompleted();
    }

    @Override
    public void createPasswordSetupToken(CreatePasswordSetupTokenRequest request,
                                         StreamObserver<CreatePasswordSetupTokenResponse> responseObserver) {
        validator.validate(new TokenRequestValidation(request.getUserId()));
        authorization.checkCreatePasswordSetupToken(callerIdentityResolver.resolve(), request.getUserId());
        responseObserver.onNext(passwordSetupTokenService.createToken(request.getUserId()));
        responseObserver.onCompleted();
    }

    @Override
    public void setPasswordWithToken(SetPasswordWithTokenRequest request, StreamObserver<Empty> responseObserver) {
        validator.validate(new TokenPasswordValidation(request.getToken(), request.getPassword()));
        authorization.checkSetPasswordWithToken(callerIdentityResolver.resolve());
        passwordSetupTokenService.setPasswordWithToken(request.getToken(), request.getPassword());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /** {@link #setPasswordWithToken} that also says whose password it was, in the contact view. */
    @Override
    public void redeemPasswordSetupToken(RedeemPasswordSetupTokenRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new TokenPasswordValidation(request.getToken(), request.getPassword()));
        authorization.checkSetPasswordWithToken(callerIdentityResolver.resolve());
        responseObserver.onNext(UserView.CONTACT.restrict(
                passwordSetupTokenService.setPasswordWithToken(request.getToken(), request.getPassword())));
        responseObserver.onCompleted();
    }

    /** The caller is resolved first: the request has no user id, the token says whose consent it is. */
    @Override
    public void recordConsent(RecordConsentRequest request, StreamObserver<ConsentState> responseObserver) {
        long userId = authorization.checkRecordConsent(callerIdentityResolver.resolve());
        validator.validate(new ConsentValidation(request.getVersion()));
        if (request.getType() == ConsentType.CONSENT_TYPE_UNSPECIFIED || request.getType() == ConsentType.UNRECOGNIZED) {
            throw new ConstraintViolationException("type: must be set", Set.of());
        }
        responseObserver.onNext(userService.recordConsent(userId, request.getType(), request.getVersion()));
        responseObserver.onCompleted();
    }

    /** {@code ROLE_UNSPECIFIED} → {@code null}, i.e. no role filter. */
    private static com.vertice.api.user.Role roleFilter(Role role) {
        return role == Role.ROLE_UNSPECIFIED ? null : domainRole(role);
    }

    private static com.vertice.api.user.Role domainRole(Role role) {
        return com.vertice.api.user.Role.valueOf(role.name());
    }

    /** 0 is the default; anything larger than the maximum is lowered to it, not refused. */
    private static int pageSize(int requested) {
        return requested == 0 ? UserService.MAX_PAGE_SIZE : Math.min(requested, UserService.MAX_PAGE_SIZE);
    }

    /** The token is the last id of the previous page; empty means the first page. */
    private static long afterId(String pageToken) {
        if (pageToken.isEmpty()) {
            return 0;
        }
        try {
            long afterId = Long.parseLong(pageToken);
            if (afterId > 0) {
                return afterId;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the same error as a non-positive id
        }
        throw new ConstraintViolationException("page_token: invalid", Set.of());
    }

    /**
     * proto3 enums always have a zero value ({@code ROLE_UNSPECIFIED}) — "omitted" and "explicitly
     * unspecified" aren't distinguishable — so this can't be a {@code @NotNull} on the validation
     * record the way other required fields are; it's checked directly instead.
     */
    private void requireRole(Role role) {
        if (role == Role.ROLE_UNSPECIFIED) {
            throw new ConstraintViolationException("role: must be set", Set.of());
        }
    }

    private record CreateValidation(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Password String password,
            @Cpf String cpf,
            @Size(max = 20) String cref,
            @Pattern(regexp = CONSENT_VERSION) String acceptedTermsVersion) {
    }

    private record ConsentValidation(@NotNull @Pattern(regexp = CONSENT_VERSION) String version) {
    }

    private record UpdateValidation(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Cpf String cpf,
            @Size(max = 20) String cref,
            @Pattern(regexp = "^$|^[0-9]{10,11}$", message = "must be 10 or 11 digits (DDD + number)") String whatsapp,
            @Pattern(regexp = "^$|^[A-Za-z0-9._]{1,30}$", message = "must be an Instagram handle without @") String instagram) {
    }

    private record PageValidation(@PositiveOrZero int pageSize) {
    }

    private record PasswordValidation(@NotBlank @Password String password) {
    }

    // No format or length rules on the password here: a login attempt only needs to fail, and rules
    // that reject it early would differ from the uniform "Invalid email or password".
    private record CredentialsValidation(@NotBlank String email, @NotEmpty String password) {
    }

    private record TokenRequestValidation(@Min(1) long userId) {
    }

    private record TokenPasswordValidation(
            @NotBlank String token,
            @NotBlank @Password String password) {
    }
}
