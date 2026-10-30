package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SetLogRepository extends JpaRepository<SetLog, Long> {

    List<SetLog> findByWorkoutLogId(Long workoutLogId);

    boolean existsByWorkoutLogId(Long workoutLogId);

    /** When the client last recorded a set in the session (the time an automatic finish takes). */
    @Query("SELECT max(sl.recordedAt) FROM SetLog sl WHERE sl.workoutLog.id = :workoutLogId")
    java.util.Optional<java.time.Instant> findLastRecordedAt(@Param("workoutLogId") Long workoutLogId);

    void deleteByWorkoutLogId(Long workoutLogId);

    void deleteByWorkoutLogIdAndExerciseSetId(Long workoutLogId, Long exerciseSetId);

    Optional<SetLog> findByWorkoutLogIdAndExerciseSetId(Long workoutLogId, Long exerciseSetId);

    boolean existsByExerciseSetId(Long exerciseSetId);

    boolean existsByExerciseSet_WorkoutExercise_Id(Long workoutExerciseId);

    /**
     * Four chained association predicates is past the point a derived method name (every other
     * query in this codebase) stays readable — see {@code exercise-progress/spec.md} §2.
     */
    @Query("SELECT sl FROM SetLog sl "
            + "WHERE sl.exerciseSet.workoutExercise.exercise.id = :exerciseId "
            + "AND sl.workoutLog.client.id = :clientId "
            + "AND sl.workoutLog.status = com.vertice.api.plan.session.WorkoutLogStatus.COMPLETED "
            + "ORDER BY sl.workoutLog.weekStartDate ASC")
    List<SetLog> findCompletedSetLogsForClientAndExercise(@Param("clientId") Long clientId, @Param("exerciseId") Long exerciseId);

    /** One exercise with progress points for a client ({@link #findProgressExercises}). */
    interface ProgressExerciseRow {
        Long getId();

        String getName();
    }

    /**
     * The exercises {@link #findCompletedSetLogsForClientAndExercise} would give a weighted point
     * for, most recently trained first. {@code visibleTo} limits them to the starter set plus that
     * user's own exercises ({@code 0} = no limit), the catalog's visibility rule.
     */
    @Query("SELECT e.id AS id, e.name AS name FROM SetLog sl "
            + "JOIN sl.workoutLog wl "
            + "JOIN sl.exerciseSet es JOIN es.workoutExercise we JOIN we.exercise e "
            + "LEFT JOIN e.owner o "
            + "WHERE wl.client.id = :clientId "
            + "AND wl.status = com.vertice.api.plan.session.WorkoutLogStatus.COMPLETED "
            + "AND sl.weight IS NOT NULL "
            + "AND (:visibleTo = 0 OR o IS NULL OR o.id = :visibleTo) "
            + "GROUP BY e.id, e.name "
            + "ORDER BY max(wl.weekStartDate) DESC, e.name ASC, e.id ASC")
    List<ProgressExerciseRow> findProgressExercises(@Param("clientId") Long clientId, @Param("visibleTo") long visibleTo);

    @Query("SELECT sl FROM SetLog sl "
            + "WHERE sl.exerciseSet.workoutExercise.workout.id = :workoutId")
    List<SetLog> findByWorkoutId(@Param("workoutId") Long workoutId);
}
