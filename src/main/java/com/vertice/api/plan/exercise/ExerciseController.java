package com.vertice.api.plan.exercise;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.exercise.v1.DeleteExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseServiceGrpc;
import com.vertice.api.generated.grpc.exercise.v1.GetExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesResponse;
import com.vertice.api.generated.grpc.exercise.v1.ListMuscleGroupsRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListMuscleGroupsResponse;
import com.vertice.api.generated.grpc.exercise.v1.UpdateExerciseRequest;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.CallerIdentityResolver;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

@GrpcService
@RequiredArgsConstructor
public class ExerciseController extends ExerciseServiceGrpc.ExerciseServiceImplBase {

    private final ExerciseService exerciseService;
    private final GrpcRequestValidator validator;
    private final CallerIdentityResolver callerIdentityResolver;

    @Override
    public void listMuscleGroups(ListMuscleGroupsRequest request, StreamObserver<ListMuscleGroupsResponse> responseObserver) {
        responseObserver.onNext(ListMuscleGroupsResponse.newBuilder()
                .addAllMuscleGroups(exerciseService.listMuscleGroups())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listExercises(ListExercisesRequest request, StreamObserver<ListExercisesResponse> responseObserver) {
        responseObserver.onNext(ListExercisesResponse.newBuilder()
                .addAllExercises(exerciseService.listExercises(callerIdentityResolver.require()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getExercise(GetExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        responseObserver.onNext(exerciseService.getExercise(callerIdentityResolver.require(), request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createExercise(ExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.require();
        validate(request);
        responseObserver.onNext(exerciseService.createExercise(caller, request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateExercise(UpdateExerciseRequest request, StreamObserver<ExerciseResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.require();
        validate(request.getExercise());
        responseObserver.onNext(exerciseService.updateExercise(caller, request.getId(), request.getExercise()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteExercise(DeleteExerciseRequest request, StreamObserver<Empty> responseObserver) {
        exerciseService.deleteExercise(callerIdentityResolver.require(), request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * An empty {@code muscle_group_ids} is checked directly rather than with {@code @NotEmpty} so
     * the description carries the pinned wording. Duplicates are allowed; the service collapses
     * them, so "empty after de-duplication" is the same as "empty".
     */
    private void validate(ExerciseRequest request) {
        validator.validate(new ExerciseValidation(request.getName(), request.getDescription(), request.getVideoUrl()));
        if (request.getMuscleGroupIdsCount() == 0) {
            throw new ConstraintViolationException("muscleGroupIds: must contain at least one muscle group", Set.of());
        }
    }

    private record ExerciseValidation(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String description,
            @Pattern(regexp = "^$|^https?://\\S+$", message = "must be a valid http(s) URL") @Size(max = 500) String videoUrl) {
    }
}
