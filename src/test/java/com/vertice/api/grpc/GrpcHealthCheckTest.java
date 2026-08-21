package com.vertice.api.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The gRPC server auto-activates JWT auth as soon as spring-boot-starter-security-oauth2-resource-server
 * is on the classpath — same as it already does for REST via {@code SecurityConfig} — so an
 * unauthenticated call is the correct smoke-test signal here: it proves the server is up, wired to
 * the standard health service, and enforcing auth by default, without grpc-cross-cutting having
 * written a single line of interceptor code yet.
 */
@SpringBootTest(properties = "spring.grpc.server.port=19090")
class GrpcHealthCheckTest {

    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void check_withoutAuth_returnsUnauthenticated() {
        channel = NettyChannelBuilder.forTarget("localhost:19090").usePlaintext().build();
        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> healthStub.check(HealthCheckRequest.newBuilder().build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }
}
