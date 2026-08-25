package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.SetStrategy;
import jakarta.validation.ConstraintViolationException;
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
class ExerciseSetServiceTest {

    @Mock
    private ExerciseSetRepository exerciseSetRepository;

    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;

    private ExerciseSetService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseSetService(exerciseSetRepository, Mappers.getMapper(ExerciseSetMapper.class), workoutExerciseRepository);
    }

    private static WorkoutExercise workoutExercise(long id) {
        WorkoutExercise workoutExercise = new WorkoutExercise();
        workoutExercise.setId(id);
        return workoutExercise;
    }

    @Test
    void createExerciseSet_setsWorkoutExerciseAndSaves() {
        when(workoutExerciseRepository.findById(1L)).thenReturn(Optional.of(workoutExercise(1L)));
        when(exerciseSetRepository.save(any(ExerciseSet.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseSetCreateRequest request = ExerciseSetCreateRequest.newBuilder()
                .setWorkoutExerciseId(1L).setSetNumber(1).setReps(12).setWeight("60.5")
                .setStrategy(SetStrategy.STRAIGHT).setRestSeconds(90).setNotes("Warm set")
                .build();

        var response = service.createExerciseSet(request);

        assertThat(response.getWorkoutExerciseId()).isEqualTo(1L);
        assertThat(response.getSetNumber()).isEqualTo(1);
        assertThat(response.getReps()).isEqualTo(12);
        assertThat(response.getWeight()).isEqualTo("60.5");
        assertThat(response.getStrategy()).isEqualTo(SetStrategy.STRAIGHT);
        assertThat(response.getRestSeconds()).isEqualTo(90);
        assertThat(response.getNotes()).isEqualTo("Warm set");
    }

    @Test
    void createExerciseSet_allowsOmittedReps_forIsometricSets() {
        when(workoutExerciseRepository.findById(1L)).thenReturn(Optional.of(workoutExercise(1L)));
        when(exerciseSetRepository.save(any(ExerciseSet.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseSetCreateRequest request = ExerciseSetCreateRequest.newBuilder()
                .setWorkoutExerciseId(1L).setSetNumber(1).setDurationSeconds(45)
                .setStrategy(SetStrategy.ISOMETRIC_HOLD)
                .build();

        var response = service.createExerciseSet(request);

        assertThat(response.getReps()).isZero();
        assertThat(response.getDurationSeconds()).isEqualTo(45);
        assertThat(response.getWeight()).isEmpty();
        assertThat(response.getLoadPercentage()).isEmpty();
    }

    @Test
    void createExerciseSet_throwsWhenWeightIsNegative() {
        when(workoutExerciseRepository.findById(1L)).thenReturn(Optional.of(workoutExercise(1L)));

        ExerciseSetCreateRequest request = ExerciseSetCreateRequest.newBuilder()
                .setWorkoutExerciseId(1L).setSetNumber(1).setWeight("-5").setStrategy(SetStrategy.STRAIGHT)
                .build();

        assertThatThrownBy(() -> service.createExerciseSet(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(exerciseSetRepository, never()).save(any());
    }

    @Test
    void createExerciseSet_throwsWhenWeightIsMalformed() {
        when(workoutExerciseRepository.findById(1L)).thenReturn(Optional.of(workoutExercise(1L)));

        ExerciseSetCreateRequest request = ExerciseSetCreateRequest.newBuilder()
                .setWorkoutExerciseId(1L).setSetNumber(1).setWeight("not-a-number").setStrategy(SetStrategy.STRAIGHT)
                .build();

        assertThatThrownBy(() -> service.createExerciseSet(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(exerciseSetRepository, never()).save(any());
    }

    @Test
    void createExerciseSet_throwsWhenWorkoutExerciseMissing() {
        when(workoutExerciseRepository.findById(99L)).thenReturn(Optional.empty());

        ExerciseSetCreateRequest request = ExerciseSetCreateRequest.newBuilder()
                .setWorkoutExerciseId(99L).setSetNumber(1).setStrategy(SetStrategy.STRAIGHT).build();

        assertThatThrownBy(() -> service.createExerciseSet(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(exerciseSetRepository, never()).save(any());
    }

    @Test
    void updateExerciseSet_updatesFieldsAndKeepsWorkoutExercise() {
        ExerciseSet existing = new ExerciseSet();
        existing.setId(1L);
        existing.setWorkoutExercise(workoutExercise(1L));
        existing.setSetNumber(1);
        existing.setStrategy(com.vertice.api.plan.workout.SetStrategy.STRAIGHT);

        when(exerciseSetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(exerciseSetRepository.save(any(ExerciseSet.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseSetRequest request = ExerciseSetRequest.newBuilder()
                .setSetNumber(2).setReps(10).setWeight("70").setLoadPercentage("80")
                .setStrategy(SetStrategy.BACKOFF).setRestSeconds(60)
                .build();

        var response = service.updateExerciseSet(1L, request);

        assertThat(response.getSetNumber()).isEqualTo(2);
        assertThat(response.getWeight()).isEqualTo("70");
        assertThat(response.getLoadPercentage()).isEqualTo("80");
        assertThat(response.getStrategy()).isEqualTo(SetStrategy.BACKOFF);
        assertThat(response.getWorkoutExerciseId()).isEqualTo(1L);
    }

    @Test
    void updateExerciseSet_throwsWhenMissing() {
        when(exerciseSetRepository.findById(99L)).thenReturn(Optional.empty());

        ExerciseSetRequest request = ExerciseSetRequest.newBuilder().setSetNumber(1).setStrategy(SetStrategy.STRAIGHT).build();

        assertThatThrownBy(() -> service.updateExerciseSet(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listExerciseSets_returnsSetsForWorkoutExercise() {
        ExerciseSet exerciseSet = new ExerciseSet();
        exerciseSet.setId(1L);
        exerciseSet.setWorkoutExercise(workoutExercise(1L));
        exerciseSet.setSetNumber(1);
        exerciseSet.setStrategy(com.vertice.api.plan.workout.SetStrategy.STRAIGHT);

        when(exerciseSetRepository.findByWorkoutExerciseId(1L)).thenReturn(List.of(exerciseSet));

        var responses = service.listExerciseSets(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getWorkoutExerciseId()).isEqualTo(1L);
        assertThat(responses.getFirst().getWeight()).isEmpty();
        assertThat(responses.getFirst().getNotes()).isEmpty();
    }

    @Test
    void getExerciseSet_throwsWhenMissing() {
        when(exerciseSetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExerciseSet(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteExerciseSet_throwsWhenMissing() {
        when(exerciseSetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteExerciseSet(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
