package com.vertice.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * {@link HmacJwtDecoder} accepts secrets of any length so the short local defaults work; outside
 * {@code local} the application refuses to boot on a secret below the 256-bit HS256 floor.
 */
@Component
@Profile("!local")
public class JwtSecretGuard {

    static final int MIN_SECRET_BYTES = 32;

    private final String secret;

    public JwtSecretGuard(@Value("${vertice.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void checkSecretLength() {
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
    }
}
