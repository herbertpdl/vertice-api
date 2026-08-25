package com.vertice.api.plan.workout;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.DeleteExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.GetExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsResponse;
import com.vertice.api.generated.grpc.plan.v1.SetStrategy;
import com.vertice.api.generated.grpc.plan.v1.UpdateExerciseSetRequest;
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

@SpringBootTest(properties = "spring.grpc.server.port=19099")
@ActiveProfiles("local")
class ExerciseSetControllerTest {

    @MockitoBean
    private ExerciseSetService exerciseSetService;

    private ManagedChannel channel;
    private ExerciseSetServiceGrpc.ExerciseSetServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19099").usePlaintext().build();
        stub = ExerciseSetServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static ExerciseSetResponse.Builder exerciseSet() {
        return ExerciseSetResponse.newBuilder().setId(1L).setWorkoutExerciseId(1L).setSetNumber(1).setStrategy(SetStrategy.STRAIGHT);
    }

    private static ExerciseSetCreateRequest.Builder validCreateRequest() {
        return ExerciseSetCreateRequest.newBuilder().setWorkoutExerciseId(1L).setSetNumber(1).setStrategy(SetStrategy.STRAIGHT);
    }

    @Test
    void listExerciseSets_returnsSetsForWorkoutExercise() {
        ExerciseSetResponse response1 = exerciseSet().build();
        when(exerciseSetService.listExerciseSets(1L)).thenReturn(java.util.List.of(response1));

        ListExerciseSetsResponse response = stub.listExerciseSets(
                ListExerciseSetsRequest.newBuilder().setWorkoutExerciseId(1L).build());

        assertThat(response.getExerciseSetsList()).containsExactly(response1);
    }

    @Test
    void getExerciseSet_whenExists_returnsExerciseSet() {
        ExerciseSetResponse exerciseSet = exerciseSet().build();
        when(exerciseSetService.getExerciseSet(1L)).thenReturn(exerciseSet);

        ExerciseSetResponse response = stub.getExerciseSet(GetExerciseSetRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(exerciseSet);
    }

    @Test
    void getExerciseSet_whenMissing_throwsNotFound() {
        when(exerciseSetService.getExerciseSet(99L)).thenThrow(new ResourceNotFoundException("ExerciseSet", 99L));

        assertThatThrownBy(() -> stub.getExerciseSet(GetExerciseSetRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createExerciseSet_withValidRequest_returnsCreated() {
        ExerciseSetResponse created = exerciseSet().build();
        when(exerciseSetService.createExerciseSet(any())).thenReturn(created);

        ExerciseSetResponse response = stub.createExerciseSet(validCreateRequest().build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createExerciseSet_withZeroSetNumber_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createExerciseSet(validCreateRequest().setSetNumber(0).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createExerciseSet_withNegativeReps_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createExerciseSet(validCreateRequest().setReps(-1).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createExerciseSet_withUnsetStrategy_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createExerciseSet(validCreateRequest().setStrategy(SetStrategy.SET_STRATEGY_UNSPECIFIED).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void createExerciseSet_withMissingWorkoutExercise_throwsNotFound() {
        when(exerciseSetService.createExerciseSet(any())).thenThrow(new ResourceNotFoundException("WorkoutExercise", 99L));

        assertThatThrownBy(() -> stub.createExerciseSet(validCreateRequest().setWorkoutExerciseId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateExerciseSet_whenExists_returnsUpdated() {
        ExerciseSetResponse updated = exerciseSet().setSetNumber(2).build();
        when(exerciseSetService.updateExerciseSet(eq(1L), any())).thenReturn(updated);

        ExerciseSetResponse response = stub.updateExerciseSet(UpdateExerciseSetRequest.newBuilder()
                .setId(1L)
                .setExerciseSet(ExerciseSetRequest.newBuilder().setSetNumber(2).setStrategy(SetStrategy.STRAIGHT).build())
                .build());

        assertThat(response.getSetNumber()).isEqualTo(2);
    }

    @Test
    void updateExerciseSet_withUnsetStrategy_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.updateExerciseSet(UpdateExerciseSetRequest.newBuilder()
                .setId(1L)
                .setExerciseSet(ExerciseSetRequest.newBuilder().setSetNumber(1).setStrategy(SetStrategy.SET_STRATEGY_UNSPECIFIED).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void updateExerciseSet_whenMissing_throwsNotFound() {
        when(exerciseSetService.updateExerciseSet(eq(99L), any())).thenThrow(new ResourceNotFoundException("ExerciseSet", 99L));

        assertThatThrownBy(() -> stub.updateExerciseSet(UpdateExerciseSetRequest.newBuilder()
                .setId(99L)
                .setExerciseSet(ExerciseSetRequest.newBuilder().setSetNumber(1).setStrategy(SetStrategy.STRAIGHT).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteExerciseSet_whenExists_succeeds() {
        Empty response = stub.deleteExerciseSet(DeleteExerciseSetRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteExerciseSet_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("ExerciseSet", 99L)).when(exerciseSetService).deleteExerciseSet(99L);

        assertThatThrownBy(() -> stub.deleteExerciseSet(DeleteExerciseSetRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }
}
