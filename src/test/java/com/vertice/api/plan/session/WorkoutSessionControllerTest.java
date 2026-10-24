package com.vertice.api.plan.session;

import com.vertice.api.common.exception.BusinessRuleViolationException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressRequest;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressResponse;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsResponse;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsResponse;
import com.vertice.api.generated.grpc.session.v1.ProgressPoint;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.SetLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.plan.PlanAccessRepository;
import com.vertice.api.plan.PlanCallers;
import com.vertice.api.trainerclient.TrainerClientRepository;
import com.vertice.api.user.Role;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.vertice.api.grpc.GrpcAssertions.assertInvalidField;
import static com.vertice.api.plan.PlanCallers.CLIENT_ID;
import static com.vertice.api.plan.PlanCallers.OTHER_CLIENT_ID;
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
@SpringBootTest(properties = {"spring.grpc.server.port=19100", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutSessionControllerTest {

    /** {@link PlanCallers#PARTIES}' plan, a workout in it, and its client's log of that workout. */
    private static final long PLAN_ID = 4L;
    private static final long WORKOUT_ID = 5L;
    private static final long WORKOUT_LOG_ID = 8L;

    @MockitoBean
    private WorkoutSessionService workoutSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlanAccessRepository planAccessRepository;

    @MockitoBean
    private TrainerClientRepository trainerClientRepository;

    private ManagedChannel channel;
    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub stub;
    private PlanCallers<WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub> callers;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19100").usePlaintext().build();
        callers = PlanCallers.of(WorkoutSessionServiceGrpc.newBlockingStub(channel));
        PlanCallers.storeUsers(userRepository);
        stub = callers.admin();
        when(planAccessRepository.findByWorkoutLogId(WORKOUT_LOG_ID)).thenReturn(Optional.of(PARTIES));
        when(planAccessRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PARTIES));
        when(planAccessRepository.findByWorkoutId(WORKOUT_ID)).thenReturn(Optional.of(PARTIES));
        when(trainerClientRepository.existsByTrainerIdAndClientIdAndClientRoleAndEndedAtIsNull(TRAINER_ID, CLIENT_ID, Role.CLIENT))
                .thenReturn(true);
        when(workoutSessionService.getOrStartWorkoutLog(any())).thenReturn(WorkoutLogResponse.getDefaultInstance());
        when(workoutSessionService.getWorkoutLog(any())).thenReturn(WorkoutLogResponse.getDefaultInstance());
        when(workoutSessionService.completeWorkoutLog(any())).thenReturn(WorkoutLogResponse.getDefaultInstance());
        when(workoutSessionService.recordSetLog(any())).thenReturn(SetLogResponse.getDefaultInstance());
        when(workoutSessionService.getExerciseProgress(any(), any())).thenReturn(GetExerciseProgressResponse.getDefaultInstance());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void getOrStartWorkoutLog_withValidRequest_returnsLog() {
        WorkoutLogResponse log = WorkoutLogResponse.newBuilder().setId(1L).setWorkoutId(1L).setClientId(2L).setWeekStartDate("2026-08-24").build();
        when(workoutSessionService.getOrStartWorkoutLog(any())).thenReturn(log);

        WorkoutLogResponse response = stub.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(1L).setClientId(2L).setWeekStartDate("2026-08-24").build());

        assertThat(response).isEqualTo(log);
    }

    @Test
    void getOrStartWorkoutLog_withBlankWeekStartDate_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(1L).setClientId(2L).setWeekStartDate("").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getOrStartWorkoutLog_withZeroWorkoutId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(0).setClientId(2L).setWeekStartDate("2026-08-24").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getOrStartWorkoutLog_withMissingWorkout_throwsNotFound() {
        when(workoutSessionService.getOrStartWorkoutLog(any())).thenThrow(new ResourceNotFoundException("Workout", 99L));

        assertThatThrownBy(() -> stub.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(99L).setClientId(2L).setWeekStartDate("2026-08-24").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void getWorkoutLog_whenExists_returnsLog() {
        WorkoutLogResponse log = WorkoutLogResponse.newBuilder().setId(1L).setWorkoutId(3L).setClientId(2L).setWeekStartDate("2026-08-24").build();
        when(workoutSessionService.getWorkoutLog(1L)).thenReturn(log);

        WorkoutLogResponse response = stub.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(log);
    }

    @Test
    void getWorkoutLog_whenMissing_throwsNotFound() {
        when(workoutSessionService.getWorkoutLog(99L)).thenThrow(new ResourceNotFoundException("WorkoutLog", 99L));

        assertThatThrownBy(() -> stub.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void getWorkoutLog_withZeroId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(0).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void recordSetLog_withSetFromAnotherWorkout_throwsFailedPrecondition() {
        when(workoutSessionService.recordSetLog(any()))
                .thenThrow(new BusinessRuleViolationException("ExerciseSet 5 does not belong to the workout of WorkoutLog 1"));

        assertThatThrownBy(() -> stub.recordSetLog(RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(1L).setExerciseSetId(5L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(ex.getStatus().getDescription()).contains("ExerciseSet 5");
                });
    }

    @Test
    void recordSetLog_withValidRequest_returnsSetLog() {
        SetLogResponse setLog = SetLogResponse.newBuilder().setId(1L).setWorkoutLogId(1L).setExerciseSetId(5L).setWeight("60").build();
        when(workoutSessionService.recordSetLog(any())).thenReturn(setLog);

        SetLogResponse response = stub.recordSetLog(RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(1L).setExerciseSetId(5L).setWeight("60").setReps(10).build());

        assertThat(response).isEqualTo(setLog);
    }

    @Test
    void recordSetLog_withNegativeReps_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.recordSetLog(RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(1L).setExerciseSetId(5L).setReps(-1).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void recordSetLog_whenSessionAlreadyCompleted_throwsInvalidArgument() {
        when(workoutSessionService.recordSetLog(any()))
                .thenThrow(new jakarta.validation.ConstraintViolationException("session already completed", java.util.Set.of()));

        assertThatThrownBy(() -> stub.recordSetLog(RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(1L).setExerciseSetId(5L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void completeWorkoutLog_whenExists_returnsCompletedLog() {
        WorkoutLogResponse completed = WorkoutLogResponse.newBuilder().setId(1L).setCompletedAt("2026-08-24T10:00:00Z").build();
        when(workoutSessionService.completeWorkoutLog(1L)).thenReturn(completed);

        WorkoutLogResponse response = stub.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(1L).build());

        assertThat(response.getCompletedAt()).isEqualTo("2026-08-24T10:00:00Z");
    }

    @Test
    void completeWorkoutLog_whenMissing_throwsNotFound() {
        when(workoutSessionService.completeWorkoutLog(99L)).thenThrow(new ResourceNotFoundException("WorkoutLog", 99L));

        assertThatThrownBy(() -> stub.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void listWorkoutLogs_returnsLogs() {
        WorkoutLogResponse log = WorkoutLogResponse.newBuilder().setId(1L).build();
        when(workoutSessionService.listWorkoutLogs(2L, 3L, "2026-08-24")).thenReturn(List.of(log));

        ListWorkoutLogsResponse response = stub.listWorkoutLogs(ListWorkoutLogsRequest.newBuilder()
                .setClientId(2L).setTrainingPlanId(3L).setWeekStartDate("2026-08-24").build());

        assertThat(response.getWorkoutLogsList()).containsExactly(log);
    }

    @Test
    void listWorkoutLogs_withZeroClientId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.listWorkoutLogs(ListWorkoutLogsRequest.newBuilder()
                .setClientId(0).setTrainingPlanId(3L).setWeekStartDate("2026-08-24").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getLastSetLogs_returnsSetLogs() {
        SetLogResponse setLog = SetLogResponse.newBuilder().setId(1L).build();
        when(workoutSessionService.getLastSetLogs(2L, 1L)).thenReturn(List.of(setLog));

        GetLastSetLogsResponse response = stub.getLastSetLogs(GetLastSetLogsRequest.newBuilder()
                .setClientId(2L).setWorkoutId(1L).build());

        assertThat(response.getSetLogsList()).containsExactly(setLog);
    }

    @Test
    void getLastSetLogs_returnsEmptyWhenNoHistory() {
        when(workoutSessionService.getLastSetLogs(2L, 1L)).thenReturn(List.of());

        GetLastSetLogsResponse response = stub.getLastSetLogs(GetLastSetLogsRequest.newBuilder()
                .setClientId(2L).setWorkoutId(1L).build());

        assertThat(response.getSetLogsList()).isEmpty();
    }

    @Test
    void getExerciseProgress_returnsPoints() {
        GetExerciseProgressResponse progress = GetExerciseProgressResponse.newBuilder()
                .addPoints(ProgressPoint.newBuilder().setWeekStartDate("2026-08-03").setWeight("60").build())
                .build();
        when(workoutSessionService.getExerciseProgress(2L, 10L)).thenReturn(progress);

        GetExerciseProgressResponse response = stub.getExerciseProgress(GetExerciseProgressRequest.newBuilder()
                .setClientId(2L).setExerciseId(10L).build());

        assertThat(response).isEqualTo(progress);
    }

    @Test
    void getExerciseProgress_withZeroExerciseId_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.getExerciseProgress(GetExerciseProgressRequest.newBuilder()
                .setClientId(2L).setExerciseId(0).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // Authorization: the plan's trainer, another trainer, the plan's (and log's) client, another client, ADMIN.

    @Test
    void getOrStartWorkoutLog_onlyThePlansClientAndAdmin() {
        callers.assertClientRule(c -> c.getOrStartWorkoutLog(getOrStart(CLIENT_ID)));
    }

    @Test
    void getOrStartWorkoutLog_clientNamingAnotherClient_isPermissionDenied() {
        assertDenied(() -> callers.otherClient().getOrStartWorkoutLog(getOrStart(CLIENT_ID)));
        assertDenied(() -> callers.client().getOrStartWorkoutLog(getOrStart(OTHER_CLIENT_ID)));
        assertDenied(() -> callers.otherClient().getOrStartWorkoutLog(getOrStart(OTHER_CLIENT_ID)));
    }

    @Test
    void getWorkoutLog_followsThePlanReadRule() {
        callers.assertReadRule(c -> c.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(WORKOUT_LOG_ID).build()));
    }

    @Test
    void recordSetLog_onlyTheLogsClientAndAdmin() {
        callers.assertClientRule(c -> c.recordSetLog(RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(WORKOUT_LOG_ID).setExerciseSetId(9L).setReps(5).build()));
    }

    @Test
    void completeWorkoutLog_onlyTheLogsClientAndAdmin() {
        callers.assertClientRule(c -> c.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(WORKOUT_LOG_ID).build()));
    }

    @Test
    void listWorkoutLogs_followsThePlanReadRule() {
        callers.assertReadRule(c -> c.listWorkoutLogs(ListWorkoutLogsRequest.newBuilder()
                .setClientId(CLIENT_ID).setTrainingPlanId(PLAN_ID).setWeekStartDate("2026-08-24").build()));
    }

    @Test
    void getLastSetLogs_followsThePlanReadRule() {
        callers.assertReadRule(c -> c.getLastSetLogs(GetLastSetLogsRequest.newBuilder()
                .setClientId(CLIENT_ID).setWorkoutId(WORKOUT_ID).build()));
    }

    /** Not a plan-tree rule: the client, their active trainer, ADMIN. */
    @Test
    void getExerciseProgress_clientActiveTrainerAndAdmin_areAllowed_othersDenied() {
        GetExerciseProgressRequest request = GetExerciseProgressRequest.newBuilder().setClientId(CLIENT_ID).setExerciseId(10L).build();

        assertAllowed(() -> callers.client().getExerciseProgress(request));
        assertAllowed(() -> callers.trainer().getExerciseProgress(request));
        assertAllowed(() -> callers.admin().getExerciseProgress(request));
        assertDenied(() -> callers.otherTrainer().getExerciseProgress(request));
        assertDenied(() -> callers.otherClient().getExerciseProgress(request));
    }

    /** A missing id answers a non-admin exactly like someone else's: no existence oracle. */
    @Test
    void completeWorkoutLog_missingLog_isPermissionDeniedForAClient() {
        assertDenied(() -> callers.client().completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(99L).build()));
    }

    @Test
    void serviceTokenIsDenied_andNoTokenIsUnauthenticated() {
        callers.assertNoServiceOrAnonymous(c -> c.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(WORKOUT_LOG_ID).build()));
        callers.assertNoServiceOrAnonymous(c -> c.getOrStartWorkoutLog(getOrStart(CLIENT_ID)));
    }

    private static GetOrStartWorkoutLogRequest getOrStart(long clientId) {
        return GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(WORKOUT_ID).setClientId(clientId).setWeekStartDate("2026-08-24").build();
    }

    // Upper bound (P1-09): logged reps follow WorkoutLimits.MAX_REPS; the weight's precision is
    // checked by ProtoDecimals in the service (ProtoDecimalsTest).

    @Test
    void recordSetLog_withRepsAtTheBound_isAccepted_andPastIt_throwsInvalidArgument() {
        stub.recordSetLog(RecordSetLogRequest.newBuilder().setWorkoutLogId(1L).setExerciseSetId(5L).setReps(1_000).build());

        assertInvalidField(() -> stub.recordSetLog(RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(1L).setExerciseSetId(5L).setReps(1_001).build()), "reps");
    }
}
