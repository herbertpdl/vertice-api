package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;

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

    /** Sessions left in progress from weeks before {@code weekStartDate} (the weekly rollover). */
    List<WorkoutLog> findByStatusAndWeekStartDateBefore(WorkoutLogStatus status, LocalDate weekStartDate);

    List<WorkoutLog> findByClientIdAndStatusAndWeekStartDateBefore(Long clientId, WorkoutLogStatus status, LocalDate weekStartDate);

    boolean existsByWorkoutId(Long workoutId);

    boolean existsByWorkout_TrainingPlan_Id(Long trainingPlanId);
}
