package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.exception.WorkoutExerciseHasRecordedDataException;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ReplaceWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.SetStrategy;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.plan.session.SetLog;
import com.vertice.api.plan.session.SetLogRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private TrainingPlanRepository trainingPlanRepository;

    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private SetLogRepository setLogRepository;

    private WorkoutService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutService(workoutRepository, Mappers.getMapper(WorkoutMapper.class), trainingPlanRepository,
                workoutExerciseRepository, Mappers.getMapper(WorkoutExerciseEntryMapper.class), exerciseRepository, setLogRepository);
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

    @Test
    void createWorkoutWithExercises_withNoExercises_producesSameWorkoutAsCreateWorkout() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(trainingPlan));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateWorkoutWithExercisesRequest request = CreateWorkoutWithExercisesRequest.newBuilder()
                .setName("Day 1 - Push")
                .setTrainingPlanId(1L)
                .setDayOfWeek(DayOfWeek.MONDAY)
                .build();

        var response = service.createWorkoutWithExercises(request);

        assertThat(response.getName()).isEqualTo("Day 1 - Push");
        assertThat(response.getTrainingPlanId()).isEqualTo(1L);
        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

        var captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkoutExercises()).isEmpty();
    }

    @Test
    void createWorkoutWithExercises_persistsFullGraphWithOrderAndSetNumberFromPosition() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(trainingPlan));

        Exercise benchPress = new Exercise();
        benchPress.setId(10L);
        benchPress.setName("Bench Press");
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(benchPress));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutExerciseEntry entryWithSets = WorkoutExerciseEntry.newBuilder()
                .setExerciseId(10L)
                .setRestSecondsBetweenSets(90)
                .addSets(ExerciseSetEntry.newBuilder().setReps(10).setWeight("60").build())
                .addSets(ExerciseSetEntry.newBuilder().setReps(8).setWeight("65")
                        .setStrategy(SetStrategy.DROPSET).build())
                .build();
        // Duplicate exercise_id (R7/E5) and zero sets (E6) are both allowed.
        WorkoutExerciseEntry entryWithNoSets = WorkoutExerciseEntry.newBuilder()
                .setExerciseId(10L)
                .build();

        CreateWorkoutWithExercisesRequest request = CreateWorkoutWithExercisesRequest.newBuilder()
                .setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY)
                .addExercises(entryWithSets).addExercises(entryWithNoSets)
                .build();

        service.createWorkoutWithExercises(request);

        var captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(captor.capture());
        List<WorkoutExercise> savedExercises = captor.getValue().getWorkoutExercises();

        assertThat(savedExercises).hasSize(2);
        WorkoutExercise firstExercise = savedExercises.get(0);
        WorkoutExercise secondExercise = savedExercises.get(1);
        assertThat(firstExercise.getExercise()).isSameAs(benchPress);
        assertThat(firstExercise.getOrder()).isEqualTo(1);
        assertThat(secondExercise.getExercise()).isSameAs(benchPress);
        assertThat(secondExercise.getOrder()).isEqualTo(2);
        assertThat(secondExercise.getExerciseSets()).isEmpty();

        assertThat(firstExercise.getExerciseSets()).hasSize(2);
        ExerciseSet firstSet = firstExercise.getExerciseSets().get(0);
        ExerciseSet secondSet = firstExercise.getExerciseSets().get(1);
        assertThat(firstSet.getSetNumber()).isEqualTo(1);
        assertThat(firstSet.getReps()).isEqualTo(10);
        // R6: strategy omitted on the entry defaults to STRAIGHT (not rejected, unlike
        // ExerciseSetController#requireStrategy).
        assertThat(firstSet.getStrategy()).isEqualTo(com.vertice.api.plan.workout.SetStrategy.STRAIGHT);
        assertThat(secondSet.getSetNumber()).isEqualTo(2);
        assertThat(secondSet.getStrategy()).isEqualTo(com.vertice.api.plan.workout.SetStrategy.DROPSET);
    }

    @Test
    void createWorkoutWithExercises_throwsWhenExerciseIdDoesNotExist() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(trainingPlan));
        when(exerciseRepository.findAllById(any())).thenReturn(List.of());

        CreateWorkoutWithExercisesRequest request = CreateWorkoutWithExercisesRequest.newBuilder()
                .setName("Day 1").setTrainingPlanId(1L).setDayOfWeek(DayOfWeek.MONDAY)
                .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(99L).build())
                .build();

        assertThatThrownBy(() -> service.createWorkoutWithExercises(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void createWorkoutWithExercises_throwsWhenTrainingPlanMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        CreateWorkoutWithExercisesRequest request = CreateWorkoutWithExercisesRequest.newBuilder()
                .setName("Day 1").setTrainingPlanId(99L).setDayOfWeek(DayOfWeek.MONDAY)
                .build();

        assertThatThrownBy(() -> service.createWorkoutWithExercises(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void replaceWorkoutExercises_withNoRecordedData_replacesOldTreeWithNewOne() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setId(1L);
        Workout workout = new Workout();
        workout.setId(1L);
        workout.setName("Leg Day");
        workout.setDayOfWeek(com.vertice.api.plan.workout.DayOfWeek.MONDAY);
        workout.setTrainingPlan(trainingPlan);
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout));

        Exercise squat = new Exercise();
        squat.setId(20L);
        squat.setName("Squat");
        WorkoutExercise existingExercise = new WorkoutExercise();
        existingExercise.setId(200L);
        existingExercise.setWorkout(workout);
        existingExercise.setExercise(squat);
        existingExercise.setOrder(1);
        List<WorkoutExercise> currentTree = List.of(existingExercise);
        when(workoutExerciseRepository.findByWorkoutIdWithExerciseSetsAndExercise(1L)).thenReturn(currentTree);
        when(setLogRepository.findByWorkoutId(1L)).thenReturn(List.of());

        Exercise deadlift = new Exercise();
        deadlift.setId(30L);
        deadlift.setName("Deadlift");
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(deadlift));

        ReplaceWorkoutExercisesRequest request = ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(1L)
                .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(30L)
                        .addSets(ExerciseSetEntry.newBuilder().setReps(5).build())
                        .build())
                .build();

        service.replaceWorkoutExercises(request);

        verify(workoutExerciseRepository).deleteAll(currentTree);
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(workoutExerciseRepository).saveAll(captor.capture());
        List<WorkoutExercise> savedTree = (List<WorkoutExercise>) captor.getValue();
        assertThat(savedTree).hasSize(1);
        assertThat(savedTree.getFirst().getExercise()).isSameAs(deadlift);
        assertThat(savedTree.getFirst().getExerciseSets()).hasSize(1);
    }

    @Test
    void replaceWorkoutExercises_throwsAndDoesNotWriteWhenRecordedDataExists() {
        Workout workout = new Workout();
        workout.setId(1L);
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout));
        when(workoutExerciseRepository.findByWorkoutIdWithExerciseSetsAndExercise(1L)).thenReturn(List.of());

        Exercise squat = new Exercise();
        squat.setId(20L);
        squat.setName("Squat");
        WorkoutExercise workoutExercise = new WorkoutExercise();
        workoutExercise.setExercise(squat);
        ExerciseSet exerciseSet = new ExerciseSet();
        exerciseSet.setWorkoutExercise(workoutExercise);
        exerciseSet.setSetNumber(2);
        SetLog recordedSetLog = new SetLog();
        recordedSetLog.setExerciseSet(exerciseSet);
        when(setLogRepository.findByWorkoutId(1L)).thenReturn(List.of(recordedSetLog));

        ReplaceWorkoutExercisesRequest request = ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(1L)
                .build();

        assertThatThrownBy(() -> service.replaceWorkoutExercises(request))
                .isInstanceOf(WorkoutExerciseHasRecordedDataException.class)
                .hasMessageContaining("Squat")
                .hasMessageContaining("set 2");
        verify(workoutExerciseRepository, never()).deleteAll(anyIterable());
        verify(workoutExerciseRepository, never()).saveAll(anyIterable());
    }

    @Test
    void replaceWorkoutExercises_throwsWhenWorkoutMissing() {
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        ReplaceWorkoutExercisesRequest request = ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(99L)
                .build();

        assertThatThrownBy(() -> service.replaceWorkoutExercises(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workoutExerciseRepository, never()).deleteAll(anyIterable());
    }
}
