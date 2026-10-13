package com.vertice.api.grpc;

import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the caller identity the gRPC security interceptor bound for this call. Controllers call
 * {@link #require()} on RPCs that need it and pass the result into the service, so services never
 * touch the security context themselves.
 */
@Component
public class CallerIdentityResolver {

    public Optional<CallerIdentity> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        Jwt jwt = jwtAuthentication.getToken();
        return userId(jwt.getClaim("id"))
                .flatMap(userId -> role(jwt.getClaim("role")).map(role -> new CallerIdentity(userId, role)));
    }

    public CallerIdentity require() {
        return current().orElseThrow(UnauthenticatedException::new);
    }

    private static Optional<Long> userId(Object claim) {
        return switch (claim) {
            // JSON numbers may surface as Long or Double depending on the parser; accept whole numbers only.
            case Number value when value.doubleValue() == value.longValue() -> Optional.of(value.longValue());
            case String value when value.matches("\\d+") -> {
                try {
                    yield Optional.of(Long.parseLong(value));
                } catch (NumberFormatException ex) {
                    yield Optional.empty();
                }
            }
            case null, default -> Optional.empty();
        };
    }

    private static Optional<Role> role(Object claim) {
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
