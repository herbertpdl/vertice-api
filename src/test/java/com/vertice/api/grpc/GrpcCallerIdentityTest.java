package com.vertice.api.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import com.vertice.api.user.Role;
import com.vertice.api.user.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the {@code local}-profile semantics of D1 over a real call: a bearer token is decoded
 * into a {@link CallerIdentity} when present, an absent token is anonymous (so only RPCs that call
 * {@link CallerIdentityResolver#requireUser()} fail), and an invalid or mis-scoped token is always
 * refused, exactly as outside {@code local}. The token's user must exist with its role, looked up
 * once per call however often the handler resolves the caller.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19104", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@Import(GrpcCallerIdentityTest.IdentityServiceConfig.class)
class GrpcCallerIdentityTest {

    private static final String SERVICE_NAME = "com.vertice.api.grpc.test.Identity";

    private static final MethodDescriptor<Empty, StringValue> WHO_AM_I = method("WhoAmI");
    private static final MethodDescriptor<Empty, StringValue> PING = method("Ping");

    @MockitoBean
    private UserRepository userRepository;

    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19104").usePlaintext().build();
        when(userRepository.findRoleById(42L)).thenReturn(Optional.of(Role.TRAINER));
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
    void call_resolvingTheCallerRepeatedly_looksTheUserUpOncePerCall() {
        call(WHO_AM_I, TestJwts.token(42L, Role.TRAINER));
        call(WHO_AM_I, TestJwts.token(42L, Role.TRAINER));

        verify(userRepository, times(2)).findRoleById(42L);
    }

    @Test
    void call_withTokenOfDemotedUser_failsUnauthenticated() {
        assertThatThrownBy(() -> call(WHO_AM_I, TestJwts.token(42L, Role.ADMIN)))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Session no longer valid");
                });
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

    @Test
    void call_withUnscopedToken_failsUnauthenticated() {
        // A pre-P1-01 token: right secret, but no iss/aud/exp.
        String unscoped = TestJwts.sign(TestJwts.LOCAL_SECRET, "{\"sub\":\"42\",\"id\":42,\"role\":\"TRAINER\"}");

        assertThatThrownBy(() -> call(PING, unscoped))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void call_withServiceToken_onUserOnlyRpc_failsPermissionDenied() {
        assertThatThrownBy(() -> call(WHO_AM_I, TestJwts.serviceToken(ServiceSubject.BFF)))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
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
                resolver.resolve();
                CallerIdentity caller = resolver.requireUser();
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
