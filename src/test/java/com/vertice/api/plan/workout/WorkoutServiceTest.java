package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
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
                .setDayOfWeek(DayOfWeek.MONDAY)
                .build();

        var response = service.createWorkout(request);

        assertThat(response.getName()).isEqualTo("Day 1 - Push");
        assertThat(response.getTrainingPlanId()).isEqualTo(1L);
        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void createWorkout_throwsWhenTrainingPlanMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutCreateRequest request = WorkoutCreateRequest.newBuilder()
                .setName("Day 1")
                .setTrainingPlanId(99L)
                .setDayOfWeek(DayOfWeek.MONDAY)
                .build();

        assertThatThrownBy(() -> service.createWorkout(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void updateWorkout_updatesNameAndDayOfWeek() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        Workout existing = new Workout();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setDayOfWeek(com.vertice.api.plan.workout.DayOfWeek.MONDAY);
        existing.setTrainingPlan(trainingPlan);

        when(workoutRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutRequest request = WorkoutRequest.newBuilder().setName("New Name").setDayOfWeek(DayOfWeek.WEDNESDAY).build();

        var response = service.updateWorkout(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getTrainingPlanId()).isEqualTo(1L);
        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    void updateWorkout_throwsWhenMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        WorkoutRequest request = WorkoutRequest.newBuilder().setName("Name").setDayOfWeek(DayOfWeek.MONDAY).build();

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
        workout.setDayOfWeek(com.vertice.api.plan.workout.DayOfWeek.MONDAY);
        workout.setTrainingPlan(trainingPlan);

        when(workoutRepository.findByTrainingPlanId(1L)).thenReturn(List.of(workout));

        var responses = service.listWorkouts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getTrainingPlanId()).isEqualTo(1L);
        assertThat(responses.getFirst().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
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

    @Test
    void cloneWorkout_deepCopiesWorkoutExercisesAndSets() {
        TrainingPlan sourcePlan = new TrainingPlan();
        sourcePlan.setId(1L);
        Workout source = new Workout();
        source.setId(1L);
        source.setName("Week 1 - Push");
        source.setDayOfWeek(com.vertice.api.plan.workout.DayOfWeek.MONDAY);
        source.setTrainingPlan(sourcePlan);

        Exercise benchPress = new Exercise();
        benchPress.setId(10L);

        WorkoutExercise sourceWorkoutExercise = new WorkoutExercise();
        sourceWorkoutExercise.setId(100L);
        sourceWorkoutExercise.setWorkout(source);
        sourceWorkoutExercise.setExercise(benchPress);
        sourceWorkoutExercise.setOrder(1);
        sourceWorkoutExercise.setRestSecondsBetweenSets(90);
        sourceWorkoutExercise.setNotes("First movement");

        ExerciseSet sourceSet = new ExerciseSet();
        sourceSet.setId(1000L);
        sourceSet.setWorkoutExercise(sourceWorkoutExercise);
        sourceSet.setSetNumber(1);
        sourceSet.setReps(10);
        sourceSet.setWeight(java.math.BigDecimal.valueOf(60));
        sourceSet.setStrategy(com.vertice.api.plan.workout.SetStrategy.STRAIGHT);
        sourceWorkoutExercise.getExerciseSets().add(sourceSet);
        source.getWorkoutExercises().add(sourceWorkoutExercise);

        TrainingPlan targetPlan = new TrainingPlan();
        targetPlan.setId(2L);

        when(workoutRepository.findById(1L)).thenReturn(Optional.of(source));
        when(trainingPlanRepository.findById(2L)).thenReturn(Optional.of(targetPlan));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        CloneWorkoutRequest request = CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(2L)
                .setName("Week 2 - Push").setDayOfWeek(DayOfWeek.WEDNESDAY)
                .build();

        var response = service.cloneWorkout(request);

        assertThat(response.getName()).isEqualTo("Week 2 - Push");
        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(response.getTrainingPlanId()).isEqualTo(2L);

        var savedWorkoutCaptor = org.mockito.ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(savedWorkoutCaptor.capture());
        Workout saved = savedWorkoutCaptor.getValue();
        assertThat(saved.getWorkoutExercises()).hasSize(1);
        WorkoutExercise savedWorkoutExercise = saved.getWorkoutExercises().getFirst();
        assertThat(savedWorkoutExercise).isNotSameAs(sourceWorkoutExercise);
        assertThat(savedWorkoutExercise.getExercise()).isSameAs(benchPress);
        assertThat(savedWorkoutExercise.getOrder()).isEqualTo(1);
        assertThat(savedWorkoutExercise.getRestSecondsBetweenSets()).isEqualTo(90);
        assertThat(savedWorkoutExercise.getExerciseSets()).hasSize(1);
        ExerciseSet savedSet = savedWorkoutExercise.getExerciseSets().getFirst();
        assertThat(savedSet).isNotSameAs(sourceSet);
        assertThat(savedSet.getReps()).isEqualTo(10);
        assertThat(savedSet.getWeight()).isEqualByComparingTo("60");
    }

    @Test
    void cloneWorkout_allowsCloningWorkoutWithNoExercises() {
        TrainingPlan sourcePlan = new TrainingPlan();
        sourcePlan.setId(1L);
        Workout source = new Workout();
        source.setId(1L);
        source.setName("Empty Day");
        source.setDayOfWeek(com.vertice.api.plan.workout.DayOfWeek.MONDAY);
        source.setTrainingPlan(sourcePlan);

        when(workoutRepository.findById(1L)).thenReturn(Optional.of(source));
        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(sourcePlan));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        CloneWorkoutRequest request = CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(1L)
                .setName("Empty Day Copy").setDayOfWeek(DayOfWeek.TUESDAY)
                .build();

        var response = service.cloneWorkout(request);

        assertThat(response.getName()).isEqualTo("Empty Day Copy");
        assertThat(response.getTrainingPlanId()).isEqualTo(1L);
    }

    @Test
    void cloneWorkout_throwsWhenSourceMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        CloneWorkoutRequest request = CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(99L).setTargetTrainingPlanId(1L)
                .setName("Copy").setDayOfWeek(DayOfWeek.MONDAY)
                .build();

        assertThatThrownBy(() -> service.cloneWorkout(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void cloneWorkout_throwsWhenTargetPlanMissing() {
        Workout source = new Workout();
        source.setId(1L);
        source.setTrainingPlan(new TrainingPlan());
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(source));
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        CloneWorkoutRequest request = CloneWorkoutRequest.newBuilder()
                .setSourceWorkoutId(1L).setTargetTrainingPlanId(99L)
                .setName("Copy").setDayOfWeek(DayOfWeek.MONDAY)
                .build();

        assertThatThrownBy(() -> service.cloneWorkout(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutRepository, never()).save(any());
    }
}
