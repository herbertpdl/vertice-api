package com.vertice.api.grpc;

import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes the loop {@code grpc-foundation} couldn't: under {@code local}, gRPC calls no longer
 * require a JWT — mirrors {@code LocalSecurityConfig}'s REST bypass, now implemented in
 * {@link GrpcSecurityConfig}.
 */
@SpringBootTest(properties = "spring.grpc.server.port=19091")
@ActiveProfiles("local")
class GrpcHealthCheckLocalProfileTest {

    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void check_withoutAuth_returnsServing() {
        channel = NettyChannelBuilder.forTarget("localhost:19091").usePlaintext().build();
        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);

        HealthCheckResponse response = healthStub.check(HealthCheckRequest.newBuilder().build());

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }
}
