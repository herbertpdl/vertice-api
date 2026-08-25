package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {

    Optional<WorkoutLog> findByWorkoutIdAndClientIdAndWeekStartDate(Long workoutId, Long clientId, LocalDate weekStartDate);

    List<WorkoutLog> findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDate(Long clientId, Long trainingPlanId, LocalDate weekStartDate);

    Optional<WorkoutLog> findFirstByClientIdAndWorkoutIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long clientId, Long workoutId);
}
