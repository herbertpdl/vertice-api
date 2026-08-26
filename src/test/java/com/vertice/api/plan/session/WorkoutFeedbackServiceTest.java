package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.workout.Workout;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutFeedbackServiceTest {

    @Mock
    private WorkoutFeedbackRepository workoutFeedbackRepository;

    @Mock
    private WorkoutLogRepository workoutLogRepository;

    private WorkoutFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutFeedbackService(workoutFeedbackRepository, Mappers.getMapper(WorkoutFeedbackMapper.class), workoutLogRepository);
    }

    private static WorkoutLog completedLog(long id) {
        User trainer = new User();
        trainer.setId(1L);
        trainer.setRole(Role.TRAINER);
        User client = new User();
        client.setId(2L);
        client.setRole(Role.CLIENT);

        TrainingPlan plan = new TrainingPlan();
        plan.setId(3L);
        plan.setTrainer(trainer);
        plan.setClient(client);

        Workout workout = new Workout();
        workout.setId(4L);
        workout.setTrainingPlan(plan);

        WorkoutLog log = new WorkoutLog();
        log.setId(id);
        log.setWorkout(workout);
        log.setClient(client);
        log.setCompletedAt(Instant.now());
        return log;
    }

    @Test
    void submitWorkoutFeedback_savesAndResolvesLinkedIds() {
        WorkoutLog log = completedLog(10L);
        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));
        when(workoutFeedbackRepository.save(any(WorkoutFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(10L).setText("Great session, felt strong today!").build();

        var response = service.submitWorkoutFeedback(request);

        assertThat(response.getWorkoutLogId()).isEqualTo(10L);
        assertThat(response.getWorkoutId()).isEqualTo(4L);
        assertThat(response.getTrainingPlanId()).isEqualTo(3L);
        assertThat(response.getClientId()).isEqualTo(2L);
        assertThat(response.getText()).isEqualTo("Great session, felt strong today!");
        assertThat(response.getCreatedAt()).isNotEmpty();
    }

    @Test
    void submitWorkoutFeedback_throwsWhenWorkoutLogMissing() {
        when(workoutLogRepository.findById(99L)).thenReturn(Optional.empty());

        var request = SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(99L).setText("Feedback").build();

        assertThatThrownBy(() -> service.submitWorkoutFeedback(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutFeedbackRepository, never()).save(any());
    }

    @Test
    void submitWorkoutFeedback_throwsWhenWorkoutLogNotCompleted() {
        WorkoutLog log = completedLog(10L);
        log.setCompletedAt(null);
        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));

        var request = SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(10L).setText("Feedback").build();

        assertThatThrownBy(() -> service.submitWorkoutFeedback(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(workoutFeedbackRepository, never()).save(any());
    }

    @Test
    void listWorkoutFeedback_returnsFeedbackForTrainer() {
        WorkoutLog log = completedLog(10L);
        WorkoutFeedback feedback = new WorkoutFeedback();
        feedback.setId(20L);
        feedback.setWorkoutLog(log);
        feedback.setText("Feedback text");
        feedback.setCreatedAt(Instant.now());

        when(workoutFeedbackRepository.findByWorkoutLog_Workout_TrainingPlan_TrainerId(1L)).thenReturn(List.of(feedback));

        var responses = service.listWorkoutFeedback(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getWorkoutId()).isEqualTo(4L);
        assertThat(responses.getFirst().getTrainingPlanId()).isEqualTo(3L);
        assertThat(responses.getFirst().getClientId()).isEqualTo(2L);
    }

    @Test
    void listWorkoutFeedback_returnsEmptyForUnknownTrainer() {
        when(workoutFeedbackRepository.findByWorkoutLog_Workout_TrainingPlan_TrainerId(99L)).thenReturn(List.of());

        var responses = service.listWorkoutFeedback(99L);

        assertThat(responses).isEmpty();
    }
}
