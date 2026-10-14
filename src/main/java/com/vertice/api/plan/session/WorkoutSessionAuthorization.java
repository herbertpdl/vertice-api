package com.vertice.api.plan.session;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.plan.PlanAccess;
import com.vertice.api.plan.PlanAccessRepository;
import com.vertice.api.trainerclient.TrainerClientRepository;
import com.vertice.api.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may call the {@code WorkoutSessionService} and {@code WorkoutFeedbackService} RPCs (P1-01).
 * A log belongs to its client: only they (or ADMIN) write into it, complete it or give feedback
 * on it. The plan's trainer reads the logs of their plans, and may open this week's log for their
 * client (vertice-bff's trainer view of a workout session does). A {@code client_id} in a request
 * must be a CLIENT caller's own. Rules and error shape are {@link PlanAccess}'s; the full matrix
 * is in CLAUDE.md.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutSessionAuthorization {

    private final PlanAccessRepository planAccessRepository;
    private final TrainerClientRepository trainerClientRepository;

    public void checkGetOrStartWorkoutLog(CallerIdentity caller, long workoutId, long clientId) {
        PlanAccess.ownId(caller, Role.CLIENT, clientId, "client_id");
        PlanAccess.requireTrainerOrClient(caller, "workout", workoutId, () -> planAccessRepository.findByWorkoutId(workoutId));
    }

    public void checkGetWorkoutLog(CallerIdentity caller, long workoutLogId) {
        PlanAccess.requireTrainerOrClient(caller, "workout log", workoutLogId,
                () -> planAccessRepository.findByWorkoutLogId(workoutLogId));
    }

    public void checkRecordSetLog(CallerIdentity caller, long workoutLogId) {
        PlanAccess.requireClient(caller, "record sets", "workout log", workoutLogId,
                () -> planAccessRepository.findByWorkoutLogId(workoutLogId));
    }

    public void checkCompleteWorkoutLog(CallerIdentity caller, long workoutLogId) {
        PlanAccess.requireClient(caller, "complete workout sessions", "workout log", workoutLogId,
                () -> planAccessRepository.findByWorkoutLogId(workoutLogId));
    }

    public void checkListWorkoutLogs(CallerIdentity caller, long clientId, long trainingPlanId) {
        PlanAccess.ownId(caller, Role.CLIENT, clientId, "client_id");
        PlanAccess.requireTrainerOrClient(caller, "training plan", trainingPlanId,
                () -> planAccessRepository.findByPlanId(trainingPlanId));
    }

    public void checkGetLastSetLogs(CallerIdentity caller, long clientId, long workoutId) {
        PlanAccess.ownId(caller, Role.CLIENT, clientId, "client_id");
        PlanAccess.requireTrainerOrClient(caller, "workout", workoutId, () -> planAccessRepository.findByWorkoutId(workoutId));
    }

    /**
     * Progress spans every plan the client ever had, so it isn't a plan-tree rule: the client
     * themself, ADMIN, or the client's active trainer (vertice-bff checks the same roster link).
     */
    public void checkGetExerciseProgress(CallerIdentity caller, long clientId) {
        PlanAccess.ownId(caller, Role.CLIENT, clientId, "client_id");
        if (caller.role() == Role.TRAINER && !trainerClientRepository
                .existsByTrainerIdAndClientIdAndClientRoleAndEndedAtIsNull(caller.userId(), clientId, Role.CLIENT)) {
            throw PermissionDeniedException.noAccess("client", clientId);
        }
    }

    public void checkSubmitWorkoutFeedback(CallerIdentity caller, long workoutLogId) {
        PlanAccess.requireClient(caller, "submit workout feedback", "workout log", workoutLogId,
                () -> planAccessRepository.findByWorkoutLogId(workoutLogId));
    }

    /** A TRAINER lists feedback on their own plans; ADMIN names the trainer. */
    public void checkListWorkoutFeedback(CallerIdentity caller, long trainerId) {
        caller.requireRole("list workout feedback", Role.TRAINER, Role.ADMIN);
        PlanAccess.ownId(caller, Role.TRAINER, trainerId, "trainer_id");
    }
}
