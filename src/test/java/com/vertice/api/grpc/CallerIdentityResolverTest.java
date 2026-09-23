package com.vertice.api.grpc;

import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerIdentityResolverTest {

    private final CallerIdentityResolver resolver = new CallerIdentityResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void require_withJwtAuthentication_returnsUserIdAndRole() {
        authenticateWith(Map.of("id", 42L, "role", "TRAINER"));

        assertThat(resolver.require()).isEqualTo(new CallerIdentity(42L, Role.TRAINER));
    }

    @Test
    void require_withoutAuthentication_throwsUnauthenticated() {
        assertThatThrownBy(resolver::require)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("Caller identity required");
    }

    @Test
    void require_withMissingIdClaim_throwsUnauthenticated() {
        authenticateWith(Map.of("role", "TRAINER"));

        assertThatThrownBy(resolver::require).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void require_withNonNumericIdClaim_throwsUnauthenticated() {
        authenticateWith(Map.of("id", "abc", "role", "TRAINER"));

        assertThatThrownBy(resolver::require).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void require_withUnknownRole_throwsUnauthenticated() {
        authenticateWith(Map.of("id", 42L, "role", "SUPERUSER"));

        assertThatThrownBy(resolver::require).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void current_withoutAuthentication_isEmpty() {
        assertThat(resolver.current()).isEmpty();
    }

    private static void authenticateWith(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claims(c -> c.putAll(claims))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
