package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseRequest;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
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
class WorkoutExerciseServiceTest {

    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    private WorkoutExerciseService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutExerciseService(workoutExerciseRepository, Mappers.getMapper(WorkoutExerciseMapper.class),
                workoutRepository, exerciseRepository);
    }

    private static Workout workout(long id) {
        Workout workout = new Workout();
        workout.setId(id);
        return workout;
    }

    private static Exercise exercise(long id) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        return exercise;
    }

    @Test
    void createWorkoutExercise_setsWorkoutAndExerciseAndSaves() {
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout(1L)));
        when(exerciseRepository.findById(2L)).thenReturn(Optional.of(exercise(2L)));
        when(workoutExerciseRepository.save(any(WorkoutExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutExerciseCreateRequest request = WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(1L).setExerciseId(2L).setOrder(1).setRestSecondsBetweenSets(90).setNotes("First movement")
                .build();

        var response = service.createWorkoutExercise(request);

        assertThat(response.getWorkoutId()).isEqualTo(1L);
        assertThat(response.getExerciseId()).isEqualTo(2L);
        assertThat(response.getOrder()).isEqualTo(1);
        assertThat(response.getRestSecondsBetweenSets()).isEqualTo(90);
        assertThat(response.getNotes()).isEqualTo("First movement");
    }

    @Test
    void createWorkoutExercise_throwsWhenWorkoutMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutExerciseCreateRequest request = WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(99L).setExerciseId(2L).setOrder(1).build();

        assertThatThrownBy(() -> service.createWorkoutExercise(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutExerciseRepository, never()).save(any());
    }

    @Test
    void createWorkoutExercise_throwsWhenExerciseMissing() {
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout(1L)));
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutExerciseCreateRequest request = WorkoutExerciseCreateRequest.newBuilder()
                .setWorkoutId(1L).setExerciseId(99L).setOrder(1).build();

        assertThatThrownBy(() -> service.createWorkoutExercise(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutExerciseRepository, never()).save(any());
    }

    @Test
    void updateWorkoutExercise_updatesOrderRestAndNotesOnly() {
        WorkoutExercise existing = new WorkoutExercise();
        existing.setId(1L);
        existing.setWorkout(workout(1L));
        existing.setExercise(exercise(2L));
        existing.setOrder(1);

        when(workoutExerciseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workoutExerciseRepository.save(any(WorkoutExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutExerciseRequest request = WorkoutExerciseRequest.newBuilder()
                .setOrder(2).setRestSecondsBetweenSets(60).setNotes("Updated").build();

        var response = service.updateWorkoutExercise(1L, request);

        assertThat(response.getOrder()).isEqualTo(2);
        assertThat(response.getRestSecondsBetweenSets()).isEqualTo(60);
        assertThat(response.getNotes()).isEqualTo("Updated");
        assertThat(response.getWorkoutId()).isEqualTo(1L);
        assertThat(response.getExerciseId()).isEqualTo(2L);
    }

    @Test
    void updateWorkoutExercise_throwsWhenMissing() {
        when(workoutExerciseRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutExerciseRequest request = WorkoutExerciseRequest.newBuilder().setOrder(1).build();

        assertThatThrownBy(() -> service.updateWorkoutExercise(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listWorkoutExercises_returnsExercisesForWorkout() {
        WorkoutExercise workoutExercise = new WorkoutExercise();
        workoutExercise.setId(1L);
        workoutExercise.setWorkout(workout(1L));
        workoutExercise.setExercise(exercise(2L));
        workoutExercise.setOrder(1);

        when(workoutExerciseRepository.findByWorkoutId(1L)).thenReturn(List.of(workoutExercise));

        var responses = service.listWorkoutExercises(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getWorkoutId()).isEqualTo(1L);
        assertThat(responses.getFirst().getNotes()).isEmpty();
    }

    @Test
    void getWorkoutExercise_throwsWhenMissing() {
        when(workoutExerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWorkoutExercise(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteWorkoutExercise_throwsWhenMissing() {
        when(workoutExerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWorkoutExercise(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
