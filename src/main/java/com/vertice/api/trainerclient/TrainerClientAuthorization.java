package com.vertice.api.trainerclient;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may call each {@code TrainerClientService} RPC (P1-01). Every RPC names a trainer, and a
 * TRAINER only ever names themself; ADMIN may name anyone. CLIENTs get nothing here (no RPC returns
 * a client's own trainer yet), and service identities are refused by {@code requireUser()} before
 * these rules run. The full matrix is in CLAUDE.md.
 *
 * <p>The decision needs only the caller and the requested {@code trainer_id} (plus, for ending a
 * relationship, whether that link is active), so it is made before the service loads anything:
 * asking about another trainer's roster says nothing about it.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainerClientAuthorization {

    private final TrainerClientRepository trainerClientRepository;

    /** A TRAINER adds clients to their own roster; ADMIN to any trainer's. */
    public void checkCreateClientForTrainer(CallerIdentity caller, long trainerId) {
        requireTrainerAsThemselfOrAdmin(caller, trainerId, "create clients");
    }

    /** A TRAINER lists their own roster; ADMIN any trainer's. */
    public void checkListClientsForTrainer(CallerIdentity caller, long trainerId) {
        requireTrainerAsThemselfOrAdmin(caller, trainerId, "list a trainer's clients");
    }

    /** The trainer asking about their own roster, or ADMIN. */
    public void checkIsTrainersClient(CallerIdentity caller, long trainerId) {
        requireTrainerAsThemselfOrAdmin(caller, trainerId, "check a trainer's clients");
    }

    /**
     * A TRAINER ends a relationship on their own active roster; ADMIN any. To the trainer a client
     * who isn't theirs and one who doesn't exist are the same {@code PERMISSION_DENIED}.
     */
    public void checkEndTrainerClientRelationship(CallerIdentity caller, long trainerId, long clientId) {
        requireTrainerAsThemselfOrAdmin(caller, trainerId, "end a trainer-client relationship");
        if (caller.role() == Role.TRAINER
                && !trainerClientRepository.existsByTrainerIdAndClientIdAndEndedAtIsNull(trainerId, clientId)) {
            throw PermissionDeniedException.noAccess("client", clientId);
        }
    }

    private static void requireTrainerAsThemselfOrAdmin(CallerIdentity caller, long trainerId, String action) {
        caller.requireRole(action, Role.TRAINER, Role.ADMIN);
        if (caller.role() == Role.TRAINER && trainerId != caller.userId()) {
            throw new PermissionDeniedException("trainer_id must be your own id");
        }
    }
}
