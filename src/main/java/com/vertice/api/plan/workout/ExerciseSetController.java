package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.DeleteExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.GetExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsResponse;
import com.vertice.api.generated.grpc.plan.v1.SetStrategy;
import com.vertice.api.generated.grpc.plan.v1.UpdateExerciseSetRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

@GrpcService
@RequiredArgsConstructor
public class ExerciseSetController extends ExerciseSetServiceGrpc.ExerciseSetServiceImplBase {

    private final ExerciseSetService exerciseSetService;
    private final GrpcRequestValidator validator;

    @Override
    public void listExerciseSets(ListExerciseSetsRequest request, StreamObserver<ListExerciseSetsResponse> responseObserver) {
        responseObserver.onNext(ListExerciseSetsResponse.newBuilder()
                .addAllExerciseSets(exerciseSetService.listExerciseSets(request.getWorkoutExerciseId()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getExerciseSet(GetExerciseSetRequest request, StreamObserver<ExerciseSetResponse> responseObserver) {
        responseObserver.onNext(exerciseSetService.getExerciseSet(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createExerciseSet(ExerciseSetCreateRequest request, StreamObserver<ExerciseSetResponse> responseObserver) {
        validator.validate(new ExerciseSetValidation(
                request.getSetNumber(), request.getReps(), request.getDurationSeconds(), request.getRestSeconds()));
        requireStrategy(request.getStrategy());
        responseObserver.onNext(exerciseSetService.createExerciseSet(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateExerciseSet(UpdateExerciseSetRequest request, StreamObserver<ExerciseSetResponse> responseObserver) {
        var exerciseSet = request.getExerciseSet();
        validator.validate(new ExerciseSetValidation(
                exerciseSet.getSetNumber(), exerciseSet.getReps(), exerciseSet.getDurationSeconds(), exerciseSet.getRestSeconds()));
        requireStrategy(exerciseSet.getStrategy());
        responseObserver.onNext(exerciseSetService.updateExerciseSet(request.getId(), exerciseSet));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteExerciseSet(DeleteExerciseSetRequest request, StreamObserver<Empty> responseObserver) {
        exerciseSetService.deleteExerciseSet(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * Same reasoning as {@code WorkoutController#requireDayOfWeek}: proto3 enums always carry a
     * zero value ({@code SET_STRATEGY_UNSPECIFIED}), so "omitted" can't be a {@code @NotNull} on
     * the validation record — it's checked directly instead.
     */
    private void requireStrategy(SetStrategy strategy) {
        if (strategy == SetStrategy.SET_STRATEGY_UNSPECIFIED) {
            throw new ConstraintViolationException("strategy: must be set", Set.of());
        }
    }

    private record ExerciseSetValidation(
            @Min(1) int setNumber,
            @Min(0) int reps,
            @Min(0) int durationSeconds,
            @Min(0) int restSeconds) {
    }
}
