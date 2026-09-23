package com.vertice.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The one {@link JwtDecoder} both transports use (REST's {@code SecurityConfig} and
 * {@code GrpcSecurityConfig}); its presence makes Spring Boot's own decoder auto-configuration
 * back off.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${vertice.jwt.secret}") String secret) {
        return new HmacJwtDecoder(secret);
    }
}
