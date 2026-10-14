package com.vertice.api.plan;

import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may call each {@code TrainingPlanService} RPC (P1-01): the plan's trainer acts on it, its
 * client reads it, ADMIN bypasses. Rules and error shape are {@link PlanAccess}'s; the full matrix
 * is in CLAUDE.md.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingPlanAuthorization {

    private final PlanAccessRepository planAccessRepository;

    /** The filters to list with; {@code null} = no filter. */
    public record ListScope(Long trainerId, Long clientId) {
    }

    /** A TRAINER lists their own plans, a CLIENT the plans written for them, ADMIN anything. */
    public ListScope checkListTrainingPlans(CallerIdentity caller, long trainerId, long clientId) {
        long scopedTrainerId = PlanAccess.ownId(caller, Role.TRAINER, trainerId, "trainer_id");
        long scopedClientId = PlanAccess.ownId(caller, Role.CLIENT, clientId, "client_id");
        return new ListScope(scopedTrainerId == 0 ? null : scopedTrainerId, scopedClientId == 0 ? null : scopedClientId);
    }

    public void checkGetTrainingPlan(CallerIdentity caller, Long id) {
        PlanAccess.requireTrainerOrClient(caller, "training plan", id, () -> planAccessRepository.findByPlanId(id));
    }

    /**
     * A TRAINER creates plans as themself; ADMIN names the trainer. The client must still be the
     * trainer's active client (checked by the service).
     *
     * @return the trainer the plan is created for
     */
    public long checkCreateTrainingPlan(CallerIdentity caller, long trainerId) {
        caller.requireRole("create training plans", Role.TRAINER, Role.ADMIN);
        return PlanAccess.ownId(caller, Role.TRAINER, trainerId, "trainer_id");
    }

    public void checkUpdateTrainingPlan(CallerIdentity caller, Long id) {
        PlanAccess.requireTrainer(caller, "change training plans", "training plan", id,
                () -> planAccessRepository.findByPlanId(id));
    }

    public void checkDeleteTrainingPlan(CallerIdentity caller, Long id) {
        PlanAccess.requireTrainer(caller, "delete training plans", "training plan", id,
                () -> planAccessRepository.findByPlanId(id));
    }
}
