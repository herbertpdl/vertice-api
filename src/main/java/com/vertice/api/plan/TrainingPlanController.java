package com.vertice.api.plan;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.DeleteTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansResponse;
import com.vertice.api.generated.grpc.plan.v1.PlanLevel;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.UpdateTrainingPlanRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

@GrpcService
@RequiredArgsConstructor
public class TrainingPlanController extends TrainingPlanServiceGrpc.TrainingPlanServiceImplBase {

    private final TrainingPlanService trainingPlanService;
    private final GrpcRequestValidator validator;

    @Override
    public void listTrainingPlans(ListTrainingPlansRequest request, StreamObserver<ListTrainingPlansResponse> responseObserver) {
        Long trainerId = request.getTrainerId() == 0 ? null : request.getTrainerId();
        Long clientId = request.getClientId() == 0 ? null : request.getClientId();
        responseObserver.onNext(ListTrainingPlansResponse.newBuilder()
                .addAllTrainingPlans(trainingPlanService.listTrainingPlans(trainerId, clientId))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTrainingPlan(GetTrainingPlanRequest request, StreamObserver<TrainingPlanResponse> responseObserver) {
        responseObserver.onNext(trainingPlanService.getTrainingPlan(request.getId()));
        responseObserver.onCompleted();
    }

    @Override
    public void createTrainingPlan(TrainingPlanCreateRequest request, StreamObserver<TrainingPlanResponse> responseObserver) {
        validator.validate(new PlanValidation(request.getName(), request.getClientId(), request.getStartDate(), request.getEndDate()));
        requireLevel(request.getLevel());
        responseObserver.onNext(trainingPlanService.createTrainingPlan(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateTrainingPlan(UpdateTrainingPlanRequest request, StreamObserver<TrainingPlanResponse> responseObserver) {
        var trainingPlan = request.getTrainingPlan();
        validator.validate(new PlanValidation(trainingPlan.getName(), trainingPlan.getClientId(), trainingPlan.getStartDate(), trainingPlan.getEndDate()));
        requireLevel(trainingPlan.getLevel());
        responseObserver.onNext(trainingPlanService.updateTrainingPlan(request.getId(), trainingPlan));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteTrainingPlan(DeleteTrainingPlanRequest request, StreamObserver<Empty> responseObserver) {
        trainingPlanService.deleteTrainingPlan(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * Same reasoning as {@code UserController#requireRole}: proto3 enums always carry a zero
     * value ({@code PLAN_LEVEL_UNSPECIFIED}), so "omitted" can't be a {@code @NotNull} on the
     * validation record — it's checked directly instead.
     */
    private void requireLevel(PlanLevel level) {
        if (level == PlanLevel.PLAN_LEVEL_UNSPECIFIED) {
            throw new ConstraintViolationException("level: must be set", Set.of());
        }
    }

    private record PlanValidation(
            @NotBlank String name,
            @Min(1) long clientId,
            @NotBlank String startDate,
            @NotBlank String endDate) {
    }
}
