package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsResponse;
import com.vertice.api.generated.grpc.plan.v1.ReplaceWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Set;

@GrpcService
@RequiredArgsConstructor
public class WorkoutController extends WorkoutServiceGrpc.WorkoutServiceImplBase {

    private static final int MAX_EXERCISE_ENTRIES = 20;
    private static final int MAX_SET_ENTRIES_PER_EXERCISE = 10;

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

    @Override
    public void createWorkoutWithExercises(CreateWorkoutWithExercisesRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        validator.validate(new WorkoutValidation(request.getName()));
        requireDayOfWeek(request.getDayOfWeek());
        validateExerciseEntries(request.getExercisesList());
        responseObserver.onNext(workoutService.createWorkoutWithExercises(request));
        responseObserver.onCompleted();
    }

    @Override
    public void replaceWorkoutExercises(ReplaceWorkoutExercisesRequest request, StreamObserver<WorkoutResponse> responseObserver) {
        validateExerciseEntries(request.getExercisesList());
        responseObserver.onNext(workoutService.replaceWorkoutExercises(request));
        responseObserver.onCompleted();
    }

    /**
     * Shared by {@code createWorkoutWithExercises}/{@code replaceWorkoutExercises}: caps and
     * per-set numeric checks (R8, R9) — generic messages, not naming the offending entry (§0/F10
     * of docs/specs/create-workout-with-exercises/spec.md). {@code exercise_id} existence and
     * {@code weight}/{@code load_percentage} parsing are checked later, in the service/mapper,
     * since they need repository access / {@code ProtoDecimals} — but still before any write.
     */
    private void validateExerciseEntries(List<WorkoutExerciseEntry> entries) {
        if (entries.size() > MAX_EXERCISE_ENTRIES) {
            throw new ConstraintViolationException(
                    "exercises: must not exceed %d entries".formatted(MAX_EXERCISE_ENTRIES), Set.of());
        }
        for (WorkoutExerciseEntry entry : entries) {
            if (entry.getSetsList().size() > MAX_SET_ENTRIES_PER_EXERCISE) {
                throw new ConstraintViolationException(
                        "sets: must not exceed %d entries".formatted(MAX_SET_ENTRIES_PER_EXERCISE), Set.of());
            }
            for (ExerciseSetEntry set : entry.getSetsList()) {
                validator.validate(new ExerciseSetEntryValidation(set.getReps(), set.getDurationSeconds(), set.getRestSeconds()));
            }
        }
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

    private record ExerciseSetEntryValidation(
            @Min(0) int reps,
            @Min(0) int durationSeconds,
            @Min(0) int restSeconds) {
    }
}
