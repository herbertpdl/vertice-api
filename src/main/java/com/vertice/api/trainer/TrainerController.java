package com.vertice.api.trainer;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.trainer.v1.DeleteTrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.GetTrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.ListTrainersRequest;
import com.vertice.api.generated.grpc.trainer.v1.ListTrainersResponse;
import com.vertice.api.generated.grpc.trainer.v1.SetTrainerPasswordRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerCreateRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerResponse;
import com.vertice.api.generated.grpc.trainer.v1.TrainerServiceGrpc;
import com.vertice.api.generated.grpc.trainer.v1.UpdateTrainerRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class TrainerController extends TrainerServiceGrpc.TrainerServiceImplBase {

    private final TrainerService trainerService;
    private final GrpcRequestValidator validator;

    @Override
    public void listTrainers(ListTrainersRequest request, StreamObserver<ListTrainersResponse> responseObserver) {
        responseObserver.onNext(ListTrainersResponse.newBuilder()
                .addAllTrainers(trainerService.listTrainers())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTrainer(GetTrainerRequest request, StreamObserver<TrainerResponse> responseObserver) {
        responseObserver.onNext(trainerService.getTrainer(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createTrainer(TrainerCreateRequest request, StreamObserver<TrainerResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getName(), request.getEmail(), request.getPassword()));
        responseObserver.onNext(trainerService.createTrainer(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateTrainer(UpdateTrainerRequest request, StreamObserver<TrainerResponse> responseObserver) {
        validator.validate(new UpdateValidation(request.getTrainer().getName(), request.getTrainer().getEmail()));
        responseObserver.onNext(trainerService.updateTrainer(request.getId(), request.getTrainer()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteTrainer(DeleteTrainerRequest request, StreamObserver<Empty> responseObserver) {
        trainerService.deleteTrainer(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void setTrainerPassword(SetTrainerPasswordRequest request, StreamObserver<Empty> responseObserver) {
        validator.validate(new PasswordValidation(request.getPassword()));
        trainerService.setPassword(request.getId(), request.getPassword());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record CreateValidation(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password) {
    }

    private record UpdateValidation(
            @NotBlank String name,
            @NotBlank @Email String email) {
    }

    private record PasswordValidation(@NotBlank @Size(min = 8) String password) {
    }
}
