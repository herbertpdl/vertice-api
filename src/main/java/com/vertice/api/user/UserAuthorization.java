package com.vertice.api.user;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.grpc.Caller;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.trainerclient.TrainerClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Who may call each {@code UserService} RPC (P1-01). vertice-bff checks the same things first; this
 * is the authoritative layer, so it holds even for a token used directly against the API. The full
 * subject → RPC matrix is in CLAUDE.md. Every refusal is {@code PERMISSION_DENIED}, except a call
 * with no token at all ({@code local} only), which is {@code UNAUTHENTICATED}.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAuthorization {

    private final UserRepository userRepository;
    private final TrainerClientRepository trainerClientRepository;
    private final TrainingPlanRepository trainingPlanRepository;

    /**
     * ADMIN or the ops scripts. Anonymous too: that's only reachable under {@code local}, where the
     * BFF's {@code VERIFY_PASSWORDS=false} dev login looks users up without a token.
     */
    public void checkListUsers(Caller caller) {
        if (caller.isAdmin() || caller.isService(ServiceSubject.OPS) || caller instanceof Caller.Anonymous) {
            return;
        }
        throw denied(caller, "list users");
    }

    /**
     * ADMIN sees everyone; a TRAINER sees only their own active clients (the BFF re-issues a setup
     * link only to a client still without a password). The ops scripts don't call this: they list
     * clients through {@code ListUsers}, so {@code service:ops} is refused here.
     *
     * @return the trainer to scope the list to, or empty for an unscoped list
     */
    public Optional<Long> checkListUsersWithoutPassword(Caller caller) {
        if (caller.isAdmin()) {
            return Optional.empty();
        }
        if (caller instanceof CallerIdentity user && user.role() == Role.TRAINER) {
            return Optional.of(user.userId());
        }
        throw denied(caller, "list users without a password");
    }

    /**
     * The user themself gets the self view (their consent state included). ADMIN or the target's
     * active trainer get the detail view. A trainer who has
     * (or had) a training plan with the target sees the summary only: their dashboard, feedback and
     * recent-workout screens name the client of every plan they wrote, including clients no longer
     * on their roster, and must not fail because one of them left.
     */
    public UserView checkGetUser(Caller caller, Long id) {
        if (caller.isUser(id)) {
            return UserView.SELF;
        }
        if (caller.isAdmin() || isActiveTrainerOf(caller, id)) {
            return UserView.DETAIL;
        }
        if (caller instanceof CallerIdentity user && user.role() == Role.TRAINER
                && trainingPlanRepository.existsByTrainerIdAndClientId(user.userId(), id)) {
            return UserView.SUMMARY;
        }
        throw noAccess(caller, id);
    }

    /** Public registration only: the BFF's service identity, and never an ADMIN account. */
    public void checkCreateUser(Caller caller, Role role) {
        if (!caller.isService(ServiceSubject.BFF)) {
            throw denied(caller, "create users");
        }
        if (role == Role.ADMIN) {
            throw new PermissionDeniedException("ADMIN users cannot be created through CreateUser");
        }
    }

    /**
     * ADMIN changes anything. Otherwise the user themself, or the target's active trainer (the
     * BFF's {@code PATCH /clients/:id}), and neither may change the role. For a trainer that leaves
     * name, email and CPF: {@code cref} can't be set on a CLIENT.
     */
    public void checkUpdateUser(Caller caller, Long id, Role requestedRole) {
        if (caller.isAdmin()) {
            return;
        }
        if (!caller.isUser(id) && !isActiveTrainerOf(caller, id)) {
            throw noAccess(caller, id);
        }
        Optional<Role> currentRole = userRepository.findById(id).map(User::getRole);
        if (currentRole.isPresent() && currentRole.get() != requestedRole) {
            throw new PermissionDeniedException("Only an ADMIN can change a user's role");
        }
    }

    /** The user themself or ADMIN. */
    public void checkDeleteUser(Caller caller, Long id) {
        requireSelfOrAdmin(caller, id);
    }

    /** The user themself (the BFF's {@code POST /auth/password}) or ADMIN. */
    public void checkSetUserPassword(Caller caller, Long id) {
        requireSelfOrAdmin(caller, id);
    }

    /**
     * Login (the BFF's service identity), or a user re-checking their own current password before
     * changing it ({@code POST /auth/password}, sent with the user's token): then the email must be
     * the caller's own, so a user token can't be used to test passwords of other accounts.
     */
    public void checkVerifyCredentials(Caller caller, String email) {
        if (caller.isService(ServiceSubject.BFF)) {
            return;
        }
        if (caller instanceof CallerIdentity user) {
            boolean ownEmail = userRepository.findById(user.userId())
                    .map(User::getEmail)
                    .filter(stored -> stored.equals(Emails.normalize(email)))
                    .isPresent();
            if (ownEmail) {
                return;
            }
            throw new PermissionDeniedException("You can only verify your own credentials");
        }
        throw denied(caller, "verify credentials");
    }

    /**
     * The ops scripts, ADMIN, or a TRAINER for their own active client who has no password yet
     * (once a client chose one, a trainer-issued link would let the trainer take the account over).
     * A setup token is a password-reset primitive, so this is the strictest rule here.
     */
    public void checkCreatePasswordSetupToken(Caller caller, Long userId) {
        if (caller.isService(ServiceSubject.OPS) || caller.isAdmin()) {
            return;
        }
        if (!isActiveTrainerOf(caller, userId)) {
            throw noAccess(caller, userId);
        }
        boolean hasPassword = userRepository.findById(userId)
                .map(User::getPasswordHash)
                .isPresent();
        if (hasPassword) {
            throw new PermissionDeniedException("User %d has already set a password".formatted(userId));
        }
    }

    /**
     * The calling user, for themself only: a consent is the data subject's own act, so not even an
     * ADMIN records one for someone else. The request carries no user id at all.
     *
     * @return the user whose consent is recorded
     */
    public long checkRecordConsent(Caller caller) {
        if (caller instanceof CallerIdentity user) {
            return user.userId();
        }
        throw denied(caller, "record a consent");
    }

    /** First access through a setup link: the BFF's service identity only. */
    public void checkSetPasswordWithToken(Caller caller) {
        if (!caller.isService(ServiceSubject.BFF)) {
            throw denied(caller, "set a password with a setup token");
        }
    }

    private void requireSelfOrAdmin(Caller caller, Long id) {
        if (!caller.isUser(id) && !caller.isAdmin()) {
            throw noAccess(caller, id);
        }
    }

    private boolean isActiveTrainerOf(Caller caller, Long clientId) {
        return caller instanceof CallerIdentity user
                && user.role() == Role.TRAINER
                && trainerClientRepository.existsByTrainerIdAndClientIdAndClientRoleAndEndedAtIsNull(
                        user.userId(), clientId, Role.CLIENT);
    }

    private static RuntimeException denied(Caller caller, String action) {
        return switch (caller) {
            case CallerIdentity user -> PermissionDeniedException.role(user.role(), action);
            case Caller.Service service -> PermissionDeniedException.service(service.subject());
            case Caller.Anonymous anonymous -> new UnauthenticatedException();
        };
    }

    private static RuntimeException noAccess(Caller caller, Long userId) {
        return caller instanceof CallerIdentity
                ? PermissionDeniedException.noAccess("user", userId)
                : denied(caller, "access user " + userId);
    }
}
