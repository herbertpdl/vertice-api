package com.vertice.api.grpc;

import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.UnauthenticatedException;
import com.vertice.api.user.Role;
import com.vertice.api.user.UserRepository;
import io.grpc.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallerIdentityResolverTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CallerIdentityResolver resolver = new CallerIdentityResolver(userRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolve_userTokenWithSubOnly_isUser() {
        storedUser(42L, Role.TRAINER);
        authenticateWith(Map.of("sub", "42", "role", "TRAINER"));

        assertThat(resolver.resolve()).isEqualTo(new CallerIdentity(42L, Role.TRAINER));
    }

    @Test
    void resolve_userTokenWithMatchingLegacyId_isUser() {
        storedUser(42L, Role.CLIENT);
        authenticateWith(Map.of("sub", "42", "id", 42L, "role", "CLIENT"));

        assertThat(resolver.resolve()).isEqualTo(new CallerIdentity(42L, Role.CLIENT));
    }

    @Test
    void resolve_legacyIdDifferentFromSub_throwsUnauthenticated() {
        authenticateWith(Map.of("sub", "42", "id", 7L, "role", "TRAINER"));

        assertThatThrownBy(resolver::resolve).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void resolve_legacyIdWithoutSub_throwsUnauthenticated() {
        authenticateWith(Map.of("id", 42L, "role", "TRAINER"));

        assertThatThrownBy(resolver::resolve).isInstanceOf(UnauthenticatedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "4.2", "service:other", "99999999999999999999"})
    void resolve_unknownSubject_throwsUnauthenticated(String subject) {
        authenticateWith(Map.of("sub", subject, "role", "TRAINER"));

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("Unrecognized caller identity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERUSER", "SERVICE", "trainer"})
    void resolve_userWithUnknownRole_throwsUnauthenticated(String role) {
        authenticateWith(Map.of("sub", "42", "role", role));

        assertThatThrownBy(resolver::resolve).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void resolve_serviceTokens_areServices() {
        authenticateWith(Map.of("sub", "service:bff", "role", "SERVICE"));
        assertThat(resolver.resolve()).isEqualTo(new Caller.Service(ServiceSubject.BFF));

        authenticateWith(Map.of("sub", "service:ops", "role", "SERVICE"));
        assertThat(resolver.resolve()).isEqualTo(new Caller.Service(ServiceSubject.OPS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TRAINER", "service"})
    void resolve_serviceSubjectWithoutServiceRole_throwsUnauthenticated(String role) {
        authenticateWith(Map.of("sub", "service:bff", "role", role));

        assertThatThrownBy(resolver::resolve).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void resolve_withoutAuthentication_isAnonymous() {
        assertThat(resolver.resolve()).isEqualTo(Caller.Anonymous.INSTANCE);
    }

    @Test
    void requireUser_withUser_returnsIt() {
        storedUser(42L, Role.ADMIN);
        authenticateWith(Map.of("sub", "42", "role", "ADMIN"));

        assertThat(resolver.requireUser()).isEqualTo(new CallerIdentity(42L, Role.ADMIN));
    }

    @Test
    void requireUser_withService_throwsPermissionDenied() {
        authenticateWith(Map.of("sub", "service:bff", "role", "SERVICE"));

        assertThatThrownBy(resolver::requireUser)
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("service:bff is not allowed to call this RPC");
    }

    @Test
    void requireUser_withoutAuthentication_throwsUnauthenticated() {
        assertThatThrownBy(resolver::requireUser)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("Caller identity required");
    }

    @Test
    void requireUserOrAnonymous_withUserOrNoToken_returnsIt() {
        storedUser(42L, Role.CLIENT);
        assertThat(resolver.requireUserOrAnonymous()).isEqualTo(Caller.Anonymous.INSTANCE);

        authenticateWith(Map.of("sub", "42", "role", "CLIENT"));
        assertThat(resolver.requireUserOrAnonymous()).isEqualTo(new CallerIdentity(42L, Role.CLIENT));
    }

    @Test
    void requireUserOrAnonymous_withService_throwsPermissionDenied() {
        authenticateWith(Map.of("sub", "service:ops", "role", "SERVICE"));

        assertThatThrownBy(resolver::requireUserOrAnonymous)
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("service:ops is not allowed to call this RPC");
    }

    @Test
    void requireService_withAllowedService_returnsIt() {
        authenticateWith(Map.of("sub", "service:ops", "role", "SERVICE"));

        assertThat(resolver.requireService(ServiceSubject.BFF, ServiceSubject.OPS))
                .isEqualTo(new Caller.Service(ServiceSubject.OPS));
    }

    @Test
    void requireService_withOtherService_throwsPermissionDenied() {
        authenticateWith(Map.of("sub", "service:ops", "role", "SERVICE"));

        assertThatThrownBy(() -> resolver.requireService(ServiceSubject.BFF)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void requireService_withUser_throwsPermissionDenied() {
        storedUser(42L, Role.ADMIN);
        authenticateWith(Map.of("sub", "42", "role", "ADMIN"));

        assertThatThrownBy(() -> resolver.requireService(ServiceSubject.BFF)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void requireService_withoutAuthentication_throwsUnauthenticated() {
        assertThatThrownBy(() -> resolver.requireService(ServiceSubject.BFF)).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void resolve_deletedUser_throwsSessionNoLongerValid() {
        authenticateWith(Map.of("sub", "42", "role", "TRAINER"));

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("Session no longer valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "ADMIN"})
    void resolve_roleChangedSinceTokenWasIssued_throwsSessionNoLongerValid(String storedRole) {
        storedUser(42L, Role.valueOf(storedRole));
        authenticateWith(Map.of("sub", "42", "role", "TRAINER"));

        assertThatThrownBy(resolver::requireUser)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("Session no longer valid");
    }

    @Test
    void resolve_serviceAndAnonymousCallers_neverLookUpAUser() {
        resolver.resolve();
        authenticateWith(Map.of("sub", "service:bff", "role", "SERVICE"));
        resolver.resolve();

        verify(userRepository, never()).findRoleById(anyLong());
    }

    @Test
    void resolve_withinOneCall_looksUpTheUserOnce() {
        storedUser(42L, Role.TRAINER);
        authenticateWith(Map.of("sub", "42", "role", "TRAINER"));

        Context.current().withValue(CallerScopeInterceptor.RESOLVED_CALLER, new AtomicReference<>()).run(() -> {
            resolver.resolve();
            resolver.requireUser();
            resolver.requireUserOrAnonymous();
        });

        verify(userRepository, times(1)).findRoleById(42L);
    }

    @Test
    void resolve_eachCall_looksUpTheUserAgain() {
        storedUser(42L, Role.TRAINER);
        authenticateWith(Map.of("sub", "42", "role", "TRAINER"));

        for (int call = 0; call < 2; call++) {
            Context.current().withValue(CallerScopeInterceptor.RESOLVED_CALLER, new AtomicReference<>())
                    .run(resolver::resolve);
        }

        verify(userRepository, times(2)).findRoleById(42L);
    }

    @Test
    void callerPredicates() {
        Caller admin = new CallerIdentity(1L, Role.ADMIN);
        Caller bff = new Caller.Service(ServiceSubject.BFF);

        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isUser(1L)).isTrue();
        assertThat(admin.isUser(2L)).isFalse();
        assertThat(admin.isService(ServiceSubject.BFF)).isFalse();
        assertThat(bff.isService(ServiceSubject.BFF)).isTrue();
        assertThat(bff.isService(ServiceSubject.OPS)).isFalse();
        assertThat(bff.isAdmin()).isFalse();
        assertThat(Caller.Anonymous.INSTANCE.isUser(1L)).isFalse();
    }

    private void storedUser(long id, Role role) {
        when(userRepository.findRoleById(id)).thenReturn(Optional.of(role));
    }

    private static void authenticateWith(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claims(c -> c.putAll(claims))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
