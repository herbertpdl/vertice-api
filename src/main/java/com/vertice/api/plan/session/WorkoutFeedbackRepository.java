package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutFeedbackRepository extends JpaRepository<WorkoutFeedback, Long> {

    List<WorkoutFeedback> findByWorkoutLog_Workout_TrainingPlan_TrainerId(Long trainerId);

    long countByWorkoutLogId(Long workoutLogId);

    Optional<WorkoutFeedback> findByWorkoutLogIdAndClientKey(Long workoutLogId, String clientKey);
}
