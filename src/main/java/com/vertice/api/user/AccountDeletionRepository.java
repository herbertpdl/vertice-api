package com.vertice.api.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * The statements {@link AccountDeletionService} runs, in the order it runs them: children before
 * parents, so no foreign key is ever violated. Native, because they reach across aggregates that
 * have no JPA association to cascade along, and each is one set-based statement however much data
 * the account has.
 */
public interface AccountDeletionRepository extends Repository<User, Long> {

    /** Row-locked, so two deletions of the same account serialise and the second sees the first's result. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM workout_feedback
            WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE client_id = :userId)
            """)
    int deleteFeedbackOfClient(Long userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM set_logs
            WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE client_id = :userId)
            """)
    int deleteSetLogsOfClient(Long userId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM workout_logs WHERE client_id = :userId")
    int deleteWorkoutLogsOfClient(Long userId);

    /** The plans written for the user, whole tree (sets, workout exercises, workouts, plans). */
    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM exercise_sets
            WHERE workout_exercise_id IN (
                SELECT we.id
                FROM workout_exercises we
                JOIN workouts w ON w.id = we.workout_id
                JOIN training_plans p ON p.id = w.training_plan_id
                WHERE p.client_id = :userId)
            """)
    int deleteExerciseSetsOfPlansForClient(Long userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM workout_exercises
            WHERE workout_id IN (
                SELECT w.id
                FROM workouts w
                JOIN training_plans p ON p.id = w.training_plan_id
                WHERE p.client_id = :userId)
            """)
    int deleteWorkoutExercisesOfPlansForClient(Long userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM workouts
            WHERE training_plan_id IN (SELECT id FROM training_plans WHERE client_id = :userId)
            """)
    int deleteWorkoutsOfPlansForClient(Long userId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM training_plans WHERE client_id = :userId")
    int deletePlansForClient(Long userId);

    /** Private exercises no workout uses; their muscle-group rows cascade. Used ones stay, owned by the anonymised row. */
    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM exercises e
            WHERE e.owner_id = :userId
              AND NOT EXISTS (SELECT 1 FROM workout_exercises we WHERE we.exercise_id = e.id)
            """)
    int deleteUnusedExercisesOwnedBy(Long userId);

    /** Both sides: as the client, and as the trainer (none are active by then, see the service). */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE trainer_clients
            SET ended_at = :endedAt
            WHERE (client_id = :userId OR trainer_id = :userId)
              AND ended_at IS NULL
            """)
    int endRelationships(Long userId, Instant endedAt);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM password_setup_tokens WHERE user_id = :userId")
    int deletePasswordSetupTokens(Long userId);
}
