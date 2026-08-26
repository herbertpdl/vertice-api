package com.vertice.api.plan.workout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {

    List<ExerciseSet> findByWorkoutExerciseId(Long workoutExerciseId);
}
