package com.vertice.api.plan.exercise;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.exercise.v1.DeleteExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseServiceGrpc;
import com.vertice.api.generated.grpc.exercise.v1.GetExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesResponse;
import com.vertice.api.generated.grpc.exercise.v1.UpdateExerciseRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class ExerciseController extends ExerciseServiceGrpc.ExerciseServiceImplBase {

    private final ExerciseService exerciseService;
    private final GrpcRequestValidator validator;

    @Override
    public void listExercises(ListExercisesRequest request, StreamObserver<ListExercisesResponse> responseObserver) {
        responseObserver.onNext(ListExercisesResponse.newBuilder()
                .addAllExercises(exerciseService.listExercises())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getExercise(GetExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        responseObserver.onNext(exerciseService.getExercise(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createExercise(ExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        validator.validate(new ExerciseValidation(request.getName(), request.getVideoUrl()));
        responseObserver.onNext(exerciseService.createExercise(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateExercise(UpdateExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        validator.validate(new ExerciseValidation(request.getExercise().getName(), request.getExercise().getVideoUrl()));
        responseObserver.onNext(exerciseService.updateExercise(request.getId(), request.getExercise()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteExercise(DeleteExerciseRequest request, StreamObserver<Empty> responseObserver) {
        exerciseService.deleteExercise(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record ExerciseValidation(
            @NotBlank String name,
            @Pattern(regexp = "^$|^https?://\\S+$", message = "must be a valid http(s) URL") String videoUrl) {
    }
}
