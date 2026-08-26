package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutFeedbackRepository extends JpaRepository<WorkoutFeedback, Long> {

    List<WorkoutFeedback> findByWorkoutLog_Workout_TrainingPlan_TrainerId(Long trainerId);
}
