package com.vertice.api.grpc;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.user.Role;
import com.vertice.api.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the caller the gRPC security interceptor bound for this call. Controllers resolve it and
 * pass it into the service, so services never touch the security context themselves.
 *
 * <p>Token shapes (vertice-bff {@code src/lib/jwt.ts}): a user has a numeric {@code sub} and a
 * {@code role} of ADMIN/TRAINER/CLIENT, plus a legacy numeric {@code id} that must equal {@code sub}
 * when present; a service has {@code sub} {@code service:bff} or {@code service:ops} and {@code role}
 * {@code SERVICE}. A token that is neither is {@code UNAUTHENTICATED}, even on RPCs that would
 * accept any caller.
 *
 * <p>A user token is only as good as the user behind it: the user must still exist and still have
 * the token's role, checked against the database (one primary-key lookup of the role) every call,
 * so a deleted or re-roled user loses access at once instead of when the token expires. Otherwise
 * the call is {@code UNAUTHENTICATED} ("Session no longer valid"), which the BFF turns into a 401
 * that logs the user out. The result is kept for the rest of the call by
 * {@link CallerScopeInterceptor}, never across calls.
 */
@Component
public class CallerIdentityResolver {

    private static final String ROLE_CLAIM = "role";
    private static final String LEGACY_ID_CLAIM = "id";
    private static final String SERVICE_PREFIX = "service:";

    private final UserRepository userRepository;

    public CallerIdentityResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The caller, or {@link Caller.Anonymous} when no token was sent (only possible under {@code local}). */
    public Caller resolve() {
        AtomicReference<Caller> resolved = CallerScopeInterceptor.RESOLVED_CALLER.get();
        if (resolved == null) {
            return resolveFromSecurityContext();
        }
        Caller caller = resolved.get();
        if (caller == null) {
            caller = resolveFromSecurityContext();
            resolved.set(caller);
        }
        return caller;
    }

    /** A user caller; a service token is {@code PERMISSION_DENIED}, no token {@code UNAUTHENTICATED}. */
    public CallerIdentity requireUser() {
        return switch (resolve()) {
            case CallerIdentity user -> user;
            case Caller.Service service -> throw PermissionDeniedException.service(service.subject());
            case Caller.Anonymous anonymous -> throw new UnauthenticatedException();
        };
    }

    /**
     * Any user, or no token at all (only possible under {@code local}); a service token is
     * {@code PERMISSION_DENIED}. For reference data every signed-in screen needs.
     */
    public Caller requireUserOrAnonymous() {
        Caller caller = resolve();
        if (caller instanceof Caller.Service service) {
            throw PermissionDeniedException.service(service.subject());
        }
        return caller;
    }

    /** One of the {@code allowed} service identities; anything else is {@code PERMISSION_DENIED}. */
    public Caller.Service requireService(ServiceSubject... allowed) {
        return switch (resolve()) {
            case Caller.Service service when Arrays.asList(allowed).contains(service.subject()) -> service;
            case Caller.Service service -> throw PermissionDeniedException.service(service.subject());
            case CallerIdentity user -> throw PermissionDeniedException.role(user.role(), "call this RPC");
            case Caller.Anonymous anonymous -> throw new UnauthenticatedException();
        };
    }

    private Caller resolveFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Caller.Anonymous.INSTANCE;
        }
        Caller caller = fromToken(jwtAuthentication.getToken());
        if (caller instanceof CallerIdentity user) {
            requireCurrent(user);
        }
        return caller;
    }

    /** The token's user must still exist with the token's role; a deleted or re-roled user is signed out. */
    private void requireCurrent(CallerIdentity user) {
        Optional<Role> storedRole = userRepository.findRoleById(user.userId());
        if (storedRole.filter(role -> role == user.role()).isEmpty()) {
            throw UnauthenticatedException.sessionNoLongerValid();
        }
    }

    private static Caller fromToken(Jwt jwt) {
        String subject = jwt.getSubject();
        Object role = jwt.getClaim(ROLE_CLAIM);
        if (subject == null) {
            throw UnauthenticatedException.unrecognized();
        }
        if (subject.startsWith(SERVICE_PREFIX)) {
            return ServiceSubject.fromSubject(subject)
                    .filter(service -> ServiceSubject.ROLE.equals(role))
                    .map(Caller.Service::new)
                    .orElseThrow(UnauthenticatedException::unrecognized);
        }
        Long userId = userId(subject).orElseThrow(UnauthenticatedException::unrecognized);
        Object legacyId = jwt.getClaim(LEGACY_ID_CLAIM);
        if (legacyId != null && !userId(legacyId).equals(Optional.of(userId))) {
            throw UnauthenticatedException.unrecognized();
        }
        return new CallerIdentity(userId, userRole(role).orElseThrow(UnauthenticatedException::unrecognized));
    }

    private static Optional<Long> userId(Object claim) {
        return switch (claim) {
            // JSON numbers may surface as Long or Double depending on the parser; accept whole numbers only.
            case Number value when value.doubleValue() == value.longValue() && value.longValue() > 0 ->
                    Optional.of(value.longValue());
            case String value when value.matches("[1-9]\\d{0,17}") -> Optional.of(Long.parseLong(value));
            case null, default -> Optional.empty();
        };
    }

    private static Optional<Role> userRole(Object claim) {
        if (!(claim instanceof String value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Role.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
