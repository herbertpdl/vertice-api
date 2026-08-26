package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressRequest;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressResponse;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsResponse;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsResponse;
import com.vertice.api.generated.grpc.session.v1.ProgressPoint;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.SetLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19100", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutSessionControllerTest {

    @MockitoBean
    private WorkoutSessionService workoutSessionService;

    private ManagedChannel channel;
    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19100").usePlaintext().build();
        stub = WorkoutSessionServiceGrpc.newBlockingStub(channel);
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
}
