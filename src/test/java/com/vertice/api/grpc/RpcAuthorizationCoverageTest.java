package com.vertice.api.grpc;

import com.google.protobuf.Message;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.RedeemPasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.SetPasswordWithTokenRequest;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.VerifyCredentialsRequest;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.vertice.api.plan.PlanCallers.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every RPC the server binds is guarded (P1-01): no future RPC can ship without an authorization
 * decision. The methods are read from the bound services themselves, so a new RPC or service is
 * covered the moment it exists, with no list to update.
 *
 * <p>Each one is called by the callers the token contract shuts out of almost everything: no token
 * at all, {@code service:bff} and {@code service:ops}. Under {@code local} the transport lets every
 * call through, so the controller is the only thing that can refuse. Unless the contract allows
 * that caller on that RPC ({@link #ALLOWED}, mirroring the CLAUDE.md matrix), the answer must be
 * {@code UNAUTHENTICATED} (no token) or {@code PERMISSION_DENIED} (service). An RPC that never asks
 * who is calling answers something else and fails here.
 *
 * <p>Why this probe rather than a per-RPC non-owner case or a "the resolver was called" spy: a
 * non-owner needs data shaped for each RPC, so it can't be generated for an RPC nobody has written
 * a test for yet; a spy proves the caller was read, not that the answer depended on it. Each
 * user-role rule (owner vs. non-owner) is covered by the controller and integration tests.
 *
 * <p>The request is the empty message, except for RPCs that validate their input before asking
 * who is calling ({@link #VALID_REQUESTS}): an empty request would stop at {@code INVALID_ARGUMENT}
 * there and prove nothing. If a new RPC fails here with {@code INVALID_ARGUMENT}, either resolve
 * the caller before validating or add a valid request for it.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19114", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RpcAuthorizationCoverageTest {

    /** Not business RPCs: liveness for orchestrators (any valid token outside {@code local}) and grpcurl's reflection. */
    private static final Set<String> INFRASTRUCTURE_SERVICES = Set.of(
            "grpc.health.v1.Health", "grpc.reflection.v1.ServerReflection", "grpc.reflection.v1alpha.ServerReflection");

    private static final String USERS = UserServiceGrpc.SERVICE_NAME + "/";
    private static final String PROBE_EMAIL = "probe@rpc-coverage.example.com";
    private static final String PASSWORD = "supersecret1";
    private static final String VALID_CPF = "11144477735";
    private static final long NO_SUCH_USER = Long.MAX_VALUE;

    /** The token contract's exceptions: who may call what without a user token. */
    private static final Map<Probe, Set<String>> ALLOWED = Map.of(
            Probe.ANONYMOUS, Set.of(USERS + "ListUsers", "vertice.exercise.v1.ExerciseService/ListMuscleGroups"),
            Probe.BFF, Set.of(USERS + "VerifyCredentials", USERS + "CreateUser", USERS + "SetPasswordWithToken",
                    USERS + "RedeemPasswordSetupToken"),
            Probe.OPS, Set.of(USERS + "ListUsers", USERS + "CreatePasswordSetupToken"));

    private static final Map<String, Message> VALID_REQUESTS = Map.of(
            USERS + "CreateUser", UserCreateRequest.newBuilder().setName("Probe").setEmail(PROBE_EMAIL)
                    .setPassword(PASSWORD).setCpf(VALID_CPF).setRole(Role.CLIENT).setAcceptedTermsVersion("probe").build(),
            USERS + "UpdateUser", UpdateUserRequest.newBuilder().setId(NO_SUCH_USER).setUser(UserRequest.newBuilder()
                    .setName("Probe").setEmail(PROBE_EMAIL).setCpf(VALID_CPF).setRole(Role.CLIENT)).build(),
            USERS + "SetUserPassword", SetUserPasswordRequest.newBuilder().setId(NO_SUCH_USER).setPassword(PASSWORD).build(),
            USERS + "VerifyCredentials", VerifyCredentialsRequest.newBuilder().setEmail(PROBE_EMAIL).setPassword(PASSWORD).build(),
            USERS + "CreatePasswordSetupToken", CreatePasswordSetupTokenRequest.newBuilder().setUserId(NO_SUCH_USER).build(),
            USERS + "SetPasswordWithToken", SetPasswordWithTokenRequest.newBuilder().setToken("probe").setPassword(PASSWORD).build(),
            USERS + "RedeemPasswordSetupToken", RedeemPasswordSetupTokenRequest.newBuilder().setToken("probe").setPassword(PASSWORD).build());

    private enum Probe {
        ANONYMOUS(null, Status.Code.UNAUTHENTICATED),
        BFF(ServiceSubject.BFF, Status.Code.PERMISSION_DENIED),
        OPS(ServiceSubject.OPS, Status.Code.PERMISSION_DENIED);

        private final ServiceSubject subject;
        private final Status.Code refusal;

        Probe(ServiceSubject subject, Status.Code refusal) {
            this.subject = subject;
            this.refusal = refusal;
        }

        Optional<String> token() {
            return Optional.ofNullable(subject).map(TestJwts::serviceToken);
        }
    }

    @Autowired
    private List<BindableService> services;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;

    @BeforeAll
    void openChannel() {
        channel = NettyChannelBuilder.forTarget("localhost:19114").usePlaintext().build();
    }

    @AfterAll
    void closeChannel() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        // Only a regression would have created it, but don't leave it behind if one did.
        jdbc.update("DELETE FROM users WHERE email = :email", Map.of("email", PROBE_EMAIL));
    }

    @TestFactory
    Stream<DynamicTest> everyRpc_refusesCallersTheContractDoesNotAllow() {
        List<MethodDescriptor<?, ?>> methods = boundMethods();
        assertThat(methods).extracting(MethodDescriptor::getFullMethodName)
                .contains("vertice.trainerclient.v1.TrainerClientService/ListClientsForTrainer",
                        "vertice.exercise.v1.ExerciseService/ListMuscleGroups");

        List<DynamicTest> tests = new ArrayList<>();
        for (MethodDescriptor<?, ?> method : methods) {
            for (Probe probe : Probe.values()) {
                if (ALLOWED.get(probe).contains(method.getFullMethodName())) {
                    continue;
                }
                tests.add(DynamicTest.dynamicTest(method.getFullMethodName() + " as " + probe,
                        () -> assertCode(() -> call(method, probe), probe.refusal)));
            }
        }
        return tests.stream();
    }

    private List<MethodDescriptor<?, ?>> boundMethods() {
        return services.stream()
                .map(BindableService::bindService)
                .filter(definition -> !INFRASTRUCTURE_SERVICES.contains(definition.getServiceDescriptor().getName()))
                .flatMap(definition -> definition.getMethods().stream())
                .<MethodDescriptor<?, ?>>map(method -> method.getMethodDescriptor())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void call(MethodDescriptor<?, ?> method, Probe probe) {
        assertThat(method.getType()).as("only unary RPCs are probed").isEqualTo(MethodDescriptor.MethodType.UNARY);
        var descriptor = (MethodDescriptor<Object, Object>) method;
        Object request = VALID_REQUESTS.containsKey(method.getFullMethodName())
                ? VALID_REQUESTS.get(method.getFullMethodName())
                : ((MethodDescriptor.PrototypeMarshaller<?>) method.getRequestMarshaller()).getMessagePrototype();
        ClientCalls.blockingUnaryCall(channelFor(probe), descriptor,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), request);
    }

    private Channel channelFor(Probe probe) {
        return probe.token().<Channel>map(token -> {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
            return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers));
        }).orElse(channel);
    }
}
