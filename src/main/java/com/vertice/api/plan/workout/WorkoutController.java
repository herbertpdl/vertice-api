package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsResponse;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class WorkoutController extends WorkoutServiceGrpc.WorkoutServiceImplBase {

    private final WorkoutService workoutService;
    private final GrpcRequestValidator validator;

    @Override
    public void listWorkouts(ListWorkoutsRequest request, StreamObserver<ListWorkoutsResponse> responseObserver) {
        responseObserver.onNext(ListWorkoutsResponse.newBuilder()
                .addAllWorkouts(workoutService.listWorkouts(request.getTrainingPlanId()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getWorkout(GetWorkoutRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        responseObserver.onNext(workoutService.getWorkout(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createWorkout(WorkoutCreateRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        validator.validate(new WorkoutValidation(request.getName()));
        responseObserver.onNext(workoutService.createWorkout(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateWorkout(UpdateWorkoutRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        validator.validate(new WorkoutValidation(request.getWorkout().getName()));
        responseObserver.onNext(workoutService.updateWorkout(request.getId(), request.getWorkout()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteWorkout(DeleteWorkoutRequest request, StreamObserver<Empty> responseObserver) {
        workoutService.deleteWorkout(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record WorkoutValidation(@NotBlank String name) {
    }
}
