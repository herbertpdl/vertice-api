package com.vertice.api.user;

import com.vertice.api.common.exception.BusinessRuleViolationException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.DbInstants;
import com.vertice.api.trainerclient.TrainerClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * {@code DeleteAccount} (P1-05, retention decision D5 in docs/data-inventory.md): one transaction
 * that deletes the account's personal data or anonymises it, so it either happens completely or not
 * at all. See docs/specs/data-subject-rights/spec.md for what is deleted and what is kept, and why.
 *
 * <ul>
 *   <li>Deleted: the user's workout feedback, set logs and workout logs (health-related data held
 *       on their consent), the plans written for them with everything under them, their unused
 *       private exercises, and their password setup tokens.</li>
 *   <li>Ended: every active relationship on either side.</li>
 *   <li>Anonymised, not deleted: the {@code users} row ({@link User#anonymise}). Plans a trainer
 *       wrote for clients and the exercises in them stay with those clients, pointing at it.</li>
 * </ul>
 *
 * <p>A trainer with active clients is refused: their clients' plans would be orphaned mid-use,
 * so the relationships are ended first, deliberately ({@code EndTrainerClientRelationship}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountDeletionService {

    private final AccountDeletionRepository repository;
    private final TrainerClientRepository trainerClientRepository;

    /**
     * @param actorId who asked (today always the user themself; logged for the audit trail)
     * @param userId  the account to delete
     */
    public void deleteAccount(long actorId, long userId) {
        User user = repository.findByIdForUpdate(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getRole() == Role.TRAINER && trainerClientRepository.existsByTrainerIdAndEndedAtIsNull(userId)) {
            throw new BusinessRuleViolationException(
                    "End your client relationships first: an account with active clients cannot be deleted");
        }

        Instant now = DbInstants.now();
        repository.deleteFeedbackOfClient(userId);
        repository.deleteSetLogsOfClient(userId);
        repository.deleteWorkoutLogsOfClient(userId);
        repository.deleteExerciseSetsOfPlansForClient(userId);
        repository.deleteWorkoutExercisesOfPlansForClient(userId);
        repository.deleteWorkoutsOfPlansForClient(userId);
        repository.deletePlansForClient(userId);
        repository.deleteUnusedExercisesOwnedBy(userId);
        repository.endRelationships(userId, now);
        repository.deletePasswordSetupTokens(userId);
        user.anonymise(now);

        // Ids and role only: the audit line must not carry the personal data it records the removal of.
        log.info("Account deleted: actorId={} userId={} role={}", actorId, userId, user.getRole());
    }
}
