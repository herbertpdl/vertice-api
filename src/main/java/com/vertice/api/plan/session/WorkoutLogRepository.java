package com.vertice.api.plan.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {

    Optional<WorkoutLog> findByWorkoutIdAndClientIdAndWeekStartDateAndStatus(
            Long workoutId, Long clientId, LocalDate weekStartDate, WorkoutLogStatus status);

    List<WorkoutLog> findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDateAndStatusNot(
            Long clientId, Long trainingPlanId, LocalDate weekStartDate, WorkoutLogStatus status);

    Optional<WorkoutLog> findFirstByClientIdAndWorkoutIdAndStatusOrderByCompletedAtDesc(
            Long clientId, Long workoutId, WorkoutLogStatus status);

    /**
     * Sessions left in progress from weeks before {@code weekStartDate} (the weekly rollover),
     * locked: concurrent passes (the job and a client's lazy pass, two devices) wait for each other,
     * and Postgres re-checks the status after the wait, so a row another pass already closed drops
     * out instead of failing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WorkoutLog> findByStatusAndWeekStartDateBefore(WorkoutLogStatus status, LocalDate weekStartDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WorkoutLog> findByClientIdAndStatusAndWeekStartDateBefore(Long clientId, WorkoutLogStatus status, LocalDate weekStartDate);

    /** One session, locked until commit: completing and discarding it can't interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkoutLog> findWithLockById(Long id);

    boolean existsByWorkoutId(Long workoutId);

    boolean existsByWorkout_TrainingPlan_Id(Long trainingPlanId);
}
