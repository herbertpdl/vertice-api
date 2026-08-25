package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    public WorkoutLogResponse getOrStartWorkoutLog(GetOrStartWorkoutLogRequest request) {
        LocalDate weekStartDate = assertMonday(request.getWeekStartDate());
        WorkoutLog workoutLog = workoutLogRepository
                .findByWorkoutIdAndClientIdAndWeekStartDate(request.getWorkoutId(), request.getClientId(), weekStartDate)
                .orElseGet(() -> startWorkoutLog(request.getWorkoutId(), request.getClientId(), weekStartDate));
        return workoutLogMapper.toResponse(workoutLog);
    }

    public SetLogResponse recordSetLog(RecordSetLogRequest request) {
        WorkoutLog workoutLog = workoutLogRepository.findById(request.getWorkoutLogId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutLog", request.getWorkoutLogId()));
        if (workoutLog.getCompletedAt() != null) {
            throw new ConstraintViolationException("workoutLogId: session already completed", Set.of());
        }
        ExerciseSet exerciseSet = exerciseSetRepository.findById(request.getExerciseSetId())
                .orElseThrow(() -> new ResourceNotFoundException("ExerciseSet", request.getExerciseSetId()));

        SetLog setLog = setLogRepository.findByWorkoutLogIdAndExerciseSetId(workoutLog.getId(), exerciseSet.getId())
                .orElseGet(SetLog::new);
        setLog.setWorkoutLog(workoutLog);
        setLog.setExerciseSet(exerciseSet);
        setLog.setWeight(ProtoDecimals.stringToDecimal(request.getWeight()));
        setLog.setReps(request.getReps());
        setLog.setRecordedAt(Instant.now());
        return setLogMapper.toResponse(setLogRepository.save(setLog));
    }

    public WorkoutLogResponse completeWorkoutLog(Long id) {
        WorkoutLog workoutLog = workoutLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutLog", id));
        if (workoutLog.getCompletedAt() == null) {
            workoutLog.setCompletedAt(Instant.now());
            workoutLog = workoutLogRepository.save(workoutLog);
        }
        return workoutLogMapper.toResponse(workoutLog);
    }

    @Transactional(readOnly = true)
    public List<WorkoutLogResponse> listWorkoutLogs(Long clientId, Long trainingPlanId, String weekStartDate) {
        LocalDate date = assertMonday(weekStartDate);
        return workoutLogRepository.findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDate(clientId, trainingPlanId, date)
                .stream()
                .map(workoutLogMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SetLogResponse> getLastSetLogs(Long clientId, Long workoutId) {
        return workoutLogRepository
                .findFirstByClientIdAndWorkoutIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(clientId, workoutId)
                .map(log -> setLogRepository.findByWorkoutLogId(log.getId()).stream().map(setLogMapper::toResponse).toList())
                .orElse(List.of());
    }

    private WorkoutLog startWorkoutLog(Long workoutId, Long clientId, LocalDate weekStartDate) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout", workoutId));
        User client = userRepository.findById(clientId)
                .filter(user -> user.getRole() == Role.CLIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        WorkoutLog workoutLog = new WorkoutLog();
        workoutLog.setWorkout(workout);
        workoutLog.setClient(client);
        workoutLog.setWeekStartDate(weekStartDate);
        workoutLog.setStartedAt(Instant.now());
        return workoutLogRepository.save(workoutLog);
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
