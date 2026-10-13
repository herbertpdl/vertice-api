package com.vertice.api.trainerclient;

import com.vertice.api.common.validation.Cpf;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.grpc.GrpcRequestValidator;
import com.vertice.api.user.Emails;
import com.vertice.api.user.PasswordPolicy;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class TrainerClientController extends TrainerClientServiceGrpc.TrainerClientServiceImplBase {

    private final TrainerClientService trainerClientService;
    private final GrpcRequestValidator validator;

    @Override
    public void createClientForTrainer(CreateClientForTrainerRequest request, StreamObserver<UserResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getTrainerId(), request.getName(),
                Emails.normalize(request.getEmail()), emptyToNull(request.getPassword()), request.getCpf()));
        responseObserver.onNext(trainerClientService.createClientForTrainer(request));
        responseObserver.onCompleted();
    }

    @Override
    public void listClientsForTrainer(ListClientsForTrainerRequest request, StreamObserver<ListClientsForTrainerResponse> responseObserver) {
        responseObserver.onNext(trainerClientService.listClientsForTrainer(request.getTrainerId()));
        responseObserver.onCompleted();
    }

    @Override
    public void isTrainersClient(IsTrainersClientRequest request, StreamObserver<IsTrainersClientResponse> responseObserver) {
        responseObserver.onNext(trainerClientService.isTrainersClient(request.getTrainerId(), request.getClientId()));
        responseObserver.onCompleted();
    }

    private record CreateValidation(
            @Min(1) long trainerId,
            @NotBlank String name,
            @NotBlank @Email String email,
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password,
            @Cpf String cpf) {
    }

    /** The password is optional here (empty = created without one), but when sent it must meet the policy. */
    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
