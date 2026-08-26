package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsResponse;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
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

@SpringBootTest(properties = {"spring.grpc.server.port=19097", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutControllerTest {

    @MockitoBean
    private WorkoutService workoutService;

    private ManagedChannel channel;
    private WorkoutServiceGrpc.WorkoutServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19097").usePlaintext().build();
        stub = WorkoutServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listWorkouts_returnsWorkoutsForTrainingPlan() {
        WorkoutResponse workout = WorkoutResponse.newBuilder().setId(1L).setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY).build();
        when(workoutService.listWorkouts(1L)).thenReturn(java.util.List.of(workout));

        ListWorkoutsResponse response = stub.listWorkouts(ListWorkoutsRequest.newBuilder().setTrainingPlanId(1L).build());

        assertThat(response.getWorkoutsList()).containsExactly(workout);
    }

    @Test
    void getWorkout_whenExists_returnsWorkout() {
        WorkoutResponse workout = WorkoutResponse.newBuilder().setId(1L).setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY).build();
        when(workoutService.getWorkout(1L)).thenReturn(workout);

        WorkoutResponse response = stub.getWorkout(GetWorkoutRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(workout);
    }

    @Test
    void getWorkout_whenMissing_throwsNotFound() {
        when(workoutService.getWorkout(99L)).thenThrow(new ResourceNotFoundException("Workout", 99L));

        assertThatThrownBy(() -> stub.getWorkout(GetWorkoutRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createWorkout_withValidRequest_returnsCreated() {
        WorkoutResponse created = WorkoutResponse.newBuilder().setId(1L).setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY).build();
        when(workoutService.createWorkout(any())).thenReturn(created);

        WorkoutResponse response = stub.createWorkout(WorkoutCreateRequest.newBuilder()
                .setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createWorkout_withBlankName_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createWorkout(WorkoutCreateRequest.newBuilder()
                .setName("").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createWorkout_withUnsetDayOfWeek_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createWorkout(WorkoutCreateRequest.newBuilder()
                .setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.DAY_OF_WEEK_UNSPECIFIED).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createWorkout_withMissingTrainingPlan_throwsNotFound() {
        when(workoutService.createWorkout(any())).thenThrow(new ResourceNotFoundException("TrainingPlan", 99L));

        assertThatThrownBy(() -> stub.createWorkout(WorkoutCreateRequest.newBuilder()
                .setName("Day 1").setTrainingPlanId(99L).setDayOfWeek(DayOfWeek.MONDAY).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateWorkout_whenExists_returnsUpdated() {
        WorkoutResponse updated = WorkoutResponse.newBuilder().setId(1L).setName("New Name").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.FRIDAY).build();
        when(workoutService.updateWorkout(eq(1L), any())).thenReturn(updated);

        WorkoutResponse response = stub.updateWorkout(UpdateWorkoutRequest.newBuilder()
                .setId(1L)
                .setWorkout(WorkoutRequest.newBuilder().setName("New Name").setDayOfWeek(DayOfWeek.FRIDAY).build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateWorkout_withUnsetDayOfWeek_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.updateWorkout(UpdateWorkoutRequest.newBuilder()
                .setId(1L)
                .setWorkout(WorkoutRequest.newBuilder().setName("Name").setDayOfWeek(DayOfWeek.DAY_OF_WEEK_UNSPECIFIED).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void updateWorkout_whenMissing_throwsNotFound() {
        when(workoutService.updateWorkout(eq(99L), any())).thenThrow(new ResourceNotFoundException("Workout", 99L));

        assertThatThrownBy(() -> stub.updateWorkout(UpdateWorkoutRequest.newBuilder()
                .setId(99L)
                .setWorkout(WorkoutRequest.newBuilder().setName("Name").setDayOfWeek(DayOfWeek.MONDAY).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteWorkout_whenExists_succeeds() {
        Empty response = stub.deleteWorkout(DeleteWorkoutRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteWorkout_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Workout", 99L)).when(workoutService).deleteWorkout(99L);

        assertThatThrownBy(() -> stub.deleteWorkout(DeleteWorkoutRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void cloneWorkout_withValidRequest_returnsClone() {
        WorkoutResponse clone = WorkoutResponse.newBuilder().setId(2L).setName("Week 2").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY).build();
        when(workoutService.cloneWorkout(any())).thenReturn(clone);

        WorkoutResponse response = stub.cloneWorkout(CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(1L).setName("Week 2").setDayOfWeek(DayOfWeek.MONDAY).build());

        assertThat(response).isEqualTo(clone);
    }

    @Test
    void cloneWorkout_withBlankName_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.cloneWorkout(CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(1L).setName("").setDayOfWeek(DayOfWeek.MONDAY).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void cloneWorkout_withUnsetDayOfWeek_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.cloneWorkout(CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(1L).setName("Week 2")
                .setDayOfWeek(DayOfWeek.DAY_OF_WEEK_UNSPECIFIED).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void cloneWorkout_withMissingSourceWorkout_throwsNotFound() {
        when(workoutService.cloneWorkout(any())).thenThrow(new ResourceNotFoundException("Workout", 99L));

        assertThatThrownBy(() -> stub.cloneWorkout(CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(99L).setTargetTrainingPlanId(1L).setName("Week 2").setDayOfWeek(DayOfWeek.MONDAY).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void cloneWorkout_withMissingTargetPlan_throwsNotFound() {
        when(workoutService.cloneWorkout(any())).thenThrow(new ResourceNotFoundException("TrainingPlan", 99L));

        assertThatThrownBy(() -> stub.cloneWorkout(CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(99L).setName("Week 2").setDayOfWeek(DayOfWeek.MONDAY).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }
}
