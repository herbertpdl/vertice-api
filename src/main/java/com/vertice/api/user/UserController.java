package com.vertice.api.user;

import com.google.protobuf.Empty;
import com.vertice.api.common.validation.Cpf;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersResponse;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

@GrpcService
@RequiredArgsConstructor
public class UserController extends UserServiceGrpc.UserServiceImplBase {

    private final UserService userService;
    private final GrpcRequestValidator validator;

    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        var roleFilter = request.getRole() == Role.ROLE_UNSPECIFIED
                ? null
                : com.vertice.api.user.Role.valueOf(request.getRole().name());
        responseObserver.onNext(ListUsersResponse.newBuilder()
                .addAllUsers(userService.listUsers(roleFilter))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        responseObserver.onNext(userService.getUser(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createUser(UserCreateRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getName(), request.getEmail(), request.getPassword(), request.getCpf()));
        requireRole(request.getRole());
        responseObserver.onNext(userService.createUser(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new UpdateValidation(request.getUser().getName(), request.getUser().getEmail(), request.getUser().getCpf()));
        requireRole(request.getUser().getRole());
        responseObserver.onNext(userService.updateUser(request.getId(), request.getUser()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<Empty> responseObserver) {
        userService.deleteUser(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void setUserPassword(SetUserPasswordRequest request, StreamObserver<Empty> responseObserver) {
        validator.validate(new PasswordValidation(request.getPassword()));
        userService.setPassword(request.getId(), request.getPassword());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
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
