package com.vertice.api.plan.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, Long> {

    List<WorkoutExercise> findByWorkoutId(Long workoutId);

    /**
     * Used by {@code WorkoutService#replaceWorkoutExercises} to load the current tree in one
     * batched fetch instead of {@code cloneWorkout}'s lazy per-association walk — this path
     * deletes the returned rows afterward, so leaving {@code exerciseSets} lazy would otherwise
     * cost one extra SELECT per {@link WorkoutExercise} when the cascade removes them (see
     * docs/specs/create-workout-with-exercises/spec.md §0/F8).
     */
    @Query("SELECT DISTINCT we FROM WorkoutExercise we "
            + "LEFT JOIN FETCH we.exerciseSets "
            + "LEFT JOIN FETCH we.exercise "
            + "WHERE we.workout.id = :workoutId")
    List<WorkoutExercise> findByWorkoutIdWithExerciseSetsAndExercise(@Param("workoutId") Long workoutId);
}
