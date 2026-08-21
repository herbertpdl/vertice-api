package com.vertice.api.plan.exercise;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.DeleteExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseServiceGrpc;
import com.vertice.api.generated.grpc.exercise.v1.GetExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesRequest;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesResponse;
import com.vertice.api.generated.grpc.exercise.v1.UpdateExerciseRequest;
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

@SpringBootTest(properties = "spring.grpc.server.port=19095")
@ActiveProfiles("local")
class ExerciseControllerTest {

    @MockitoBean
    private ExerciseService exerciseService;

    private ManagedChannel channel;
    private ExerciseServiceGrpc.ExerciseServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19095").usePlaintext().build();
        stub = ExerciseServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listExercises_returnsAll() {
        ExerciseResponse exercise = ExerciseResponse.newBuilder().setId(1L).setName("Squat").setDescription("Barbell back squat").build();
        when(exerciseService.listExercises()).thenReturn(java.util.List.of(exercise));

        ListExercisesResponse response = stub.listExercises(ListExercisesRequest.newBuilder().build());

        assertThat(response.getExercisesList()).containsExactly(exercise);
    }

    @Test
    void getExercise_whenExists_returnsExercise() {
        ExerciseResponse exercise = ExerciseResponse.newBuilder().setId(1L).setName("Squat").setDescription("Barbell back squat").build();
        when(exerciseService.getExercise(1L)).thenReturn(exercise);

        ExerciseResponse response = stub.getExercise(GetExerciseRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(exercise);
    }

    @Test
    void getExercise_whenMissing_throwsNotFound() {
        when(exerciseService.getExercise(99L)).thenThrow(new ResourceNotFoundException("Exercise", 99L));

        assertThatThrownBy(() -> stub.getExercise(GetExerciseRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createExercise_withValidRequest_returnsCreated() {
        ExerciseResponse created = ExerciseResponse.newBuilder().setId(1L).setName("Squat").setDescription("Barbell back squat").build();
        when(exerciseService.createExercise(any())).thenReturn(created);

        ExerciseResponse response = stub.createExercise(ExerciseRequest.newBuilder()
                .setName("Squat").setDescription("Barbell back squat").build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createExercise_withBlankName_throwsInvalidArgument() {
        assertThatThrownBy(() -> stub.createExercise(ExerciseRequest.newBuilder()
                .setName("").setDescription("Barbell back squat").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void updateExercise_whenExists_returnsUpdated() {
        ExerciseResponse updated = ExerciseResponse.newBuilder().setId(1L).setName("New Name").setDescription("New description").build();
        when(exerciseService.updateExercise(eq(1L), any())).thenReturn(updated);

        ExerciseResponse response = stub.updateExercise(UpdateExerciseRequest.newBuilder()
                .setId(1L)
                .setExercise(ExerciseRequest.newBuilder().setName("New Name").setDescription("New description").build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateExercise_whenMissing_throwsNotFound() {
        when(exerciseService.updateExercise(eq(99L), any())).thenThrow(new ResourceNotFoundException("Exercise", 99L));

        assertThatThrownBy(() -> stub.updateExercise(UpdateExerciseRequest.newBuilder()
                .setId(99L)
                .setExercise(ExerciseRequest.newBuilder().setName("Name").build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void deleteExercise_whenExists_succeeds() {
        Empty response = stub.deleteExercise(DeleteExerciseRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteExercise_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Exercise", 99L)).when(exerciseService).deleteExercise(99L);

        assertThatThrownBy(() -> stub.deleteExercise(DeleteExerciseRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }
}
