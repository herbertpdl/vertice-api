package com.vertice.api.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import com.vertice.api.user.Role;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * Proves the {@code local}-profile semantics of D1 over a real call: a bearer token is decoded
 * into a {@link CallerIdentity} when present, an absent token is anonymous (so only RPCs that call
 * {@link CallerIdentityResolver#require()} fail), and an invalid token is always refused.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19104", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@Import(GrpcCallerIdentityTest.IdentityServiceConfig.class)
class GrpcCallerIdentityTest {

    private static final String SERVICE_NAME = "com.vertice.api.grpc.test.Identity";

    private static final MethodDescriptor<Empty, StringValue> WHO_AM_I = method("WhoAmI");
    private static final MethodDescriptor<Empty, StringValue> PING = method("Ping");

    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19104").usePlaintext().build();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void call_withBearerToken_resolvesIdentity() {
        StringValue response = call(WHO_AM_I, TestJwts.token(42L, Role.TRAINER));

        assertThat(response.getValue()).isEqualTo("42:TRAINER");
    }

    @Test
    void call_withoutToken_requireFailsUnauthenticated() {
        assertThatThrownBy(() -> call(WHO_AM_I, null))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Caller identity required");
                });
    }

    @Test
    void call_withoutToken_rpcNotRequiringIdentitySucceeds() {
        StringValue response = call(PING, null);

        assertThat(response.getValue()).isEqualTo("pong");
    }

    @Test
    void call_withInvalidToken_failsUnauthenticated() {
        String wrongSecretToken = TestJwts.token("some-other-secret", 42L, Role.TRAINER);

        assertThatThrownBy(() -> call(PING, wrongSecretToken))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    private StringValue call(MethodDescriptor<Empty, StringValue> method, String token) {
        if (token == null) {
            return ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, Empty.getDefaultInstance());
        }
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return ClientCalls.blockingUnaryCall(
                ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers)),
                method, CallOptions.DEFAULT, Empty.getDefaultInstance());
    }

    private static MethodDescriptor<Empty, StringValue> method(String name) {
        return MethodDescriptor.<Empty, StringValue>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, name))
                .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .build();
    }

    @TestConfiguration
    static class IdentityServiceConfig {

        @Bean
        BindableService identityService(CallerIdentityResolver resolver) {
            ServerCalls.UnaryMethod<Empty, StringValue> whoAmI = (request, responseObserver) -> {
                CallerIdentity caller = resolver.require();
                responseObserver.onNext(StringValue.of(caller.userId() + ":" + caller.role()));
                responseObserver.onCompleted();
            };
            ServerCalls.UnaryMethod<Empty, StringValue> ping = (request, responseObserver) -> {
                responseObserver.onNext(StringValue.of("pong"));
                responseObserver.onCompleted();
            };

            return () -> ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(WHO_AM_I, ServerCalls.asyncUnaryCall(whoAmI))
                    .addMethod(PING, ServerCalls.asyncUnaryCall(ping))
                    .build();
        }
    }
}
