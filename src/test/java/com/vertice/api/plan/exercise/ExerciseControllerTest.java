package com.vertice.api.plan.exercise;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.DeleteExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseServiceGrpc;
import com.vertice.api.generated.grpc.exercise.v1.GetExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesResponse;
import com.vertice.api.generated.grpc.exercise.v1.ListMuscleGroupsRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListMuscleGroupsResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.generated.grpc.exercise.v1.UpdateExerciseRequest;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.user.Role;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19095", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class ExerciseControllerTest {

    private static final long TRAINER_ID = 10L;
    private static final MuscleGroupResponse PEITO = MuscleGroupResponse.newBuilder().setId(1L).setName("Peito").build();
    private static final MuscleGroupResponse QUADRICEPS = MuscleGroupResponse.newBuilder().setId(7L).setName("Quadríceps").build();

    @MockitoBean
    private ExerciseService exerciseService;

    private ManagedChannel channel;
    /** Calls as trainer {@link #TRAINER_ID}; {@link #anonymousStub} sends no token. */
    private ExerciseServiceGrpc.ExerciseServiceBlockingStub stub;
    private ExerciseServiceGrpc.ExerciseServiceBlockingStub anonymousStub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19095").usePlaintext().build();
        anonymousStub = ExerciseServiceGrpc.newBlockingStub(channel);
        stub = TestJwts.asCaller(anonymousStub, TRAINER_ID, Role.TRAINER);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listMuscleGroups_returnsGroups() {
        when(exerciseService.listMuscleGroups()).thenReturn(List.of(PEITO, QUADRICEPS));

        ListMuscleGroupsResponse response = stub.listMuscleGroups(ListMuscleGroupsRequest.getDefaultInstance());

        assertThat(response.getMuscleGroupsList()).containsExactly(PEITO, QUADRICEPS);
    }

    @Test
    void listMuscleGroups_withoutToken_succeeds() {
        when(exerciseService.listMuscleGroups()).thenReturn(List.of(PEITO));

        ListMuscleGroupsResponse response = anonymousStub.listMuscleGroups(ListMuscleGroupsRequest.getDefaultInstance());

        assertThat(response.getMuscleGroupsList()).containsExactly(PEITO);
    }

    @Test
    void listExercises_withoutToken_unauthenticated() {
        assertUnauthenticated(() -> anonymousStub.listExercises(ListExercisesRequest.getDefaultInstance()));
    }

    @Test
    void getExercise_withoutToken_unauthenticated() {
        assertUnauthenticated(() -> anonymousStub.getExercise(GetExerciseRequest.newBuilder().setId(1L).build()));
    }

    @Test
    void createExercise_withoutToken_unauthenticated() {
        // Identity is checked before the request shape: even an invalid request is UNAUTHENTICATED.
        assertUnauthenticated(() -> anonymousStub.createExercise(ExerciseRequest.newBuilder().build()));
    }

    @Test
    void updateExercise_withoutToken_unauthenticated() {
        assertUnauthenticated(() -> anonymousStub.updateExercise(UpdateExerciseRequest.newBuilder()
                .setId(1L).setExercise(squatRequest().build()).build()));
    }

    @Test
    void deleteExercise_withoutToken_unauthenticated() {
        assertUnauthenticated(() -> anonymousStub.deleteExercise(DeleteExerciseRequest.newBuilder().setId(1L).build()));
    }

    @Test
    void listExercises_clientToken_permissionDenied() {
        when(exerciseService.listExercises(new CallerIdentity(20L, Role.CLIENT)))
                .thenThrow(PermissionDeniedException.role(Role.CLIENT, "list exercises"));

        assertPermissionDenied(() -> TestJwts.asCaller(anonymousStub, 20L, Role.CLIENT)
                        .listExercises(ListExercisesRequest.getDefaultInstance()),
                "Role CLIENT is not allowed to list exercises");
    }

    @Test
    void updateExercise_starter_permissionDeniedWithMessage() {
        when(exerciseService.updateExercise(any(), eq(5L), any()))
                .thenThrow(PermissionDeniedException.starterSet(5L, "changed"));

        assertPermissionDenied(() -> stub.updateExercise(UpdateExerciseRequest.newBuilder()
                        .setId(5L).setExercise(squatRequest().build()).build()),
                "Exercise 5 belongs to the shared starter set and cannot be changed");
    }

    @Test
    void deleteExercise_otherTrainers_permissionDeniedWithMessage() {
        doThrow(PermissionDeniedException.noAccess("exercise", 6L)).when(exerciseService).deleteExercise(any(), eq(6L));

        assertPermissionDenied(() -> stub.deleteExercise(DeleteExerciseRequest.newBuilder().setId(6L).build()),
                "You do not have access to exercise 6");
    }

    @Test
    void createExercise_passesCallerIdentityToService() {
        when(exerciseService.createExercise(any(), any())).thenReturn(squat());

        stub.createExercise(squatRequest().build());

        ArgumentCaptor<CallerIdentity> caller = ArgumentCaptor.forClass(CallerIdentity.class);
        verify(exerciseService).createExercise(caller.capture(), any());
        assertThat(caller.getValue()).isEqualTo(new CallerIdentity(TRAINER_ID, Role.TRAINER));
    }

    @Test
    void listExercises_returnsAll() {
        ExerciseResponse exercise = squat();
        when(exerciseService.listExercises(any())).thenReturn(List.of(exercise));

        ListExercisesResponse response = stub.listExercises(ListExercisesRequest.newBuilder().build());

        assertThat(response.getExercisesList()).containsExactly(exercise);
    }

    @Test
    void getExercise_whenExists_returnsExercise() {
        ExerciseResponse exercise = squat();
        when(exerciseService.getExercise(any(), eq(1L))).thenReturn(exercise);

        ExerciseResponse response = stub.getExercise(GetExerciseRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(exercise);
    }

    @Test
    void getExercise_whenMissing_throwsNotFound() {
        when(exerciseService.getExercise(any(), eq(99L))).thenThrow(new ResourceNotFoundException("Exercise", 99L));

        assertStatus(() -> stub.getExercise(GetExerciseRequest.newBuilder().setId(99L).build()), Status.Code.NOT_FOUND);
    }

    @Test
    void createExercise_withValidRequest_returnsCreated() {
        ExerciseResponse created = squat();
        when(exerciseService.createExercise(any(), any())).thenReturn(created);

        ExerciseResponse response = stub.createExercise(squatRequest().setDescription("Barbell back squat").build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createExercise_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createExercise(squatRequest().setName("").build()), "name: must not be blank");
    }

    @Test
    void createExercise_nameOver255_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createExercise(squatRequest().setName("a".repeat(256)).build()),
                "name: size must be between 0 and 255");
    }

    @Test
    void createExercise_withoutMuscleGroupIds_throwsInvalidArgumentWithMessage() {
        assertInvalidArgument(() -> stub.createExercise(ExerciseRequest.newBuilder().setName("Squat").build()),
                "muscleGroupIds: must contain at least one muscle group");
        verifyNoInteractions(exerciseService);
    }

    @Test
    void createExercise_withDuplicateGroupIds_isAllowed() {
        ExerciseResponse created = squat();
        when(exerciseService.createExercise(any(), any())).thenReturn(created);

        ExerciseResponse response = stub.createExercise(squatRequest().addMuscleGroupIds(7L).addMuscleGroupIds(7L).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createExercise_withBlankVideoUrl_isAllowed() {
        ExerciseResponse created = squat();
        when(exerciseService.createExercise(any(), any())).thenReturn(created);

        assertThat(stub.createExercise(squatRequest().build())).isEqualTo(created);
    }

    @Test
    void createExercise_withValidVideoUrl_returnsCreated() {
        ExerciseResponse created = squat().toBuilder().setVideoUrl("https://youtube.com/watch?v=abc123").build();
        when(exerciseService.createExercise(any(), any())).thenReturn(created);

        ExerciseResponse response = stub.createExercise(squatRequest().setVideoUrl("https://youtube.com/watch?v=abc123").build());

        assertThat(response.getVideoUrl()).isEqualTo("https://youtube.com/watch?v=abc123");
    }

    @Test
    void createExercise_withMalformedVideoUrl_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createExercise(squatRequest().setVideoUrl("not-a-url").build()),
                "videoUrl: must be a valid http(s) URL");
    }

    @Test
    void createExercise_withNonHttpVideoUrl_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createExercise(squatRequest().setVideoUrl("ftp://example.com/video.mp4").build()),
                "videoUrl: must be a valid http(s) URL");
    }

    @Test
    void updateExercise_whenExists_returnsUpdated() {
        ExerciseResponse updated = squat().toBuilder().setName("New Name").build();
        when(exerciseService.updateExercise(any(), eq(1L), any())).thenReturn(updated);

        ExerciseResponse response = stub.updateExercise(UpdateExerciseRequest.newBuilder()
                .setId(1L)
                .setExercise(squatRequest().setName("New Name").build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateExercise_withoutMuscleGroupIds_throwsInvalidArgumentWithMessage() {
        assertInvalidArgument(() -> stub.updateExercise(UpdateExerciseRequest.newBuilder()
                        .setId(1L)
                        .setExercise(ExerciseRequest.newBuilder().setName("Name").build())
                        .build()),
                "muscleGroupIds: must contain at least one muscle group");
        verifyNoInteractions(exerciseService);
    }

    @Test
    void updateExercise_whenMissing_throwsNotFound() {
        when(exerciseService.updateExercise(any(), eq(99L), any())).thenThrow(new ResourceNotFoundException("Exercise", 99L));

        assertStatus(() -> stub.updateExercise(UpdateExerciseRequest.newBuilder()
                .setId(99L)
                .setExercise(squatRequest().build())
                .build()), Status.Code.NOT_FOUND);
    }

    @Test
    void deleteExercise_whenExists_succeeds() {
        Empty response = stub.deleteExercise(DeleteExerciseRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteExercise_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Exercise", 99L)).when(exerciseService).deleteExercise(any(), eq(99L));

        assertStatus(() -> stub.deleteExercise(DeleteExerciseRequest.newBuilder().setId(99L).build()), Status.Code.NOT_FOUND);
    }

    private static ExerciseResponse squat() {
        return ExerciseResponse.newBuilder().setId(1L).setName("Squat").setDescription("Barbell back squat")
                .addMuscleGroups(QUADRICEPS).build();
    }

    private static ExerciseRequest.Builder squatRequest() {
        return ExerciseRequest.newBuilder().setName("Squat").addMuscleGroupIds(7L);
    }

    private static void assertStatus(ThrowingCallable call, Status.Code code) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(code);
    }

    private static void assertUnauthenticated(ThrowingCallable call) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Caller identity required");
                });
    }

    private static void assertPermissionDenied(ThrowingCallable call, String description) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo(description);
                });
    }

    private static void assertInvalidArgument(ThrowingCallable call, String description) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(ex.getStatus().getDescription()).isEqualTo(description);
                });
    }
}
