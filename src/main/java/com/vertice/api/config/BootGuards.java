package com.vertice.api.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Runs the startup guards as soon as the environment is prepared, before any bean exists. As
 * beans they could be skipped by {@code spring.main.lazy-initialization} and would only fire after
 * Hikari had connected and Flyway had migrated. Registered in {@code META-INF/spring.factories}.
 *
 * <ul>
 *   <li>{@code local} active: {@link LocalProfileGuard} (local machine and local database only).</li>
 *   <li>otherwise: {@link JwtSecretGuard} (no blank, weak or placeholder JWT secret).</li>
 * </ul>
 */
public class BootGuards implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.matchesProfiles("local")) {
            new LocalProfileGuard(environment.getProperty("VERTICE_ENV", ""),
                    environment.getProperty("spring.datasource.url", "")).check();
        } else {
            new JwtSecretGuard(environment.getProperty("vertice.jwt.secret")).checkSecret();
        }
    }

    /** Last, so profiles and properties from config files are already in place. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
