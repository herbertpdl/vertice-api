package com.vertice.api.plan.workout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, Long> {

    List<WorkoutExercise> findByWorkoutId(Long workoutId);
}
