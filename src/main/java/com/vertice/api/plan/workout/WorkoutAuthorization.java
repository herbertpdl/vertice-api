package com.vertice.api.plan.workout;

import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.plan.PlanAccess;
import com.vertice.api.plan.PlanAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may call the {@code WorkoutService}, {@code WorkoutExerciseService} and
 * {@code ExerciseSetService} RPCs (P1-01): everything under a plan follows the plan. Its trainer
 * reads and writes, its client only reads, ADMIN bypasses. Rules and error shape are
 * {@link PlanAccess}'s; the full matrix is in CLAUDE.md. Which exercises may go into a workout is
 * the services' check (spec exercise-starter-catalog PR9), since it needs the loaded rows.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutAuthorization {

    private final PlanAccessRepository planAccessRepository;

    /** ListWorkouts: read access to the plan. */
    public void checkReadPlan(CallerIdentity caller, Long trainingPlanId) {
        PlanAccess.requireTrainerOrClient(caller, "training plan", trainingPlanId,
                () -> planAccessRepository.findByPlanId(trainingPlanId));
    }

    /** CreateWorkout, CreateWorkoutWithExercises, CloneWorkout's target: the plan's trainer. */
    public void checkWritePlan(CallerIdentity caller, Long trainingPlanId) {
        PlanAccess.requireTrainer(caller, "change workouts", "training plan", trainingPlanId,
                () -> planAccessRepository.findByPlanId(trainingPlanId));
    }

    /** GetWorkout, ListWorkoutExercises. */
    public void checkReadWorkout(CallerIdentity caller, Long workoutId) {
        PlanAccess.requireTrainerOrClient(caller, "workout", workoutId,
                () -> planAccessRepository.findByWorkoutId(workoutId));
    }

    /**
     * UpdateWorkout, DeleteWorkout, ReplaceWorkoutExercises, CreateWorkoutExercise, CloneWorkout's
     * source (copying a workout is the owner's call, not its client's).
     */
    public void checkWriteWorkout(CallerIdentity caller, Long workoutId) {
        PlanAccess.requireTrainer(caller, "change workouts", "workout", workoutId,
                () -> planAccessRepository.findByWorkoutId(workoutId));
    }

    /** CloneWorkout: the source workout and the target plan must both be the caller's. */
    public void checkCloneWorkout(CallerIdentity caller, Long sourceWorkoutId, Long targetTrainingPlanId) {
        checkWriteWorkout(caller, sourceWorkoutId);
        checkWritePlan(caller, targetTrainingPlanId);
    }

    /** GetWorkoutExercise, ListExerciseSets. */
    public void checkReadWorkoutExercise(CallerIdentity caller, Long workoutExerciseId) {
        PlanAccess.requireTrainerOrClient(caller, "workout exercise", workoutExerciseId,
                () -> planAccessRepository.findByWorkoutExerciseId(workoutExerciseId));
    }

    /** UpdateWorkoutExercise, DeleteWorkoutExercise, CreateExerciseSet. */
    public void checkWriteWorkoutExercise(CallerIdentity caller, Long workoutExerciseId) {
        PlanAccess.requireTrainer(caller, "change workouts", "workout exercise", workoutExerciseId,
                () -> planAccessRepository.findByWorkoutExerciseId(workoutExerciseId));
    }

    /** GetExerciseSet. */
    public void checkReadExerciseSet(CallerIdentity caller, Long exerciseSetId) {
        PlanAccess.requireTrainerOrClient(caller, "exercise set", exerciseSetId,
                () -> planAccessRepository.findByExerciseSetId(exerciseSetId));
    }

    /** UpdateExerciseSet, DeleteExerciseSet. */
    public void checkWriteExerciseSet(CallerIdentity caller, Long exerciseSetId) {
        PlanAccess.requireTrainer(caller, "change workouts", "exercise set", exerciseSetId,
                () -> planAccessRepository.findByExerciseSetId(exerciseSetId));
    }
}
