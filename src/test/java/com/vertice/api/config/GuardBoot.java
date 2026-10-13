package com.vertice.api.config;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Boots a real, non-web {@code SpringApplication}, which runs {@link BootGuards} from
 * {@code spring.factories}. Pass every property a guard reads as a command-line arg: those win over
 * the developer's shell (JWT_SECRET, VERTICE_ENV, SPRING_DATASOURCE_URL), keeping tests hermetic.
 */
final class GuardBoot {

    private GuardBoot() {
    }

    static ConfigurableApplicationContext boot(Class<?> config, String... args) {
        return new SpringApplicationBuilder(config)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    static ConfigurableApplicationContext boot(String... args) {
        return boot(EmptyConfig.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }
}
