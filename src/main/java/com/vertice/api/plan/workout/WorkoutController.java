package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
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
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

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
        requireDayOfWeek(request.getDayOfWeek());
        responseObserver.onNext(workoutService.createWorkout(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateWorkout(UpdateWorkoutRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        validator.validate(new WorkoutValidation(request.getWorkout().getName()));
        requireDayOfWeek(request.getWorkout().getDayOfWeek());
        responseObserver.onNext(workoutService.updateWorkout(request.getId(), request.getWorkout()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteWorkout(DeleteWorkoutRequest request, StreamObserver<Empty> responseObserver) {
        workoutService.deleteWorkout(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void cloneWorkout(CloneWorkoutRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        validator.validate(new WorkoutValidation(request.getName()));
        requireDayOfWeek(request.getDayOfWeek());
        responseObserver.onNext(workoutService.cloneWorkout(request));
        responseObserver.onCompleted();
    }

    /**
     * Same reasoning as {@code UserController#requireRole}/{@code TrainingPlanController#requireLevel}:
     * proto3 enums always carry a zero value ({@code DAY_OF_WEEK_UNSPECIFIED}), so "omitted"
     * can't be a {@code @NotNull} on the validation record — it's checked directly instead.
     */
    private void requireDayOfWeek(DayOfWeek dayOfWeek) {
        if (dayOfWeek == DayOfWeek.DAY_OF_WEEK_UNSPECIFIED) {
            throw new ConstraintViolationException("dayOfWeek: must be set", Set.of());
        }
    }

    private record WorkoutValidation(@NotBlank String name) {
    }
}
