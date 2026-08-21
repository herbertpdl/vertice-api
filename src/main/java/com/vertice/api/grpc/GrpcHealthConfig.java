package com.vertice.api.grpc;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot's gRPC autoconfiguration (server, reflection service, health service) only
 * activates once at least one {@link BindableService} bean exists — there's nothing to serve
 * otherwise, and it won't stand up a server for zero services. This empty placeholder (no RPCs)
 * exists purely to satisfy that bootstrap condition without introducing any business logic ahead
 * of the first real service (added in grpc-trainer); it's replaced by real services as those land
 * and can be deleted once at least one exists.
 */
@Configuration
class GrpcHealthConfig {

    @Bean
    BindableService grpcBootstrapPlaceholder() {
        return () -> ServerServiceDefinition.builder("com.vertice.api.grpc.Bootstrap").build();
    }
}
