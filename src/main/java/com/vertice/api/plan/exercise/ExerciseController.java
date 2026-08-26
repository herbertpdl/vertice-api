package com.vertice.api.plan.exercise;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.exercise.v1.DeleteExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseServiceGrpc;
import com.vertice.api.generated.grpc.exercise.v1.GetExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroup;
import com.vertice.api.generated.grpc.exercise.v1.UpdateExerciseRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

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
        requireMuscleGroup(request.getMuscleGroup());
        responseObserver.onNext(exerciseService.createExercise(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateExercise(UpdateExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        validator.validate(new ExerciseValidation(request.getExercise().getName(), request.getExercise().getVideoUrl()));
        requireMuscleGroup(request.getExercise().getMuscleGroup());
        responseObserver.onNext(exerciseService.updateExercise(request.getId(), request.getExercise()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteExercise(DeleteExerciseRequest request, StreamObserver<Empty> responseObserver) {
        exerciseService.deleteExercise(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * Same reasoning as {@code WorkoutController#requireDayOfWeek}: proto3 enums always carry a
     * zero value ({@code MUSCLE_GROUP_UNSPECIFIED}), so "omitted" can't be a {@code @NotNull} on
     * the validation record — it's checked directly instead.
     */
    private void requireMuscleGroup(MuscleGroup muscleGroup) {
        if (muscleGroup == MuscleGroup.MUSCLE_GROUP_UNSPECIFIED) {
            throw new ConstraintViolationException("muscleGroup: must be set", Set.of());
        }
    }

    private record ExerciseValidation(
            @NotBlank String name,
            @Pattern(regexp = "^$|^https?://\\S+$", message = "must be a valid http(s) URL") String videoUrl) {
    }
}
