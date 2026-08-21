package com.vertice.api.plan;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.DeleteTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.UpdateTrainingPlanRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.grpc.server.port=19096")
@ActiveProfiles("local")
class TrainingPlanControllerTest {

    @MockitoBean
    private TrainingPlanService trainingPlanService;

    private ManagedChannel channel;
    private TrainingPlanServiceGrpc.TrainingPlanServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19096").usePlaintext().build();
        stub = TrainingPlanServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listTrainingPlans_returnsPlansForTrainer() {
        TrainingPlanResponse plan = TrainingPlanResponse.newBuilder().setId(1L).setName("Plan").setTrainerId(1L).build();
        when(trainingPlanService.listTrainingPlans(1L)).thenReturn(java.util.List.of(plan));

        ListTrainingPlansResponse response = stub.listTrainingPlans(ListTrainingPlansRequest.newBuilder().setTrainerId(1L).build());

        assertThat(response.getTrainingPlansList()).containsExactly(plan);
    }

    @Test
    void getTrainingPlan_whenExists_returnsPlan() {
        TrainingPlanResponse plan = TrainingPlanResponse.newBuilder().setId(1L).setName("Plan").setTrainerId(1L).build();
        when(trainingPlanService.getTrainingPlan(1L)).thenReturn(plan);

        TrainingPlanResponse response = stub.getTrainingPlan(GetTrainingPlanRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(plan);
    }

    @Test
    void getTrainingPlan_whenMissing_throwsNotFound() {
        when(trainingPlanService.getTrainingPlan(99L)).thenThrow(new ResourceNotFoundException("TrainingPlan", 99L));

        assertThatThrownBy(() -> stub.getTrainingPlan(GetTrainingPlanRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createTrainingPlan_withValidRequest_returnsCreated() {
        TrainingPlanResponse created = TrainingPlanResponse.newBuilder().setId(1L).setName("Plan").setTrainerId(1L).build();
        when(trainingPlanService.createTrainingPlan(any())).thenReturn(created);

        TrainingPlanResponse response = stub.createTrainingPlan(TrainingPlanCreateRequest.newBuilder()
                .setName("Plan").setTrainerId(1L).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createTrainingPlan_withBlankName_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createTrainingPlan(TrainingPlanCreateRequest.newBuilder()
                .setName("").setTrainerId(1L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createTrainingPlan_withMissingTrainer_throwsNotFound() {
        when(trainingPlanService.createTrainingPlan(any())).thenThrow(new ResourceNotFoundException("Trainer", 99L));

        assertThatThrownBy(() -> stub.createTrainingPlan(TrainingPlanCreateRequest.newBuilder()
                .setName("Plan").setTrainerId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateTrainingPlan_whenExists_returnsUpdated() {
        TrainingPlanResponse updated = TrainingPlanResponse.newBuilder().setId(1L).setName("New Name").setTrainerId(1L).build();
        when(trainingPlanService.updateTrainingPlan(eq(1L), any())).thenReturn(updated);

        TrainingPlanResponse response = stub.updateTrainingPlan(UpdateTrainingPlanRequest.newBuilder()
                .setId(1L)
                .setTrainingPlan(TrainingPlanRequest.newBuilder().setName("New Name").build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateTrainingPlan_whenMissing_throwsNotFound() {
        when(trainingPlanService.updateTrainingPlan(eq(99L), any())).thenThrow(new ResourceNotFoundException("TrainingPlan", 99L));

        assertThatThrownBy(() -> stub.updateTrainingPlan(UpdateTrainingPlanRequest.newBuilder()
                .setId(99L)
                .setTrainingPlan(TrainingPlanRequest.newBuilder().setName("Name").build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteTrainingPlan_whenExists_succeeds() {
        Empty response = stub.deleteTrainingPlan(DeleteTrainingPlanRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteTrainingPlan_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("TrainingPlan", 99L)).when(trainingPlanService).deleteTrainingPlan(99L);

        assertThatThrownBy(() -> stub.deleteTrainingPlan(DeleteTrainingPlanRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }
}
