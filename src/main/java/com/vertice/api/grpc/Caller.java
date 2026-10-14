package com.vertice.api.grpc;

import com.vertice.api.user.Role;

/**
 * Who is calling, resolved from the bearer token by {@link CallerIdentityResolver}: a user
 * ({@link CallerIdentity}), one of the BFF's service identities ({@link Service}), or nobody
 * ({@link Anonymous}, only possible under the {@code local} profile, where a token is optional).
 */
public sealed interface Caller permits CallerIdentity, Caller.Service, Caller.Anonymous {

    /** A service identity: {@code sub} is one of {@link ServiceSubject}, {@code role} is {@code SERVICE}. */
    record Service(ServiceSubject subject) implements Caller {
    }

    /** No token was sent. Non-local profiles reject such calls before they reach a controller. */
    enum Anonymous implements Caller {
        INSTANCE
    }

    default boolean isService(ServiceSubject subject) {
        return this instanceof Service(ServiceSubject actual) && actual == subject;
    }

    default boolean isUserWithRole(Role role) {
        return this instanceof CallerIdentity user && user.role() == role;
    }

    default boolean isAdmin() {
        return isUserWithRole(Role.ADMIN);
    }

    /** Whether this is the user {@code userId} themself. */
    default boolean isUser(Long userId) {
        return this instanceof CallerIdentity user && user.userId().equals(userId);
    }
}
