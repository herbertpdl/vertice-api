package com.vertice.api.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Defining our own {@link AuthenticationProcessInterceptor} bean(s) makes Spring Boot's default
 * gRPC OAuth2 resource-server wiring back off entirely (it's
 * {@code @ConditionalOnMissingBean(AuthenticationProcessInterceptor.class)}), which is how this
 * class gets to add the {@code local}-profile bypass REST already has via
 * {@link com.vertice.api.config.LocalSecurityConfig} — gRPC's auto-configured default doesn't
 * know about that Spring MVC-only bean.
 *
 * <p>Both beans decode bearer tokens with the shared-secret
 * {@link com.vertice.api.config.HmacJwtDecoder}. Non-local requires a valid token on every call.
 * Local permits every call but still decodes a token when one is sent, so
 * {@code CallerIdentityResolver} can see who is calling; an absent token leaves the call
 * anonymous, while a present but invalid token is still rejected with {@code UNAUTHENTICATED}.
 */
@Configuration
class GrpcSecurityConfig {

    @Bean
    @Profile("!local")
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor grpcAuthenticationInterceptor(GrpcSecurity grpc) throws Exception {
        return grpc
                .authorizeRequests(requests -> requests.allRequests().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(withDefaults()))
                .build();
    }

    @Bean
    @Profile("local")
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor localGrpcAuthenticationInterceptor(GrpcSecurity grpc) throws Exception {
        return grpc
                .authorizeRequests(requests -> requests.allRequests().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(withDefaults()))
                .build();
    }
}
