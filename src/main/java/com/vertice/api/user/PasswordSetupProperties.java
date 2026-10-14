package com.vertice.api.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code vertice.password-setup.token-ttl}: how long a password setup token stays usable. */
@ConfigurationProperties("vertice.password-setup")
public record PasswordSetupProperties(Duration tokenTtl) {

    public PasswordSetupProperties {
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("vertice.password-setup.token-ttl must be a positive duration");
        }
    }
}
