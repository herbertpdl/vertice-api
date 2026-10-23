package com.vertice.api.plan.session;

import com.vertice.api.common.exception.BusinessRuleViolationException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.BusinessDates;
import com.vertice.api.common.time.DbInstants;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressResponse;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ProgressPoint;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.RestartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.SetLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.grpc.ProtoDates;
import com.vertice.api.grpc.ProtoDecimals;
import com.vertice.api.plan.workout.ExerciseSet;
import com.vertice.api.plan.workout.ExerciseSetRepository;
import com.vertice.api.plan.workout.Workout;
import com.vertice.api.plan.workout.WorkoutRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutSessionService {

    private final WorkoutLogRepository workoutLogRepository;
    private final SetLogRepository setLogRepository;
    private final WorkoutLogMapper workoutLogMapper;
    private final SetLogMapper setLogMapper;
    private final WorkoutRepository workoutRepository;
    private final UserRepository userRepository;
    private final ExerciseSetRepository exerciseSetRepository;
    private final Clock clock;

    /**
     * The week's session in progress, else its completed one, else a new one. Sessions the client
     * left in progress in earlier weeks are rolled over first, so the job's lag never shows.
     */
    public WorkoutLogResponse getOrStartWorkoutLog(GetOrStartWorkoutLogRequest request) {
        LocalDate weekStartDate = assertMonday(request.getWeekStartDate());
        rollOverClient(request.getClientId());
        Optional<WorkoutLog> existing = findInWeek(request.getWorkoutId(), request.getClientId(), weekStartDate, WorkoutLogStatus.IN_PROGRESS)
                .or(() -> findInWeek(request.getWorkoutId(), request.getClientId(), weekStartDate, WorkoutLogStatus.COMPLETED));
        if (existing.isPresent()) {
            // Checked here too so a log created before this rule existed isn't handed back.
            assertWorkoutAssignedToClient(existing.get().getWorkout(), request.getClientId());
            return workoutLogMapper.toResponse(existing.get());
        }
        return workoutLogMapper.toResponse(startWorkoutLog(request.getWorkoutId(), request.getClientId(), weekStartDate));
    }

    /**
     * A redo of the week's completed workout: a new session next to the completed one (which stays
     * until the redo is finished), or the session already in progress.
     */
    public WorkoutLogResponse restartWorkoutLog(RestartWorkoutLogRequest request) {
        LocalDate weekStartDate = assertMonday(request.getWeekStartDate());
        rollOverClient(request.getClientId());
        Optional<WorkoutLog> running = findInWeek(request.getWorkoutId(), request.getClientId(), weekStartDate, WorkoutLogStatus.IN_PROGRESS);
        if (running.isPresent()) {
            assertWorkoutAssignedToClient(running.get().getWorkout(), request.getClientId());
            return workoutLogMapper.toResponse(running.get());
        }
        return workoutLogMapper.toResponse(startWorkoutLog(request.getWorkoutId(), request.getClientId(), weekStartDate));
    }

    /** Deletes a session in progress with its set logs; a completed one can't be discarded. */
    public void discardWorkoutLog(Long id) {
        WorkoutLog workoutLog = lockWorkoutLogOrThrow(id);
        assertInProgress(workoutLog);
        setLogRepository.deleteByWorkoutLogId(workoutLog.getId());
        workoutLogRepository.delete(workoutLog);
    }

    /** Unticks a set of a session in progress; unticking a set that wasn't ticked is a no-op. */
    public void deleteSetLog(Long workoutLogId, Long exerciseSetId) {
        WorkoutLog workoutLog = lockWorkoutLogOrThrow(workoutLogId);
        assertInProgress(workoutLog);
        setLogRepository.deleteByWorkoutLogIdAndExerciseSetId(workoutLog.getId(), exerciseSetId);
    }

    @Transactional(readOnly = true)
    public List<SetLogResponse> listSetLogs(Long workoutLogId) {
        return setLogRepository.findByWorkoutLogId(findWorkoutLogOrThrow(workoutLogId).getId()).stream()
                .map(setLogMapper::toResponse)
                .toList();
    }

    /**
     * Finishes every session left in progress before {@code weekStartDate}: one with at least one
     * set becomes completed (an automatic finish, which replaces the week's earlier completed
     * session like any finish); one with none is deleted. Returns how many sessions it touched.
     */
    public int rollOver(LocalDate weekStartDate) {
        return rollOver(workoutLogRepository.findByStatusAndWeekStartDateBeforeOrderByIdAsc(WorkoutLogStatus.IN_PROGRESS, weekStartDate));
    }

    @Transactional(readOnly = true)
    public WorkoutLogResponse getWorkoutLog(Long id) {
        return workoutLogMapper.toResponse(findWorkoutLogOrThrow(id));
    }

    public SetLogResponse recordSetLog(RecordSetLogRequest request) {
        // Locked like complete, discard and the rollover: a tick waits for them instead of racing.
        WorkoutLog workoutLog = lockWorkoutLogOrThrow(request.getWorkoutLogId());
        if (!workoutLog.isInProgress()) {
            throw new ConstraintViolationException("workoutLogId: session already completed", Set.of());
        }
        ExerciseSet exerciseSet = exerciseSetRepository.findById(request.getExerciseSetId())
                .orElseThrow(() -> new ResourceNotFoundException("ExerciseSet", request.getExerciseSetId()));
        Long setWorkoutId = exerciseSet.getWorkoutExercise().getWorkout().getId();
        if (!setWorkoutId.equals(workoutLog.getWorkout().getId())) {
            throw new BusinessRuleViolationException("ExerciseSet %d does not belong to the workout of WorkoutLog %d"
                    .formatted(exerciseSet.getId(), workoutLog.getId()));
        }

        SetLog setLog = setLogRepository.findByWorkoutLogIdAndExerciseSetId(workoutLog.getId(), exerciseSet.getId())
                .orElseGet(SetLog::new);
        setLog.setWorkoutLog(workoutLog);
        setLog.setExerciseSet(exerciseSet);
        setLog.setWeight(ProtoDecimals.stringToWeight(request.getWeight()));
        setLog.setReps(request.getReps());
        setLog.setRecordedAt(DbInstants.now());
        return setLogMapper.toResponse(setLogRepository.save(setLog));
    }

    /** Idempotent: completing a finished session returns it unchanged. */
    public WorkoutLogResponse completeWorkoutLog(Long id) {
        WorkoutLog workoutLog = lockWorkoutLogOrThrow(id);
        if (workoutLog.isInProgress()) {
            workoutLog = finish(workoutLog, CompletionKind.CLIENT);
        }
        return workoutLogMapper.toResponse(workoutLog);
    }

    public List<WorkoutLogResponse> listWorkoutLogs(Long clientId, Long trainingPlanId, String weekStartDate) {
        LocalDate date = assertMonday(weekStartDate);
        rollOverClient(clientId);
        return workoutLogRepository
                .findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDateAndStatusNot(clientId, trainingPlanId, date, WorkoutLogStatus.SUPERSEDED)
                .stream()
                .map(workoutLogMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SetLogResponse> getLastSetLogs(Long clientId, Long workoutId) {
        return workoutLogRepository
                .findFirstByClientIdAndWorkoutIdAndStatusOrderByWeekStartDateDescCompletedAtDesc(clientId, workoutId, WorkoutLogStatus.COMPLETED)
                .map(log -> setLogRepository.findByWorkoutLogId(log.getId()).stream().map(setLogMapper::toResponse).toList())
                .orElse(List.of());
    }

    /**
     * One point per ISO week, {@code MAX(weight)} that week — the natural "progress" signal for
     * strength work, per {@code exercise-progress/spec.md} §0. {@link TreeMap} keeps the merge
     * sorted oldest-to-newest by key regardless of the query's own row order.
     */
    @Transactional(readOnly = true)
    public GetExerciseProgressResponse getExerciseProgress(Long clientId, Long exerciseId) {
        Map<LocalDate, BigDecimal> maxWeightByWeek = new TreeMap<>();
        for (SetLog setLog : setLogRepository.findCompletedSetLogsForClientAndExercise(clientId, exerciseId)) {
            if (setLog.getWeight() == null) {
                continue;
            }
            maxWeightByWeek.merge(setLog.getWorkoutLog().getWeekStartDate(), setLog.getWeight(), BigDecimal::max);
        }

        GetExerciseProgressResponse.Builder response = GetExerciseProgressResponse.newBuilder();
        maxWeightByWeek.forEach((week, weight) -> response.addPoints(ProgressPoint.newBuilder()
                .setWeekStartDate(ProtoDates.dateToString(week))
                .setWeight(ProtoDecimals.decimalToString(weight))
                .build()));
        return response.build();
    }

    /**
     * New sessions only in the current week (R20 redoes a workout "finished this week"): a session
     * created in an earlier week would be closed by the next rollover pass mid-workout.
     */
    private WorkoutLog startWorkoutLog(Long workoutId, Long clientId, LocalDate weekStartDate) {
        if (weekStartDate.isBefore(BusinessDates.currentWeekStart(clock))) {
            throw new BusinessRuleViolationException("A workout session can only be started in the current week");
        }
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout", workoutId));
        User client = userRepository.findById(clientId)
                .filter(user -> !user.isDeleted())
                .filter(user -> user.getRole() == Role.CLIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
        assertWorkoutAssignedToClient(workout, clientId);

        WorkoutLog workoutLog = new WorkoutLog();
        workoutLog.setWorkout(workout);
        workoutLog.setClient(client);
        workoutLog.setWeekStartDate(weekStartDate);
        workoutLog.setStartedAt(DbInstants.now());
        return workoutLogRepository.save(workoutLog);
    }

    private Optional<WorkoutLog> findInWeek(Long workoutId, Long clientId, LocalDate weekStartDate, WorkoutLogStatus status) {
        return workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDateAndStatus(workoutId, clientId, weekStartDate, status);
    }

    /** The client's own leftovers from earlier weeks, before answering about the current one. */
    private void rollOverClient(Long clientId) {
        rollOver(workoutLogRepository.findByClientIdAndStatusAndWeekStartDateBeforeOrderByIdAsc(
                clientId, WorkoutLogStatus.IN_PROGRESS, BusinessDates.currentWeekStart(clock)));
    }

    private int rollOver(List<WorkoutLog> stale) {
        for (WorkoutLog workoutLog : stale) {
            if (setLogRepository.existsByWorkoutLogId(workoutLog.getId())) {
                finish(workoutLog, CompletionKind.AUTO);
            } else {
                workoutLogRepository.delete(workoutLog);
            }
        }
        return stale.size();
    }

    /**
     * Completes a session, superseding the week's earlier completed one first (flushed, so the
     * one-completed-per-week index never sees two).
     */
    private WorkoutLog finish(WorkoutLog workoutLog, CompletionKind kind) {
        findInWeek(workoutLog.getWorkout().getId(), workoutLog.getClient().getId(), workoutLog.getWeekStartDate(), WorkoutLogStatus.COMPLETED)
                .ifPresent(previous -> {
                    previous.setStatus(WorkoutLogStatus.SUPERSEDED);
                    workoutLogRepository.saveAndFlush(previous);
                });
        // An automatic finish happens days later: it takes the time the client last trained in it.
        Instant at = kind == CompletionKind.AUTO
                ? setLogRepository.findLastRecordedAt(workoutLog.getId()).orElse(workoutLog.getStartedAt())
                : DbInstants.now();
        workoutLog.complete(at, kind);
        return workoutLogRepository.saveAndFlush(workoutLog);
    }

    private static void assertInProgress(WorkoutLog workoutLog) {
        if (!workoutLog.isInProgress()) {
            throw new BusinessRuleViolationException("WorkoutLog %d is already completed".formatted(workoutLog.getId()));
        }
    }

    private WorkoutLog lockWorkoutLogOrThrow(Long id) {
        return workoutLogRepository.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutLog", id));
    }

    private WorkoutLog findWorkoutLogOrThrow(Long id) {
        return workoutLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutLog", id));
    }

    /** A client may only log workouts from their own training plan. */
    private void assertWorkoutAssignedToClient(Workout workout, Long clientId) {
        if (!workout.getTrainingPlan().getClient().getId().equals(clientId)) {
            throw new BusinessRuleViolationException("Workout %d is not assigned to client %d"
                    .formatted(workout.getId(), clientId));
        }
    }

    /**
     * "Week" is identified by its Monday, per the plan-mode decision this spec implements — a
     * cross-field-style business rule that can't be a Bean Validation annotation on the request,
     * same reasoning {@code TrainingPlanService#assertDateRangeValid} already documents.
     */
    private LocalDate assertMonday(String weekStartDate) {
        LocalDate date = ProtoDates.stringToDate(weekStartDate);
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ConstraintViolationException("weekStartDate must be a Monday", Set.of());
        }
        return date;
    }
}
