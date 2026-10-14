package com.vertice.api.plan;

import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;

/**
 * Who a training plan, or anything under it, belongs to: the plan's trainer and its client. For a
 * workout log, {@code clientId} is the log's own client (see {@link PlanAccessRepository}).
 */
public record PlanParties(Long trainerId, Long clientId) {

    public boolean isTrainer(CallerIdentity caller) {
        return caller.role() == Role.TRAINER && caller.userId().equals(trainerId);
    }

    public boolean isClient(CallerIdentity caller) {
        return caller.role() == Role.CLIENT && caller.userId().equals(clientId);
    }
}
