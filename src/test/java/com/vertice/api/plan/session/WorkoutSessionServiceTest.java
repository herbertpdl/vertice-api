package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.plan.workout.ExerciseSet;
import com.vertice.api.plan.workout.ExerciseSetRepository;
import com.vertice.api.plan.workout.Workout;
import com.vertice.api.plan.workout.WorkoutRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutSessionServiceTest {

    @Mock
    private WorkoutLogRepository workoutLogRepository;

    @Mock
    private SetLogRepository setLogRepository;

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExerciseSetRepository exerciseSetRepository;

    private WorkoutSessionService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutSessionService(workoutLogRepository, setLogRepository,
                Mappers.getMapper(WorkoutLogMapper.class), Mappers.getMapper(SetLogMapper.class),
                workoutRepository, userRepository, exerciseSetRepository);
    }

    private static Workout workout(long id) {
        Workout workout = new Workout();
        workout.setId(id);
        return workout;
    }

    private static User client(long id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.CLIENT);
        return user;
    }

    private static WorkoutLog workoutLog(long id, Workout workout, User client) {
        WorkoutLog log = new WorkoutLog();
        log.setId(id);
        log.setWorkout(workout);
        log.setClient(client);
        log.setWeekStartDate(java.time.LocalDate.parse("2026-08-24"));
        log.setStartedAt(Instant.now());
        return log;
    }

    @Test
    void getOrStartWorkoutLog_createsNewLogWhenNoneExists() {
        when(workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(1L, 2L, java.time.LocalDate.parse("2026-08-24")))
                .thenReturn(Optional.empty());
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout(1L)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(client(2L)));
        when(workoutLogRepository.save(any(WorkoutLog.class))).thenAnswer(inv -> {
            WorkoutLog log = inv.getArgument(0);
            log.setId(10L);
            return log;
        });

        var request = GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(1L).setClientId(2L).setWeekStartDate("2026-08-24").build();

        var response = service.getOrStartWorkoutLog(request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getWorkoutId()).isEqualTo(1L);
        assertThat(response.getClientId()).isEqualTo(2L);
        assertThat(response.getWeekStartDate()).isEqualTo("2026-08-24");
        assertThat(response.getCompletedAt()).isEmpty();
    }

    @Test
    void getOrStartWorkoutLog_returnsExistingLogIdempotently() {
        WorkoutLog existing = workoutLog(10L, workout(1L), client(2L));
        when(workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(1L, 2L, java.time.LocalDate.parse("2026-08-24")))
                .thenReturn(Optional.of(existing));

        var request = GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(1L).setClientId(2L).setWeekStartDate("2026-08-24").build();

        var response = service.getOrStartWorkoutLog(request);

        assertThat(response.getId()).isEqualTo(10L);
        verify(workoutRepository, never()).findById(any());
        verify(workoutLogRepository, never()).save(any());
    }

    @Test
    void getOrStartWorkoutLog_throwsWhenNotMonday() {
        var request = GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(1L).setClientId(2L).setWeekStartDate("2026-08-25").build();

        assertThatThrownBy(() -> service.getOrStartWorkoutLog(request))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void getOrStartWorkoutLog_throwsWhenWorkoutMissing() {
        when(workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(99L, 2L, java.time.LocalDate.parse("2026-08-24")))
                .thenReturn(Optional.empty());
        when(workoutRepository.findById(99L)).thenReturn(Optional.empty());

        var request = GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(99L).setClientId(2L).setWeekStartDate("2026-08-24").build();

        assertThatThrownBy(() -> service.getOrStartWorkoutLog(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrStartWorkoutLog_throwsWhenClientIsNotClientRole() {
        when(workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(1L, 2L, java.time.LocalDate.parse("2026-08-24")))
                .thenReturn(Optional.empty());
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout(1L)));
        User trainer = new User();
        trainer.setId(2L);
        trainer.setRole(Role.TRAINER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(trainer));

        var request = GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(1L).setClientId(2L).setWeekStartDate("2026-08-24").build();

        assertThatThrownBy(() -> service.getOrStartWorkoutLog(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordSetLog_createsNewSetLog() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        ExerciseSet exerciseSet = new ExerciseSet();
        exerciseSet.setId(5L);

        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));
        when(exerciseSetRepository.findById(5L)).thenReturn(Optional.of(exerciseSet));
        when(setLogRepository.findByWorkoutLogIdAndExerciseSetId(10L, 5L)).thenReturn(Optional.empty());
        when(setLogRepository.save(any(SetLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(10L).setExerciseSetId(5L).setWeight("62.5").setReps(10).build();

        var response = service.recordSetLog(request);

        assertThat(response.getWorkoutLogId()).isEqualTo(10L);
        assertThat(response.getExerciseSetId()).isEqualTo(5L);
        assertThat(response.getWeight()).isEqualTo("62.5");
        assertThat(response.getReps()).isEqualTo(10);
    }

    @Test
    void recordSetLog_upsertsExistingSetLogInPlace() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        ExerciseSet exerciseSet = new ExerciseSet();
        exerciseSet.setId(5L);
        SetLog existing = new SetLog();
        existing.setId(50L);
        existing.setWorkoutLog(log);
        existing.setExerciseSet(exerciseSet);
        existing.setWeight(java.math.BigDecimal.valueOf(50));

        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));
        when(exerciseSetRepository.findById(5L)).thenReturn(Optional.of(exerciseSet));
        when(setLogRepository.findByWorkoutLogIdAndExerciseSetId(10L, 5L)).thenReturn(Optional.of(existing));
        when(setLogRepository.save(any(SetLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(10L).setExerciseSetId(5L).setWeight("65").setReps(8).build();

        var response = service.recordSetLog(request);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getWeight()).isEqualTo("65");
    }

    @Test
    void recordSetLog_throwsWhenWorkoutLogAlreadyCompleted() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        log.setCompletedAt(Instant.now());

        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));

        var request = RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(10L).setExerciseSetId(5L).setReps(8).build();

        assertThatThrownBy(() -> service.recordSetLog(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(setLogRepository, never()).save(any());
    }

    @Test
    void recordSetLog_throwsWhenExerciseSetMissing() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));
        when(exerciseSetRepository.findById(99L)).thenReturn(Optional.empty());

        var request = RecordSetLogRequest.newBuilder().setWorkoutLogId(10L).setExerciseSetId(99L).build();

        assertThatThrownBy(() -> service.recordSetLog(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completeWorkoutLog_setsCompletedAt() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));
        when(workoutLogRepository.save(any(WorkoutLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.completeWorkoutLog(10L);

        assertThat(response.getCompletedAt()).isNotEmpty();
    }

    @Test
    void completeWorkoutLog_isIdempotentWhenAlreadyCompleted() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        Instant firstCompletion = Instant.now().minusSeconds(3600);
        log.setCompletedAt(firstCompletion);
        when(workoutLogRepository.findById(10L)).thenReturn(Optional.of(log));

        var response = service.completeWorkoutLog(10L);

        assertThat(response.getCompletedAt()).isEqualTo(firstCompletion.toString());
        verify(workoutLogRepository, never()).save(any());
    }

    @Test
    void completeWorkoutLog_throwsWhenMissing() {
        when(workoutLogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeWorkoutLog(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listWorkoutLogs_returnsLogsForClientPlanAndWeek() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        when(workoutLogRepository.findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDate(
                2L, 3L, java.time.LocalDate.parse("2026-08-24")))
                .thenReturn(List.of(log));

        var responses = service.listWorkoutLogs(2L, 3L, "2026-08-24");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getClientId()).isEqualTo(2L);
    }

    @Test
    void listWorkoutLogs_throwsWhenWeekStartDateNotMonday() {
        assertThatThrownBy(() -> service.listWorkoutLogs(2L, 3L, "2026-08-25"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void getLastSetLogs_returnsSetsFromMostRecentCompletedLog() {
        WorkoutLog log = workoutLog(10L, workout(1L), client(2L));
        SetLog setLog = new SetLog();
        setLog.setId(50L);
        setLog.setWorkoutLog(log);
        setLog.setExerciseSet(new ExerciseSet());

        when(workoutLogRepository.findFirstByClientIdAndWorkoutIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(2L, 1L))
                .thenReturn(Optional.of(log));
        when(setLogRepository.findByWorkoutLogId(10L)).thenReturn(List.of(setLog));

        var responses = service.getLastSetLogs(2L, 1L);

        assertThat(responses).hasSize(1);
    }

    @Test
    void getLastSetLogs_returnsEmptyWhenNoCompletedSession() {
        when(workoutLogRepository.findFirstByClientIdAndWorkoutIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(2L, 1L))
                .thenReturn(Optional.empty());

        var responses = service.getLastSetLogs(2L, 1L);

        assertThat(responses).isEmpty();
    }

    private static SetLog completedSetLog(LocalDate weekStartDate, java.math.BigDecimal weight) {
        WorkoutLog log = new WorkoutLog();
        log.setWeekStartDate(weekStartDate);
        log.setCompletedAt(Instant.now());
        SetLog setLog = new SetLog();
        setLog.setWorkoutLog(log);
        setLog.setWeight(weight);
        return setLog;
    }

    @Test
    void getExerciseProgress_returnsOnePointPerWeekWithMaxWeight() {
        List<SetLog> setLogs = List.of(
                completedSetLog(LocalDate.parse("2026-08-03"), java.math.BigDecimal.valueOf(55)),
                completedSetLog(LocalDate.parse("2026-08-03"), java.math.BigDecimal.valueOf(60)),
                completedSetLog(LocalDate.parse("2026-08-10"), java.math.BigDecimal.valueOf(62.5)));
        when(setLogRepository.findCompletedSetLogsForClientAndExercise(2L, 10L)).thenReturn(setLogs);

        var response = service.getExerciseProgress(2L, 10L);

        assertThat(response.getPointsList()).hasSize(2);
        assertThat(response.getPoints(0).getWeekStartDate()).isEqualTo("2026-08-03");
        assertThat(response.getPoints(0).getWeight()).isEqualTo("60");
        assertThat(response.getPoints(1).getWeekStartDate()).isEqualTo("2026-08-10");
        assertThat(response.getPoints(1).getWeight()).isEqualTo("62.5");
    }

    @Test
    void getExerciseProgress_skipsSetLogsWithNoWeight() {
        SetLog durationOnly = completedSetLog(LocalDate.parse("2026-08-03"), null);
        when(setLogRepository.findCompletedSetLogsForClientAndExercise(2L, 10L)).thenReturn(List.of(durationOnly));

        var response = service.getExerciseProgress(2L, 10L);

        assertThat(response.getPointsList()).isEmpty();
    }

    @Test
    void getExerciseProgress_returnsEmptyWhenNoHistory() {
        when(setLogRepository.findCompletedSetLogsForClientAndExercise(2L, 99L)).thenReturn(List.of());

        var response = service.getExerciseProgress(2L, 99L);

        assertThat(response.getPointsList()).isEmpty();
    }
}
