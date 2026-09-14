package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SetLogRepository extends JpaRepository<SetLog, Long> {

    List<SetLog> findByWorkoutLogId(Long workoutLogId);

    Optional<SetLog> findByWorkoutLogIdAndExerciseSetId(Long workoutLogId, Long exerciseSetId);

    /**
     * Four chained association predicates is past the point a derived method name (every other
     * query in this codebase) stays readable — see {@code exercise-progress/spec.md} §2.
     */
    @Query("SELECT sl FROM SetLog sl "
            + "WHERE sl.exerciseSet.workoutExercise.exercise.id = :exerciseId "
            + "AND sl.workoutLog.client.id = :clientId "
            + "AND sl.workoutLog.completedAt IS NOT NULL "
            + "ORDER BY sl.workoutLog.weekStartDate ASC")
    List<SetLog> findCompletedSetLogsForClientAndExercise(@Param("clientId") Long clientId, @Param("exerciseId") Long exerciseId);

    @Query("SELECT sl FROM SetLog sl "
            + "WHERE sl.exerciseSet.workoutExercise.workout.id = :workoutId")
    List<SetLog> findByWorkoutId(@Param("workoutId") Long workoutId);
}
