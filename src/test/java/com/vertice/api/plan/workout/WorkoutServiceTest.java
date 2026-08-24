package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private TrainingPlanRepository trainingPlanRepository;

    private WorkoutService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutService(workoutRepository, Mappers.getMapper(WorkoutMapper.class), trainingPlanRepository);
    }

    @Test
    void createWorkout_setsTrainingPlanAndSaves() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(trainingPlan));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutCreateRequest request = WorkoutCreateRequest.newBuilder()
                .setName("Day 1 - Push")
                .setTrainingPlanId(1L)
                .build();

        var response = service.createWorkout(request);

        assertThat(response.getName()).isEqualTo("Day 1 - Push");
        assertThat(response.getTrainingPlanId()).isEqualTo(1L);
    }

    @Test
    void createWorkout_throwsWhenTrainingPlanMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutCreateRequest request = WorkoutCreateRequest.newBuilder()
                .setName("Day 1")
                .setTrainingPlanId(99L)
                .build();

        assertThatThrownBy(() -> service.createWorkout(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void updateWorkout_updatesNameOnly() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        Workout existing = new Workout();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setTrainingPlan(trainingPlan);

        when(workoutRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutRequest request = WorkoutRequest.newBuilder().setName("New Name").build();

        var response = service.updateWorkout(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getTrainingPlanId()).isEqualTo(1L);
    }

    @Test
    void updateWorkout_throwsWhenMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutRequest request = WorkoutRequest.newBuilder().setName("Name").build();

        assertThatThrownBy(() -> service.updateWorkout(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listWorkouts_returnsWorkoutsForTrainingPlan() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        Workout workout = new Workout();
        workout.setId(1L);
        workout.setName("Day 1");
        workout.setTrainingPlan(trainingPlan);

        when(workoutRepository.findByTrainingPlanId(1L)).thenReturn(List.of(workout));

        var responses = service.listWorkouts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getTrainingPlanId()).isEqualTo(1L);
    }

    @Test
    void getWorkout_throwsWhenMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWorkout(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteWorkout_throwsWhenMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWorkout(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
