package com.vertice.api.user;

import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.ConsentState;
import com.vertice.api.generated.grpc.user.v1.ConsentType;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.RecordConsentRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * P1-04 end to end, as production runs it (no {@code local} profile, real database): CPF optional
 * for clients and required for trainers, the Terms version stored at registration, and consent
 * recorded by the user themself and shown back to them only. Data is committed (the gRPC call runs
 * on another thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19119", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + UserConsentIntegrationTest.SECRET})
class UserConsentIntegrationTest {

    static final String SECRET = "non-local-consent-test-secret-32-bytes!";
    private static final String PASSWORD = "supersecret1";

    @Autowired
    private UserRepository userRepository;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub users;
    private TrainerClientServiceGrpc.TrainerClientServiceBlockingStub trainerClients;
    private final List<Long> clientIds = new ArrayList<>();
    private final List<Long> trainerIds = new ArrayList<>();

    private UserResponse trainer;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19119").usePlaintext().build();
        users = UserServiceGrpc.newBlockingStub(channel);
        trainerClients = TrainerClientServiceGrpc.newBlockingStub(channel);
        trainer = register(UserCreateRequest.newBuilder().setCpf(uniqueCpf()).setAcceptedTermsVersion("2026-10-14"));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        // Clients first: their trainer_clients rows cascade, which frees the trainers.
        userRepository.deleteAllById(clientIds);
        userRepository.deleteAllById(trainerIds);
    }

    @Test
    void registration_storesTheTermsVersionWithTheTime_andTheRowTimestamps() {
        User stored = userRepository.findById(trainer.getId()).orElseThrow();

        assertThat(stored.getAcceptedTermsVersion()).isEqualTo("2026-10-14");
        assertThat(stored.getTermsAcceptedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(stored.getHealthDataConsentAt()).isNull();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
    }

    @Test
    void aTrainerWithoutCpf_isRefused() {
        assertCode(() -> TestJwts.withBearer(users, TestJwts.serviceToken(SECRET, ServiceSubject.BFF))
                .createUser(UserCreateRequest.newBuilder().setName("Trainer").setEmail(uniqueEmail("trainer"))
                        .setPassword(PASSWORD).setAcceptedTermsVersion("2026-10-14")
                        .setRole(com.vertice.api.generated.grpc.user.v1.Role.TRAINER).build()),
                Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void clientsWithoutCpf_areCreated_andACpfIsStillUnique() {
        UserResponse first = createClient("");
        UserResponse second = createClient("");
        String cpf = uniqueCpf();
        createClient(cpf);

        assertThat(first.getCpf()).isEmpty();
        assertThat(second.getCpf()).isEmpty();
        assertThat(userRepository.findById(first.getId()).orElseThrow().getCpf()).isNull();
        assertCode(() -> createClient(cpf), Status.Code.ALREADY_EXISTS);
    }

    @Test
    void consent_isRecordedByTheUserThemself_andShownToThemOnly() {
        UserResponse client = createClient("");
        var asClient = TestJwts.withBearer(users, TestJwts.token(SECRET, client.getId(), Role.CLIENT));
        GetUserRequest getClient = GetUserRequest.newBuilder().setId(client.getId()).build();

        assertThat(asClient.getUser(getClient).getConsent()).isEqualTo(ConsentState.getDefaultInstance());

        asClient.recordConsent(RecordConsentRequest.newBuilder().setType(ConsentType.TERMS).setVersion("2026-10-14").build());
        ConsentState state = asClient.recordConsent(RecordConsentRequest.newBuilder()
                .setType(ConsentType.HEALTH_DATA).setVersion("2026-10-14").build());

        assertThat(state.getAcceptedTermsVersion()).isEqualTo("2026-10-14");
        assertThat(state.getHealthDataConsentVersion()).isEqualTo("2026-10-14");
        assertThat(Instant.parse(state.getHealthDataConsentAt())).isBeforeOrEqualTo(Instant.now());
        assertThat(asClient.getUser(getClient).getConsent()).isEqualTo(state);
        // Their active trainer gets the detail view, which leaves the consent state out.
        assertThat(asTrainer().getUser(getClient).hasConsent()).isFalse();
    }

    @Test
    void consent_cannotBeRecordedByAService() {
        assertCode(() -> TestJwts.withBearer(users, TestJwts.serviceToken(SECRET, ServiceSubject.BFF))
                .recordConsent(RecordConsentRequest.newBuilder().setType(ConsentType.TERMS).setVersion("v1").build()),
                Status.Code.PERMISSION_DENIED);
    }

    private UserResponse register(UserCreateRequest.Builder request) {
        UserResponse created = TestJwts.withBearer(users, TestJwts.serviceToken(SECRET, ServiceSubject.BFF))
                .createUser(request.setName("Trainer").setEmail(uniqueEmail("trainer")).setPassword(PASSWORD)
                        .setRole(com.vertice.api.generated.grpc.user.v1.Role.TRAINER).build());
        trainerIds.add(created.getId());
        return created;
    }

    private UserResponse createClient(String cpf) {
        UserResponse created = TestJwts.withBearer(trainerClients, TestJwts.token(SECRET, trainer.getId(), Role.TRAINER))
                .createClientForTrainer(CreateClientForTrainerRequest.newBuilder().setTrainerId(trainer.getId())
                        .setName("Client").setEmail(uniqueEmail("client")).setCpf(cpf).build());
        clientIds.add(created.getId());
        return created;
    }

    private UserServiceGrpc.UserServiceBlockingStub asTrainer() {
        return TestJwts.withBearer(users, TestJwts.token(SECRET, trainer.getId(), Role.TRAINER));
    }

    private static void assertCode(ThrowingCallable callable, Status.Code code) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(code);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@consent.example.com";
    }

    /** A valid CPF (check digits computed) that is unlikely to collide with other tests' rows. */
    private static String uniqueCpf() {
        String base = "%09d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000L));
        int first = checkDigit(base);
        int second = checkDigit(base + first);
        return base + first + second;
    }

    private static int checkDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * (digits.length() + 1 - i);
        }
        int digit = 11 - sum % 11;
        return digit >= 10 ? 0 : digit;
    }
}
