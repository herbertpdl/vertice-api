package com.vertice.api.plan;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * The authorization rules' only reads: given an id anywhere in the plan tree, the plan's trainer
 * and client, in one query and without loading entities. Empty when the id doesn't exist, which the
 * rules treat exactly like "not yours" (see {@link PlanAccess}).
 */
public interface PlanAccessRepository extends Repository<TrainingPlan, Long> {

    @Query("SELECT new com.vertice.api.plan.PlanParties(p.trainer.id, p.client.id) "
            + "FROM TrainingPlan p WHERE p.id = :id")
    Optional<PlanParties> findByPlanId(@Param("id") Long id);

    @Query("SELECT new com.vertice.api.plan.PlanParties(p.trainer.id, p.client.id) "
            + "FROM Workout w JOIN w.trainingPlan p WHERE w.id = :id")
    Optional<PlanParties> findByWorkoutId(@Param("id") Long id);

    @Query("SELECT new com.vertice.api.plan.PlanParties(p.trainer.id, p.client.id) "
            + "FROM WorkoutExercise we JOIN we.workout w JOIN w.trainingPlan p WHERE we.id = :id")
    Optional<PlanParties> findByWorkoutExerciseId(@Param("id") Long id);

    @Query("SELECT new com.vertice.api.plan.PlanParties(p.trainer.id, p.client.id) "
            + "FROM ExerciseSet es JOIN es.workoutExercise we JOIN we.workout w JOIN w.trainingPlan p WHERE es.id = :id")
    Optional<PlanParties> findByExerciseSetId(@Param("id") Long id);

    /** The client is the log's own, not the plan's: the log is what a client owns. */
    @Query("SELECT new com.vertice.api.plan.PlanParties(p.trainer.id, l.client.id) "
            + "FROM WorkoutLog l JOIN l.workout w JOIN w.trainingPlan p WHERE l.id = :id")
    Optional<PlanParties> findByWorkoutLogId(@Param("id") Long id);
}
