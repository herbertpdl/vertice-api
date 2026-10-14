package com.vertice.api.user;

import com.google.protobuf.Empty;
import com.vertice.api.common.validation.Cpf;
import com.vertice.api.common.validation.Password;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenResponse;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersResponse;
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
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    private final UserService userService;
    private final PasswordSetupTokenService passwordSetupTokenService;
    private final GrpcRequestValidator validator;
    private final CallerIdentityResolver callerIdentityResolver;
    private final UserAuthorization authorization;

    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        authorization.checkListUsers(callerIdentityResolver.resolve());
        responseObserver.onNext(ListUsersResponse.newBuilder()
                .addAllUsers(userService.listUsers(roleFilter(request)))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listUsersWithoutPassword(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        Caller caller = callerIdentityResolver.resolve();
        List<UserResponse> users = authorization.checkListUsersWithoutPassword(caller)
                .map(trainerId -> userService.listClientsWithoutPassword(trainerId, roleFilter(request)))
                .orElseGet(() -> userService.listUsersWithoutPassword(roleFilter(request)));
        responseObserver.onNext(ListUsersResponse.newBuilder().addAllUsers(users).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        UserAuthorization.UserView view = authorization.checkGetUser(callerIdentityResolver.resolve(), request.getId());
        UserResponse user = userService.getUser(request.getId());
        responseObserver.onNext(view == UserAuthorization.UserView.FULL ? user : UserResponse.newBuilder()
                .setId(user.getId()).setName(user.getName()).setRole(user.getRole()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void createUser(UserCreateRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getName(), Emails.normalize(request.getEmail()), request.getPassword(), request.getCpf()));
        requireRole(request.getRole());
        authorization.checkCreateUser(callerIdentityResolver.resolve(), domainRole(request.getRole()));
        responseObserver.onNext(userService.createUser(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new UpdateValidation(request.getUser().getName(), Emails.normalize(request.getUser().getEmail()), request.getUser().getCpf()));
        requireRole(request.getUser().getRole());
        authorization.checkUpdateUser(callerIdentityResolver.resolve(), request.getId(), domainRole(request.getUser().getRole()));
        responseObserver.onNext(userService.updateUser(request.getId(), request.getUser()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<Empty> responseObserver) {
        authorization.checkDeleteUser(callerIdentityResolver.resolve(), request.getId());
        userService.deleteUser(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
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
        responseObserver.onNext(userService.verifyCredentials(request.getEmail(), request.getPassword()));
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

    private static com.vertice.api.user.Role roleFilter(ListUsersRequest request) {
        return request.getRole() == Role.ROLE_UNSPECIFIED ? null : domainRole(request.getRole());
    }

    private static com.vertice.api.user.Role domainRole(Role role) {
        return com.vertice.api.user.Role.valueOf(role.name());
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
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Password String password,
            @Cpf String cpf) {
    }

    private record UpdateValidation(
            @NotBlank String name,
            @NotBlank @Email String email,
            @Cpf String cpf) {
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
