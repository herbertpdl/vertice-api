package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackResponse;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19101", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutFeedbackControllerTest {

    @MockitoBean
    private WorkoutFeedbackService workoutFeedbackService;

    private ManagedChannel channel;
    private WorkoutFeedbackServiceGrpc.WorkoutFeedbackServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19101").usePlaintext().build();
        stub = WorkoutFeedbackServiceGrpc.newBlockingStub(channel);
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
}
