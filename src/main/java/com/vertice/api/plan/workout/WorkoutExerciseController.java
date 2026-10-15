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
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.CallerIdentityResolver;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import static com.vertice.api.plan.workout.WorkoutLimits.MAX_NOTES_LENGTH;
import static com.vertice.api.plan.workout.WorkoutLimits.MAX_POSITION;
import static com.vertice.api.plan.workout.WorkoutLimits.MAX_REST_SECONDS;

@GrpcService
@RequiredArgsConstructor
public class WorkoutExerciseController extends WorkoutExerciseServiceGrpc.WorkoutExerciseServiceImplBase {

    private final WorkoutExerciseService workoutExerciseService;
    private final GrpcRequestValidator validator;
    private final CallerIdentityResolver callerIdentityResolver;
    private final WorkoutAuthorization authorization;

    @Override
    public void listWorkoutExercises(ListWorkoutExercisesRequest request, StreamObserver<ListWorkoutExercisesResponse> responseObserver) {
        authorization.checkReadWorkout(callerIdentityResolver.requireUser(), request.getWorkoutId());
        responseObserver.onNext(ListWorkoutExercisesResponse.newBuilder()
                .addAllWorkoutExercises(workoutExerciseService.listWorkoutExercises(request.getWorkoutId()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getWorkoutExercise(GetWorkoutExerciseRequest request, StreamObserver<WorkoutExerciseResponse> responseObserver) {
        authorization.checkReadWorkoutExercise(callerIdentityResolver.requireUser(), request.getId());
        responseObserver.onNext(workoutExerciseService.getWorkoutExercise(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createWorkoutExercise(WorkoutExerciseCreateRequest request, StreamObserver<WorkoutExerciseResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new WorkoutExerciseValidation(
                request.getOrder(), request.getRestSecondsBetweenSets(), request.getNotes()));
        authorization.checkWriteWorkout(caller, request.getWorkoutId());
        responseObserver.onNext(workoutExerciseService.createWorkoutExercise(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateWorkoutExercise(UpdateWorkoutExerciseRequest request, StreamObserver<WorkoutExerciseResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        var workoutExercise = request.getWorkoutExercise();
        validator.validate(new WorkoutExerciseValidation(
                workoutExercise.getOrder(), workoutExercise.getRestSecondsBetweenSets(), workoutExercise.getNotes()));
        authorization.checkWriteWorkoutExercise(caller, request.getId());
        responseObserver.onNext(workoutExerciseService.updateWorkoutExercise(request.getId(), workoutExercise));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteWorkoutExercise(DeleteWorkoutExerciseRequest request, StreamObserver<Empty> responseObserver) {
        authorization.checkWriteWorkoutExercise(callerIdentityResolver.requireUser(), request.getId());
        workoutExerciseService.deleteWorkoutExercise(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record WorkoutExerciseValidation(
            @Min(1) @Max(MAX_POSITION) int order,
            @Min(0) @Max(MAX_REST_SECONDS) int restSecondsBetweenSets,
            @Size(max = MAX_NOTES_LENGTH) String notes) {
    }
}
