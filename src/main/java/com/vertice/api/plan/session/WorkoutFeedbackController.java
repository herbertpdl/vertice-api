package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackResponse;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
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
public class WorkoutFeedbackController extends WorkoutFeedbackServiceGrpc.WorkoutFeedbackServiceImplBase {

    private final WorkoutFeedbackService workoutFeedbackService;
    private final GrpcRequestValidator validator;
    private final CallerIdentityResolver callerIdentityResolver;
    private final WorkoutSessionAuthorization authorization;

    @Override
    public void submitWorkoutFeedback(SubmitWorkoutFeedbackRequest request, StreamObserver<WorkoutFeedbackResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new SubmitValidation(request.getWorkoutLogId(), request.getText()));
        authorization.checkSubmitWorkoutFeedback(caller, request.getWorkoutLogId());
        responseObserver.onNext(workoutFeedbackService.submitWorkoutFeedback(request));
        responseObserver.onCompleted();
    }

    @Override
    public void listWorkoutFeedback(ListWorkoutFeedbackRequest request, StreamObserver<ListWorkoutFeedbackResponse> responseObserver) {
        CallerIdentity caller = callerIdentityResolver.requireUser();
        validator.validate(new ListValidation(request.getTrainerId()));
        authorization.checkListWorkoutFeedback(caller, request.getTrainerId());
        responseObserver.onNext(ListWorkoutFeedbackResponse.newBuilder()
                .addAllWorkoutFeedback(workoutFeedbackService.listWorkoutFeedback(request.getTrainerId()))
                .build());
        responseObserver.onCompleted();
    }

    private record SubmitValidation(@Min(1) long workoutLogId, @NotBlank String text) {
    }

    private record ListValidation(@Min(1) long trainerId) {
    }
}
