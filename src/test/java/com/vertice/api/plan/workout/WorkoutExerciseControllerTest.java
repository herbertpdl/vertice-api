package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesResponse;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.vertice.api.grpc.GrpcAssertions.assertInvalidField;
import static com.vertice.api.plan.PlanCallers.PARTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Validation, error mapping and {@link WorkoutAuthorization} over a real gRPC call, with the
 * service and the ownership lookup mocked. {@code stub} is ADMIN, who bypasses ownership, so the
 * request-shape tests stay about the request; the authorization tests try every caller.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19098", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutExerciseControllerTest {

    /** A workout and a workout exercise in {@link PlanCallers#PARTIES}' plan. */
    private static final long WORKOUT_ID = 5L;
    private static final long WORKOUT_EXERCISE_ID = 6L;

    @MockitoBean
    private WorkoutExerciseService workoutExerciseService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlanAccessRepository planAccessRepository;

    private ManagedChannel channel;
    private WorkoutExerciseServiceGrpc.WorkoutExerciseServiceBlockingStub stub;
    private PlanCallers<WorkoutExerciseServiceGrpc.WorkoutExerciseServiceBlockingStub> callers;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19098").usePlaintext().build();
        callers = PlanCallers.of(WorkoutExerciseServiceGrpc.newBlockingStub(channel));
        PlanCallers.storeUsers(userRepository);
        stub = callers.admin();
        when(planAccessRepository.findByWorkoutId(WORKOUT_ID)).thenReturn(Optional.of(PARTIES));
        when(planAccessRepository.findByWorkoutExerciseId(WORKOUT_EXERCISE_ID)).thenReturn(Optional.of(PARTIES));
        when(workoutExerciseService.getWorkoutExercise(any())).thenReturn(workoutExercise().build());
        when(workoutExerciseService.createWorkoutExercise(any())).thenReturn(workoutExercise().build());
        when(workoutExerciseService.updateWorkoutExercise(any(), any())).thenReturn(workoutExercise().build());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static WorkoutExerciseResponse.Builder workoutExercise() {
        return WorkoutExerciseResponse.newBuilder().setId(1L).setWorkoutId(1L).setExerciseId(2L).setOrder(1);
    }

    @Test
    void listWorkoutExercises_returnsExercisesForWorkout() {
        WorkoutExerciseResponse response1 = workoutExercise().build();
        when(workoutExerciseService.listWorkoutExercises(1L)).thenReturn(java.util.List.of(response1));

        ListWorkoutExercisesResponse response = stub.listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(1L).build());

        assertThat(response.getWorkoutExercisesList()).containsExactly(response1);
    }

    @Test
    void getWorkoutExercise_whenExists_returnsWorkoutExercise() {
        WorkoutExerciseResponse workoutExercise = workoutExercise().build();
        when(workoutExerciseService.getWorkoutExercise(1L)).thenReturn(workoutExercise);

        WorkoutExerciseResponse response = stub.getWorkoutExercise(GetWorkoutExerciseRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(workoutExercise);
    }

    @Test
    void getWorkoutExercise_whenMissing_throwsNotFound() {
        when(workoutExerciseService.getWorkoutExercise(99L)).thenThrow(new ResourceNotFoundException("WorkoutExercise", 99L));

        assertThatThrownBy(() -> stub.getWorkoutExercise(GetWorkoutExerciseRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createWorkoutExercise_withValidRequest_returnsCreated() {
        WorkoutExerciseResponse created = workoutExercise().build();
        when(workoutExerciseService.createWorkoutExercise(any())).thenReturn(created);

        WorkoutExerciseResponse response = stub.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(1L).setExerciseId(2L).setOrder(1).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createWorkoutExercise_withZeroOrder_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(1L).setExerciseId(2L).setOrder(0).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createWorkoutExercise_withNegativeRestSeconds_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(1L).setExerciseId(2L).setOrder(1).setRestSecondsBetweenSets(-1).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createWorkoutExercise_withMissingWorkout_throwsNotFound() {
        when(workoutExerciseService.createWorkoutExercise(any())).thenThrow(new ResourceNotFoundException("Workout", 99L));

        assertThatThrownBy(() -> stub.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(99L).setExerciseId(2L).setOrder(1).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createWorkoutExercise_withMissingExercise_throwsNotFound() {
        when(workoutExerciseService.createWorkoutExercise(any())).thenThrow(new ResourceNotFoundException("Exercise", 99L));

        assertThatThrownBy(() -> stub.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(1L).setExerciseId(99L).setOrder(1).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateWorkoutExercise_whenExists_returnsUpdated() {
        WorkoutExerciseResponse updated = workoutExercise().setOrder(2).build();
        when(workoutExerciseService.updateWorkoutExercise(eq(1L), any())).thenReturn(updated);

        WorkoutExerciseResponse response = stub.updateWorkoutExercise(UpdateWorkoutExerciseRequest.newBuilder()
                .setId(1L)
                .setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(2).build())
                .build());

        assertThat(response.getOrder()).isEqualTo(2);
    }

    @Test
    void updateWorkoutExercise_withZeroOrder_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.updateWorkoutExercise(UpdateWorkoutExerciseRequest.newBuilder()
                .setId(1L)
                .setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(0).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void updateWorkoutExercise_whenMissing_throwsNotFound() {
        when(workoutExerciseService.updateWorkoutExercise(eq(99L), any())).thenThrow(new ResourceNotFoundException("WorkoutExercise", 99L));

        assertThatThrownBy(() -> stub.updateWorkoutExercise(UpdateWorkoutExerciseRequest.newBuilder()
                .setId(99L)
                .setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(1).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteWorkoutExercise_whenExists_succeeds() {
        Empty response = stub.deleteWorkoutExercise(DeleteWorkoutExerciseRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteWorkoutExercise_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("WorkoutExercise", 99L)).when(workoutExerciseService).deleteWorkoutExercise(99L);

        assertThatThrownBy(() -> stub.deleteWorkoutExercise(DeleteWorkoutExerciseRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    // Authorization: owner, another trainer, the plan's client, another client, ADMIN.

    @Test
    void listWorkoutExercises_followsThePlanReadRule() {
        callers.assertReadRule(c -> c.listWorkoutExercises(ListWorkoutExercisesRequest.newBuilder().setWorkoutId(WORKOUT_ID).build()));
    }

    @Test
    void getWorkoutExercise_followsThePlanReadRule() {
        callers.assertReadRule(c -> c.getWorkoutExercise(GetWorkoutExerciseRequest.newBuilder().setId(WORKOUT_EXERCISE_ID).build()));
    }

    @Test
    void createWorkoutExercise_followsThePlanWriteRule() {
        callers.assertWriteRule(c -> c.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(WORKOUT_ID).setExerciseId(2L).setOrder(1).build()));
    }

    @Test
    void updateWorkoutExercise_followsThePlanWriteRule() {
        callers.assertWriteRule(c -> c.updateWorkoutExercise(UpdateWorkoutExerciseRequest.newBuilder()
                .setId(WORKOUT_EXERCISE_ID).setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(1)).build()));
    }

    @Test
    void deleteWorkoutExercise_followsThePlanWriteRule() {
        callers.assertWriteRule(c -> c.deleteWorkoutExercise(DeleteWorkoutExerciseRequest.newBuilder().setId(WORKOUT_EXERCISE_ID).build()));
    }

    @Test
    void serviceTokenIsDenied_andNoTokenIsUnauthenticated() {
        callers.assertNoServiceOrAnonymous(c -> c.createWorkoutExercise(WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(WORKOUT_ID).setExerciseId(2L).setOrder(1).build()));
    }

    // Upper bounds (P1-09): WorkoutLimits, and VARCHAR(255) for notes.

    private static WorkoutExerciseCreateRequest.Builder validCreateRequest() {
        return WorkoutExerciseCreateRequest.newBuilder().setWorkoutId(1L).setExerciseId(2L).setOrder(1);
    }

    @Test
    void createWorkoutExercise_atEveryUpperBound_isAccepted() {
        stub.createWorkoutExercise(validCreateRequest().setOrder(1_000).setRestSecondsBetweenSets(3_600)
                .setNotes("n".repeat(255)).build());
    }

    @Test
    void createWorkoutExercise_pastAnUpperBound_throwsInvalidArgument() {
        assertInvalidField(() -> stub.createWorkoutExercise(validCreateRequest().setOrder(1_001).build()), "order");
        assertInvalidField(() -> stub.createWorkoutExercise(validCreateRequest().setRestSecondsBetweenSets(3_601).build()),
                "restSecondsBetweenSets");
        assertInvalidField(() -> stub.createWorkoutExercise(validCreateRequest().setNotes("n".repeat(256)).build()), "notes");
    }

    @Test
    void updateWorkoutExercise_pastAnUpperBound_throwsInvalidArgument() {
        assertInvalidField(() -> stub.updateWorkoutExercise(UpdateWorkoutExerciseRequest.newBuilder().setId(WORKOUT_EXERCISE_ID)
                .setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(1_001)).build()), "order");
        assertInvalidField(() -> stub.updateWorkoutExercise(UpdateWorkoutExerciseRequest.newBuilder().setId(WORKOUT_EXERCISE_ID)
                .setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(1).setNotes("n".repeat(256))).build()), "notes");
    }
}
