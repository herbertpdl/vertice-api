package com.vertice.api.plan.workout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByTrainingPlanId(Long trainingPlanId);
}
