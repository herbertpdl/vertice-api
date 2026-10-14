package com.vertice.api.plan;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The plan-tree rules every {@code *Authorization} class in {@code plan/} is built from (P1-01). A
 * plan's trainer acts on the plan and everything under it, its client reads it, and ADMIN bypasses.
 *
 * <p>Existence is never revealed to a non-admin: a missing id and someone else's id are the same
 * {@code PERMISSION_DENIED} ({@code You do not have access to <resource> <id>}), decided before
 * the service loads anything. ADMIN skips the lookup, so a missing id reaches the service and is
 * {@code NOT_FOUND} as before. A wrong role is refused before any lookup too.
 */
public final class PlanAccess {

    private PlanAccess() {
    }

    /** ADMIN, or the plan's trainer or client. */
    public static void requireTrainerOrClient(CallerIdentity caller, String resource, Long id,
                                              Supplier<Optional<PlanParties>> parties) {
        require(caller, resource, id, parties, p -> p.isTrainer(caller) || p.isClient(caller));
    }

    /** ADMIN or the plan's trainer. */
    public static void requireTrainer(CallerIdentity caller, String action, String resource, Long id,
                                      Supplier<Optional<PlanParties>> parties) {
        caller.requireRole(action, Role.TRAINER, Role.ADMIN);
        require(caller, resource, id, parties, p -> p.isTrainer(caller));
    }

    /** ADMIN or the client in {@code parties} (for a workout log, the log's own client). */
    public static void requireClient(CallerIdentity caller, String action, String resource, Long id,
                                     Supplier<Optional<PlanParties>> parties) {
        caller.requireRole(action, Role.CLIENT, Role.ADMIN);
        require(caller, resource, id, parties, p -> p.isClient(caller));
    }

    /**
     * The id a request names for {@code role}'s side ({@code trainer_id}, {@code client_id}), once
     * the caller is known: a caller with that role always acts as themself, so {@code 0} or their
     * own id becomes their id and any other id is {@code PERMISSION_DENIED}. Other roles keep the
     * requested value (ADMIN may name anyone; for the opposite role it's a filter the other rules
     * already bound).
     */
    public static long ownId(CallerIdentity caller, Role role, long requested, String field) {
        if (caller.role() != role) {
            return requested;
        }
        if (requested == 0 || requested == caller.userId()) {
            return caller.userId();
        }
        throw new PermissionDeniedException("%s must be your own id".formatted(field));
    }

    private static void require(CallerIdentity caller, String resource, Long id,
                                Supplier<Optional<PlanParties>> parties, Predicate<PlanParties> allowed) {
        if (caller.isAdmin()) {
            return;
        }
        if (parties.get().filter(allowed).isEmpty()) {
            throw PermissionDeniedException.noAccess(resource, id);
        }
    }
}
