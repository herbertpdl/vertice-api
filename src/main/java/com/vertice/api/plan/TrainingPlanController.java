package com.vertice.api.plan;

import com.google.protobuf.Empty;
import com.vertice.api.generated.grpc.plan.v1.DeleteTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.UpdateTrainingPlanRequest;
import com.vertice.api.grpc.GrpcRequestValidator;
import io.grpc.stub.StreamObserver;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class TrainingPlanController extends TrainingPlanServiceGrpc.TrainingPlanServiceImplBase {

    private final TrainingPlanService trainingPlanService;
    private final GrpcRequestValidator validator;

    @Override
    public void listTrainingPlans(ListTrainingPlansRequest request, StreamObserver<ListTrainingPlansResponse> responseObserver) {
        responseObserver.onNext(ListTrainingPlansResponse.newBuilder()
                .addAllTrainingPlans(trainingPlanService.listTrainingPlans(request.getTrainerId()))
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
        validator.validate(new CreateValidation(request.getName()));
        responseObserver.onNext(trainingPlanService.createTrainingPlan(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateTrainingPlan(UpdateTrainingPlanRequest request, StreamObserver<TrainingPlanResponse> responseObserver) {
        validator.validate(new CreateValidation(request.getTrainingPlan().getName()));
        responseObserver.onNext(trainingPlanService.updateTrainingPlan(request.getId(), request.getTrainingPlan()));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteTrainingPlan(DeleteTrainingPlanRequest request, StreamObserver<Empty> responseObserver) {
        trainingPlanService.deleteTrainingPlan(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private record CreateValidation(@NotBlank String name) {
    }
}
