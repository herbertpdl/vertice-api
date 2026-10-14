package com.vertice.api.plan;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.DeleteTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansResponse;
import com.vertice.api.generated.grpc.plan.v1.PlanLevel;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.UpdateTrainingPlanRequest;
import com.vertice.api.user.UserRepository;
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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static com.vertice.api.plan.PlanCallers.CLIENT_ID;
import static com.vertice.api.plan.PlanCallers.OTHER_TRAINER_ID;
import static com.vertice.api.plan.PlanCallers.PARTIES;
import static com.vertice.api.plan.PlanCallers.TRAINER_ID;
import static com.vertice.api.plan.PlanCallers.assertAllowed;
import static com.vertice.api.plan.PlanCallers.assertCode;
import static com.vertice.api.plan.PlanCallers.assertDenied;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validation, error mapping and {@link TrainingPlanAuthorization} over a real gRPC call, with the
 * service and the ownership lookup mocked. {@code stub} is ADMIN, who bypasses ownership, so the
 * request-shape tests stay about the request; the authorization tests try every caller.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19096", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class TrainingPlanControllerTest {

    /** Owned by {@link PlanCallers#TRAINER_ID}, written for {@link PlanCallers#CLIENT_ID}. */
    private static final long PLAN_ID = 1L;

    @MockitoBean
    private TrainingPlanService trainingPlanService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlanAccessRepository planAccessRepository;

    private ManagedChannel channel;
    private TrainingPlanServiceGrpc.TrainingPlanServiceBlockingStub stub;
    private PlanCallers<TrainingPlanServiceGrpc.TrainingPlanServiceBlockingStub> callers;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19096").usePlaintext().build();
        callers = PlanCallers.of(TrainingPlanServiceGrpc.newBlockingStub(channel));
        PlanCallers.storeUsers(userRepository);
        stub = callers.admin();
        when(planAccessRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PARTIES));
        when(trainingPlanService.getTrainingPlan(any())).thenReturn(plan().build());
        when(trainingPlanService.createTrainingPlan(any())).thenReturn(plan().build());
        when(trainingPlanService.updateTrainingPlan(any(), any())).thenReturn(plan().build());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static TrainingPlanResponse.Builder plan() {
        return TrainingPlanResponse.newBuilder()
                .setId(1L).setName("Plan").setTrainerId(1L).setClientId(2L)
                .setStartDate("2026-01-05").setEndDate("2026-03-30").setLevel(PlanLevel.INTERMEDIATE);
    }

    private static TrainingPlanCreateRequest.Builder validCreateRequest() {
        return TrainingPlanCreateRequest.newBuilder()
                .setName("Plan").setTrainerId(1L).setClientId(2L)
                .setStartDate("2026-01-05").setEndDate("2026-03-30").setLevel(PlanLevel.INTERMEDIATE);
    }

    @Test
    void listTrainingPlans_withTrainerIdFilter_passesTrainerIdOnly() {
        when(trainingPlanService.listTrainingPlans(eq(1L), isNull())).thenReturn(java.util.List.of(plan().build()));

        ListTrainingPlansResponse response = stub.listTrainingPlans(ListTrainingPlansRequest.newBuilder().setTrainerId(1L).build());

        assertThat(response.getTrainingPlansList()).hasSize(1);
    }

    @Test
    void listTrainingPlans_withClientIdFilter_passesClientIdOnly() {
        when(trainingPlanService.listTrainingPlans(isNull(), eq(2L))).thenReturn(java.util.List.of(plan().build()));

        ListTrainingPlansResponse response = stub.listTrainingPlans(ListTrainingPlansRequest.newBuilder().setClientId(2L).build());

        assertThat(response.getTrainingPlansList()).hasSize(1);
    }

    @Test
    void listTrainingPlans_withNoFilters_passesBothNull() {
        when(trainingPlanService.listTrainingPlans(isNull(), isNull())).thenReturn(java.util.List.of());

        ListTrainingPlansResponse response = stub.listTrainingPlans(ListTrainingPlansRequest.newBuilder().build());

        assertThat(response.getTrainingPlansList()).isEmpty();
    }

    @Test
    void getTrainingPlan_whenExists_returnsPlan() {
        TrainingPlanResponse plan = plan().build();
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
        TrainingPlanResponse created = plan().build();
        when(trainingPlanService.createTrainingPlan(any())).thenReturn(created);

        TrainingPlanResponse response = stub.createTrainingPlan(validCreateRequest().build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createTrainingPlan_withBlankName_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createTrainingPlan(validCreateRequest().setName("").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createTrainingPlan_withMissingClientId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createTrainingPlan(validCreateRequest().setClientId(0).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createTrainingPlan_withBlankStartDate_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createTrainingPlan(validCreateRequest().setStartDate("").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createTrainingPlan_withUnsetLevel_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createTrainingPlan(validCreateRequest().setLevel(PlanLevel.PLAN_LEVEL_UNSPECIFIED).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createTrainingPlan_withMissingClient_throwsNotFound() {
        when(trainingPlanService.createTrainingPlan(any())).thenThrow(new ResourceNotFoundException("Client", 99L));

        assertThatThrownBy(() -> stub.createTrainingPlan(validCreateRequest().setClientId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateTrainingPlan_whenExists_returnsUpdated() {
        TrainingPlanResponse updated = plan().setName("New Name").build();
        when(trainingPlanService.updateTrainingPlan(eq(1L), any())).thenReturn(updated);

        TrainingPlanResponse response = stub.updateTrainingPlan(UpdateTrainingPlanRequest.newBuilder()
                .setId(1L)
                .setTrainingPlan(TrainingPlanRequest.newBuilder()
                        .setName("New Name").setClientId(2L)
                        .setStartDate("2026-01-05").setEndDate("2026-03-30").setLevel(PlanLevel.INTERMEDIATE))
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateTrainingPlan_whenMissing_throwsNotFound() {
        when(trainingPlanService.updateTrainingPlan(eq(99L), any())).thenThrow(new ResourceNotFoundException("TrainingPlan", 99L));

        assertThatThrownBy(() -> stub.updateTrainingPlan(UpdateTrainingPlanRequest.newBuilder()
                .setId(99L)
                .setTrainingPlan(TrainingPlanRequest.newBuilder()
                        .setName("Name").setClientId(2L)
                        .setStartDate("2026-01-05").setEndDate("2026-03-30").setLevel(PlanLevel.INTERMEDIATE))
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

    // Authorization: owner, another trainer, the plan's client, another client, ADMIN.

    @Test
    void listTrainingPlans_trainerIsScopedToThemself_andCannotNameAnotherTrainer() {
        callers.trainer().listTrainingPlans(ListTrainingPlansRequest.newBuilder().setClientId(CLIENT_ID).build());
        verify(trainingPlanService).listTrainingPlans(TRAINER_ID, CLIENT_ID);

        assertDenied(() -> callers.trainer().listTrainingPlans(ListTrainingPlansRequest.newBuilder()
                .setTrainerId(OTHER_TRAINER_ID).build()));
    }

    @Test
    void listTrainingPlans_clientIsScopedToThemself_andCannotNameAnotherClient() {
        callers.client().listTrainingPlans(ListTrainingPlansRequest.getDefaultInstance());
        verify(trainingPlanService).listTrainingPlans(null, CLIENT_ID);

        assertDenied(() -> callers.otherClient().listTrainingPlans(ListTrainingPlansRequest.newBuilder()
                .setClientId(CLIENT_ID).build()));
    }

    @Test
    void getTrainingPlan_ownerClientAndAdmin_areAllowed_othersDenied() {
        GetTrainingPlanRequest request = GetTrainingPlanRequest.newBuilder().setId(PLAN_ID).build();

        assertAllowed(() -> callers.trainer().getTrainingPlan(request));
        assertAllowed(() -> callers.client().getTrainingPlan(request));
        assertAllowed(() -> callers.admin().getTrainingPlan(request));
        assertDenied(() -> callers.otherTrainer().getTrainingPlan(request));
        assertDenied(() -> callers.otherClient().getTrainingPlan(request));
    }

    /** A missing id answers a non-admin exactly like someone else's: no existence oracle. */
    @Test
    void getTrainingPlan_missingId_isPermissionDeniedForATrainer_butNotFoundForAdmin() {
        when(trainingPlanService.getTrainingPlan(99L)).thenThrow(new ResourceNotFoundException("TrainingPlan", 99L));
        GetTrainingPlanRequest request = GetTrainingPlanRequest.newBuilder().setId(99L).build();

        assertDenied(() -> callers.trainer().getTrainingPlan(request));
        assertCode(() -> callers.admin().getTrainingPlan(request), Status.Code.NOT_FOUND);
    }

    @Test
    void createTrainingPlan_trainerCreatesAsThemself_clientsAreDenied() {
        callers.trainer().createTrainingPlan(validCreateRequest().setTrainerId(0).build());
        verify(trainingPlanService).createTrainingPlan(validCreateRequest().setTrainerId(TRAINER_ID).build());

        assertDenied(() -> callers.trainer().createTrainingPlan(validCreateRequest().setTrainerId(OTHER_TRAINER_ID).build()));
        assertDenied(() -> callers.client().createTrainingPlan(validCreateRequest().setTrainerId(TRAINER_ID).build()));
        assertDenied(() -> callers.otherClient().createTrainingPlan(validCreateRequest().build()));
        assertAllowed(() -> callers.otherTrainer().createTrainingPlan(validCreateRequest().setTrainerId(OTHER_TRAINER_ID).build()));
        assertAllowed(() -> callers.admin().createTrainingPlan(validCreateRequest().setTrainerId(TRAINER_ID).build()));
    }

    @Test
    void updateTrainingPlan_ownerAndAdmin_areAllowed_othersDenied() {
        UpdateTrainingPlanRequest request = UpdateTrainingPlanRequest.newBuilder()
                .setId(PLAN_ID)
                .setTrainingPlan(TrainingPlanRequest.newBuilder()
                        .setName("Name").setClientId(CLIENT_ID)
                        .setStartDate("2026-01-05").setEndDate("2026-03-30").setLevel(PlanLevel.INTERMEDIATE))
                .build();

        assertAllowed(() -> callers.trainer().updateTrainingPlan(request));
        assertAllowed(() -> callers.admin().updateTrainingPlan(request));
        assertDenied(() -> callers.otherTrainer().updateTrainingPlan(request));
        assertDenied(() -> callers.client().updateTrainingPlan(request));
        assertDenied(() -> callers.otherClient().updateTrainingPlan(request));
    }

    @Test
    void deleteTrainingPlan_ownerAndAdmin_areAllowed_othersDenied() {
        DeleteTrainingPlanRequest request = DeleteTrainingPlanRequest.newBuilder().setId(PLAN_ID).build();

        assertDenied(() -> callers.otherTrainer().deleteTrainingPlan(request));
        assertDenied(() -> callers.client().deleteTrainingPlan(request));
        assertDenied(() -> callers.otherClient().deleteTrainingPlan(request));
        verify(trainingPlanService, never()).deleteTrainingPlan(any());

        assertAllowed(() -> callers.trainer().deleteTrainingPlan(request));
        assertAllowed(() -> callers.admin().deleteTrainingPlan(request));
    }

    @Test
    void serviceTokenIsDenied_andNoTokenIsUnauthenticated() {
        GetTrainingPlanRequest request = GetTrainingPlanRequest.newBuilder().setId(PLAN_ID).build();

        assertDenied(() -> callers.bff().getTrainingPlan(request));
        assertCode(() -> callers.anonymous().getTrainingPlan(request), Status.Code.UNAUTHENTICATED);
        assertCode(() -> callers.anonymous().listTrainingPlans(ListTrainingPlansRequest.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED);
    }
}
