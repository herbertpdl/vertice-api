package com.vertice.api.user;

import com.vertice.api.generated.grpc.user.v1.UserResponse;

/**
 * How much of a user an RPC returns (P1-03). Every view keeps the {@link UserResponse} shape, so
 * older clients still decode it; the fields outside the view are left empty.
 *
 * <ul>
 *   <li>{@link #DETAIL}: {@code GetUser} for the user themself, ADMIN or the target's active
 *       trainer; {@code UpdateUser}; {@code CreateClientForTrainer}.</li>
 *   <li>{@link #CONTACT}: {@code ListUsers} (the ops setup-link script and the local dev login
 *       match by email), {@code CreateUser} and {@code VerifyCredentials} (the BFF puts the
 *       email in the session).</li>
 *   <li>{@link #SUMMARY}: {@code ListClientsForTrainer} (vertice-bff's roster);
 *       {@code ListUsersWithoutPassword}; {@code GetUser} for a trainer who only has a training
 *       plan with the target.</li>
 * </ul>
 */
public enum UserView {
    /** Every field: email, CPF and CREF included. */
    DETAIL,
    /** id, name, role and email: no CPF or CREF. */
    CONTACT,
    /** id, name and role only. */
    SUMMARY;

    public UserResponse restrict(UserResponse user) {
        return switch (this) {
            case DETAIL -> user;
            case CONTACT -> summary(user).setEmail(user.getEmail()).build();
            case SUMMARY -> summary(user).build();
        };
    }

    private static UserResponse.Builder summary(UserResponse user) {
        return UserResponse.newBuilder().setId(user.getId()).setName(user.getName()).setRole(user.getRole());
    }
}
