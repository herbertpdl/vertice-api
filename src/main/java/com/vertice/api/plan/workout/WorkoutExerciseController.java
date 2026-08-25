package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesResponse;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class WorkoutExerciseController extends WorkoutExerciseServiceGrpc.WorkoutExerciseServiceImplBase {

    private final WorkoutExerciseService workoutExerciseService;
    private final GrpcRequestValidator validator;

    @Override
    public void listWorkoutExercises(ListWorkoutExercisesRequest request, StreamObserver<ListWorkoutExercisesResponse> responseObserver) {
        responseObserver.onNext(ListWorkoutExercisesResponse.newBuilder()
                .addAllWorkoutExercises(workoutExerciseService.listWorkoutExercises(request.getWorkoutId()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getWorkoutExercise(GetWorkoutExerciseRequest request, StreamObserver<WorkoutExerciseResponse> responseObserver) {
        responseObserver.onNext(workoutExerciseService.getWorkoutExercise(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createWorkoutExercise(WorkoutExerciseCreateRequest request, StreamObserver<WorkoutExerciseResponse> responseObserver) {
        validator.validate(new WorkoutExerciseValidation(request.getOrder(), request.getRestSecondsBetweenSets()));
        responseObserver.onNext(workoutExerciseService.createWorkoutExercise(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateWorkoutExercise(UpdateWorkoutExerciseRequest request, StreamObserver<WorkoutExerciseResponse> responseObserver) {
        var workoutExercise = request.getWorkoutExercise();
        validator.validate(new WorkoutExerciseValidation(workoutExercise.getOrder(), workoutExercise.getRestSecondsBetweenSets()));
        responseObserver.onNext(workoutExerciseService.updateWorkoutExercise(request.getId(), workoutExercise));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteWorkoutExercise(DeleteWorkoutExerciseRequest request, StreamObserver<Empty> responseObserver) {
        workoutExerciseService.deleteWorkoutExercise(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record WorkoutExerciseValidation(@Min(1) int order, @Min(0) int restSecondsBetweenSets) {
    }
}
