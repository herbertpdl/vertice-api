package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressRequest;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressResponse;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsResponse;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsResponse;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.SetLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.CallerIdentityResolver;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class WorkoutSessionController extends WorkoutSessionServiceGrpc.WorkoutSessionServiceImplBase {

    private final WorkoutSessionService workoutSessionService;
    private final GrpcRequestValidator validator;
    private final CallerIdentityResolver callerIdentityResolver;
    private final WorkoutSessionAuthorization authorization;

    @Override
    public void getOrStartWorkoutLog(GetOrStartWorkoutLogRequest request, StreamObserver<WorkoutLogResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new GetOrStartValidation(request.getWorkoutId(), request.getClientId(), request.getWeekStartDate()));
        authorization.checkGetOrStartWorkoutLog(caller, request.getWorkoutId(), request.getClientId());
        responseObserver.onNext(workoutSessionService.getOrStartWorkoutLog(request));
        responseObserver.onCompleted();
    }

    @Override
    public void getWorkoutLog(GetWorkoutLogRequest request, StreamObserver<WorkoutLogResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new GetWorkoutLogValidation(request.getId()));
        authorization.checkGetWorkoutLog(caller, request.getId());
        responseObserver.onNext(workoutSessionService.getWorkoutLog(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void recordSetLog(RecordSetLogRequest request, StreamObserver<SetLogResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new RecordSetLogValidation(request.getWorkoutLogId(), request.getExerciseSetId(), request.getReps()));
        authorization.checkRecordSetLog(caller, request.getWorkoutLogId());
        responseObserver.onNext(workoutSessionService.recordSetLog(request));
        responseObserver.onCompleted();
    }

    @Override
    public void completeWorkoutLog(CompleteWorkoutLogRequest request, StreamObserver<WorkoutLogResponse> responseObserver) {
        authorization.checkCompleteWorkoutLog(callerIdentityResolver.requireUser(), request.getId());
        responseObserver.onNext(workoutSessionService.completeWorkoutLog(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void listWorkoutLogs(ListWorkoutLogsRequest request, StreamObserver<ListWorkoutLogsResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new ListWorkoutLogsValidation(request.getClientId(), request.getTrainingPlanId(), request.getWeekStartDate()));
        authorization.checkListWorkoutLogs(caller, request.getClientId(), request.getTrainingPlanId());
        responseObserver.onNext(ListWorkoutLogsResponse.newBuilder()
                .addAllWorkoutLogs(workoutSessionService.listWorkoutLogs(
                        request.getClientId(), request.getTrainingPlanId(), request.getWeekStartDate()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLastSetLogs(GetLastSetLogsRequest request, StreamObserver<GetLastSetLogsResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new GetLastSetLogsValidation(request.getClientId(), request.getWorkoutId()));
        authorization.checkGetLastSetLogs(caller, request.getClientId(), request.getWorkoutId());
        responseObserver.onNext(GetLastSetLogsResponse.newBuilder()
                .addAllSetLogs(workoutSessionService.getLastSetLogs(request.getClientId(), request.getWorkoutId()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getExerciseProgress(GetExerciseProgressRequest request, StreamObserver<GetExerciseProgressResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new GetExerciseProgressValidation(request.getClientId(), request.getExerciseId()));
        authorization.checkGetExerciseProgress(caller, request.getClientId());
        responseObserver.onNext(workoutSessionService.getExerciseProgress(request.getClientId(), request.getExerciseId()));
        responseObserver.onCompleted();
    }

    private record GetOrStartValidation(@Min(1) long workoutId, @Min(1) long clientId, @NotBlank String weekStartDate) {
    }

    private record GetWorkoutLogValidation(@Min(1) long id) {
    }

    private record GetExerciseProgressValidation(@Min(1) long clientId, @Min(1) long exerciseId) {
    }

    private record RecordSetLogValidation(@Min(1) long workoutLogId, @Min(1) long exerciseSetId, @Min(0) int reps) {
    }

    private record ListWorkoutLogsValidation(@Min(1) long clientId, @Min(1) long trainingPlanId, @NotBlank String weekStartDate) {
    }

    private record GetLastSetLogsValidation(@Min(1) long clientId, @Min(1) long workoutId) {
    }
}
