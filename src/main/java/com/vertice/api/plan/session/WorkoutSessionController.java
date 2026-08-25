package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsResponse;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsResponse;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.SetLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
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

    @Override
    public void getOrStartWorkoutLog(GetOrStartWorkoutLogRequest request, StreamObserver<WorkoutLogResponse> responseObserver) {
        validator.validate(new GetOrStartValidation(request.getWorkoutId(), request.getClientId(), request.getWeekStartDate()));
        responseObserver.onNext(workoutSessionService.getOrStartWorkoutLog(request));
        responseObserver.onCompleted();
    }

    @Override
    public void recordSetLog(RecordSetLogRequest request, StreamObserver<SetLogResponse> responseObserver) {
        validator.validate(new RecordSetLogValidation(request.getWorkoutLogId(), request.getExerciseSetId(), request.getReps()));
        responseObserver.onNext(workoutSessionService.recordSetLog(request));
        responseObserver.onCompleted();
    }

    @Override
    public void completeWorkoutLog(CompleteWorkoutLogRequest request, StreamObserver<WorkoutLogResponse> responseObserver) {
        responseObserver.onNext(workoutSessionService.completeWorkoutLog(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void listWorkoutLogs(ListWorkoutLogsRequest request, StreamObserver<ListWorkoutLogsResponse> responseObserver) {
        validator.validate(new ListWorkoutLogsValidation(request.getClientId(), request.getTrainingPlanId(), request.getWeekStartDate()));
        responseObserver.onNext(ListWorkoutLogsResponse.newBuilder()
                .addAllWorkoutLogs(workoutSessionService.listWorkoutLogs(
                        request.getClientId(), request.getTrainingPlanId(), request.getWeekStartDate()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLastSetLogs(GetLastSetLogsRequest request, StreamObserver<GetLastSetLogsResponse> responseObserver) {
        validator.validate(new GetLastSetLogsValidation(request.getClientId(), request.getWorkoutId()));
        responseObserver.onNext(GetLastSetLogsResponse.newBuilder()
                .addAllSetLogs(workoutSessionService.getLastSetLogs(request.getClientId(), request.getWorkoutId()))
                .build());
        responseObserver.onCompleted();
    }

    private record GetOrStartValidation(@Min(1) long workoutId, @Min(1) long clientId, @NotBlank String weekStartDate) {
    }

    private record RecordSetLogValidation(@Min(1) long workoutLogId, @Min(1) long exerciseSetId, @Min(0) int reps) {
    }

    private record ListWorkoutLogsValidation(@Min(1) long clientId, @Min(1) long trainingPlanId, @NotBlank String weekStartDate) {
    }

    private record GetLastSetLogsValidation(@Min(1) long clientId, @Min(1) long workoutId) {
    }
}
