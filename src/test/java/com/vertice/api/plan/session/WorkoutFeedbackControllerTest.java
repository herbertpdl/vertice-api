package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackResponse;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
import com.vertice.api.plan.PlanAccessRepository;
import com.vertice.api.plan.PlanCallers;
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

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.vertice.api.plan.PlanCallers.OTHER_TRAINER_ID;
import static com.vertice.api.plan.PlanCallers.PARTIES;
import static com.vertice.api.plan.PlanCallers.TRAINER_ID;
import static com.vertice.api.plan.PlanCallers.assertAllowed;
import static com.vertice.api.plan.PlanCallers.assertDenied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Validation, error mapping and {@link WorkoutSessionAuthorization} over a real gRPC call, with the
 * service and the ownership lookups mocked. {@code stub} is ADMIN, who bypasses ownership, so the
 * request-shape tests stay about the request; the authorization tests try every caller.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19101", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutFeedbackControllerTest {

    /** A completed log of {@link PlanCallers#CLIENT_ID} in {@link PlanCallers#TRAINER_ID}'s plan. */
    private static final long WORKOUT_LOG_ID = 8L;

    @MockitoBean
    private WorkoutFeedbackService workoutFeedbackService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlanAccessRepository planAccessRepository;

    private ManagedChannel channel;
    private WorkoutFeedbackServiceGrpc.WorkoutFeedbackServiceBlockingStub stub;
    private PlanCallers<WorkoutFeedbackServiceGrpc.WorkoutFeedbackServiceBlockingStub> callers;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19101").usePlaintext().build();
        callers = PlanCallers.of(WorkoutFeedbackServiceGrpc.newBlockingStub(channel));
        PlanCallers.storeUsers(userRepository);
        stub = callers.admin();
        when(planAccessRepository.findByWorkoutLogId(WORKOUT_LOG_ID)).thenReturn(Optional.of(PARTIES));
        when(workoutFeedbackService.submitWorkoutFeedback(any())).thenReturn(WorkoutFeedbackResponse.getDefaultInstance());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void submitWorkoutFeedback_withValidRequest_returnsFeedback() {
        WorkoutFeedbackResponse feedback = WorkoutFeedbackResponse.newBuilder()
                .setId(1L).setWorkoutLogId(10L).setText("Great session!").build();
        when(workoutFeedbackService.submitWorkoutFeedback(any())).thenReturn(feedback);

        WorkoutFeedbackResponse response = stub.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(10L).setText("Great session!").build());

        assertThat(response).isEqualTo(feedback);
    }

    @Test
    void submitWorkoutFeedback_withBlankText_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(10L).setText("").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void submitWorkoutFeedback_withZeroWorkoutLogId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(0).setText("Feedback").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void submitWorkoutFeedback_withMissingWorkoutLog_throwsNotFound() {
        when(workoutFeedbackService.submitWorkoutFeedback(any())).thenThrow(new ResourceNotFoundException("WorkoutLog", 99L));

        assertThatThrownBy(() -> stub.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(99L).setText("Feedback").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void submitWorkoutFeedback_whenSessionNotCompleted_throwsInvalidArgument() {
        when(workoutFeedbackService.submitWorkoutFeedback(any()))
                .thenThrow(new jakarta.validation.ConstraintViolationException("workout session is not completed yet", Set.of()));

        assertThatThrownBy(() -> stub.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(10L).setText("Feedback").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void listWorkoutFeedback_returnsFeedbackForTrainer() {
        WorkoutFeedbackResponse feedback = WorkoutFeedbackResponse.newBuilder().setId(1L).setText("Feedback").build();
        when(workoutFeedbackService.listWorkoutFeedback(1L)).thenReturn(List.of(feedback));

        ListWorkoutFeedbackResponse response = stub.listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(1L).build());

        assertThat(response.getWorkoutFeedbackList()).containsExactly(feedback);
    }

    @Test
    void listWorkoutFeedback_withZeroTrainerId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(0).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // Authorization: the plan's trainer, another trainer, the log's client, another client, ADMIN.

    @Test
    void submitWorkoutFeedback_onlyTheLogsClientAndAdmin() {
        callers.assertClientRule(c -> c.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(WORKOUT_LOG_ID).setText("Great session!").build()));
    }

    @Test
    void listWorkoutFeedback_trainerOnlyForThemself_adminForAnyone_clientsNever() {
        assertAllowed(() -> callers.trainer().listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(TRAINER_ID).build()));
        assertAllowed(() -> callers.admin().listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(TRAINER_ID).build()));
        assertDenied(() -> callers.otherTrainer().listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(TRAINER_ID).build()));
        assertDenied(() -> callers.client().listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(TRAINER_ID).build()));
        assertDenied(() -> callers.otherClient().listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(OTHER_TRAINER_ID).build()));
    }

    @Test
    void serviceTokenIsDenied_andNoTokenIsUnauthenticated() {
        callers.assertNoServiceOrAnonymous(c -> c.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(WORKOUT_LOG_ID).setText("Great session!").build()));
    }
}
