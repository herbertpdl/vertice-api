package com.vertice.api.trainerclient;

import com.vertice.api.generated.grpc.plan.v1.PlanLevel;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.GetExerciseProgressRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.vertice.api.plan.PlanCallers.assertCode;
import static com.vertice.api.plan.PlanCallers.assertDenied;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trainer-client rules end to end, as production runs them: no {@code local} profile, the real
 * decoder, real {@code trainer_clients} rows. Trainer A has client A; trainer B is the outsider.
 * Data is committed (the gRPC call runs on another thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19113", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + TrainerClientAuthorizationIntegrationTest.SECRET})
class TrainerClientAuthorizationIntegrationTest {

    static final String SECRET = "non-local-trainer-client-test-secret!";
    private static final String EMAIL_DOMAIN = "@trainer-client-authz.example.com";
    /** No user has this id; ADMIN's rules never look the caller up. */
    private static final long ADMIN_ID = 999_999_999L;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private User trainerA;
    private User clientA;
    private User trainerB;
    private TrainerClient linkA;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19113").usePlaintext().build();
        trainerA = userRepository.save(newUser("TrainerA", Role.TRAINER, "hash"));
        // No password yet, so the setup-token rule hinges on the roster link alone.
        clientA = userRepository.save(newUser("ClientA", Role.CLIENT, null));
        trainerB = userRepository.save(newUser("TrainerB", Role.TRAINER, "hash"));
        linkA = new TrainerClient();
        linkA.setTrainer(trainerA);
        linkA.setClient(clientA);
        linkA.setStartedAt(Instant.now());
        linkA = trainerClientRepository.save(linkA);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, Object> params = Map.of("ids", List.of(trainerA.getId(), clientA.getId(), trainerB.getId()),
                "domain", "%" + EMAIL_DOMAIN);
        jdbc.update("DELETE FROM training_plans WHERE trainer_id IN (:ids)", params);
        jdbc.update("DELETE FROM trainer_clients WHERE trainer_id IN (:ids)", params);
        jdbc.update("DELETE FROM password_setup_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE :domain)", params);
        jdbc.update("DELETE FROM users WHERE email LIKE :domain", params);
    }

    @Test
    void theTrainer_seesAndGrowsTheirOwnRoster() {
        TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asA = as(trainerA, TrainerClientServiceGrpc::newBlockingStub);

        assertThat(asA.listClientsForTrainer(listFor(trainerA)).getClientsList())
                .extracting(UserResponse::getId).containsExactly(clientA.getId());
        assertThat(asA.isTrainersClient(isClient(trainerA, clientA)).getIsClient()).isTrue();

        UserResponse created = asA.createClientForTrainer(createFor(trainerA, "new-of-a"));
        assertThat(asA.listClientsForTrainer(listFor(trainerA)).getClientsList())
                .extracting(UserResponse::getId).containsExactlyInAnyOrder(clientA.getId(), created.getId());
    }

    @Test
    void anotherTrainer_isPermissionDenied_onTheRoster_andCreatesNoClientThere() {
        TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asB = as(trainerB, TrainerClientServiceGrpc::newBlockingStub);

        assertDenied(() -> asB.listClientsForTrainer(listFor(trainerA)));
        assertDenied(() -> asB.isTrainersClient(isClient(trainerA, clientA)));
        assertDenied(() -> asB.createClientForTrainer(createFor(trainerA, "planted-by-b")));

        // B asking about A's client on B's own roster is allowed, and the answer is just "no".
        assertThat(asB.isTrainersClient(isClient(trainerB, clientA)).getIsClient()).isFalse();
        assertThat(asB.listClientsForTrainer(listFor(trainerB)).getClientsList()).isEmpty();
        assertThat(userRepository.findByEmail("planted-by-b" + EMAIL_DOMAIN)).isEmpty();
        assertThat(as(trainerA, TrainerClientServiceGrpc::newBlockingStub)
                .listClientsForTrainer(listFor(trainerA)).getClientsList())
                .extracting(UserResponse::getId).containsExactly(clientA.getId());
    }

    @Test
    void theClient_andServiceIdentities_getNothing_whileAdminReadsAnyRoster() {
        TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asClient = as(clientA, TrainerClientServiceGrpc::newBlockingStub);
        assertDenied(() -> asClient.listClientsForTrainer(listFor(trainerA)));
        assertDenied(() -> asClient.isTrainersClient(isClient(trainerA, clientA)));
        assertDenied(() -> asClient.createClientForTrainer(createFor(trainerA, "planted-by-client")));

        for (ServiceSubject service : ServiceSubject.values()) {
            TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asService = TestJwts.withBearer(
                    TrainerClientServiceGrpc.newBlockingStub(channel), TestJwts.serviceToken(SECRET, service));
            assertDenied(() -> asService.listClientsForTrainer(listFor(trainerA)));
            assertDenied(() -> asService.isTrainersClient(isClient(trainerA, clientA)));
        }

        TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asAdmin = TestJwts.withBearer(
                TrainerClientServiceGrpc.newBlockingStub(channel), TestJwts.token(SECRET, ADMIN_ID, Role.ADMIN));
        assertThat(asAdmin.isTrainersClient(isClient(trainerA, clientA)).getIsClient()).isTrue();
        assertThat(asAdmin.listClientsForTrainer(listFor(trainerA)).getClientsList())
                .extracting(UserResponse::getId).containsExactly(clientA.getId());
    }

    /**
     * Once the relationship ended, every rule that requires an active link refuses trainer A. The
     * row is ended directly in the database: no RPC ends a relationship yet (P1-05).
     */
    @Test
    void anEndedRelationship_endsEveryActiveLinkPrivilege() {
        jdbc.update("UPDATE trainer_clients SET ended_at = now() WHERE id = :id", Map.of("id", linkA.getId()));
        TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asA = as(trainerA, TrainerClientServiceGrpc::newBlockingStub);
        UserServiceGrpc.UserServiceBlockingStub usersAsA = as(trainerA, UserServiceGrpc::newBlockingStub);

        assertThat(asA.isTrainersClient(isClient(trainerA, clientA)).getIsClient()).isFalse();
        assertThat(asA.listClientsForTrainer(listFor(trainerA)).getClientsList()).isEmpty();

        // No plan was ever written for client A, so not even the name-only GetUser view applies.
        assertDenied(() -> usersAsA.getUser(GetUserRequest.newBuilder().setId(clientA.getId()).build()));
        assertDenied(() -> usersAsA.updateUser(UpdateUserRequest.newBuilder().setId(clientA.getId())
                .setUser(UserRequest.newBuilder().setName("Renamed").setEmail(clientA.getEmail()).setCpf(clientA.getCpf())
                        .setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT)).build()));
        assertDenied(() -> usersAsA.createPasswordSetupToken(
                CreatePasswordSetupTokenRequest.newBuilder().setUserId(clientA.getId()).build()));
        assertThat(usersAsA.listUsersWithoutPassword(ListUsersRequest.getDefaultInstance()).getUsersList())
                .extracting(UserResponse::getId).doesNotContain(clientA.getId());
        assertDenied(() -> as(trainerA, WorkoutSessionServiceGrpc::newBlockingStub).getExerciseProgress(
                GetExerciseProgressRequest.newBuilder().setClientId(clientA.getId()).setExerciseId(1L).build()));
        // A new plan needs an active client: a business rule in the service, hence FAILED_PRECONDITION.
        assertCode(() -> as(trainerA, TrainingPlanServiceGrpc::newBlockingStub).createTrainingPlan(
                TrainingPlanCreateRequest.newBuilder().setName("After the end").setTrainerId(trainerA.getId())
                        .setClientId(clientA.getId()).setStartDate("2026-01-01").setEndDate("2026-12-31")
                        .setLevel(PlanLevel.BEGINNER).build()), Status.Code.FAILED_PRECONDITION);

        assertThat(userRepository.findById(clientA.getId()).orElseThrow().getName()).isEqualTo("ClientA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM training_plans WHERE trainer_id = :id",
                Map.of("id", trainerA.getId()), Long.class)).isZero();
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private static ListClientsForTrainerRequest listFor(User trainer) {
        return ListClientsForTrainerRequest.newBuilder().setTrainerId(trainer.getId()).build();
    }

    private static IsTrainersClientRequest isClient(User trainer, User client) {
        return IsTrainersClientRequest.newBuilder().setTrainerId(trainer.getId()).setClientId(client.getId()).build();
    }

    private static CreateClientForTrainerRequest createFor(User trainer, String emailPrefix) {
        return CreateClientForTrainerRequest.newBuilder().setTrainerId(trainer.getId()).setName("New Client")
                .setEmail(emailPrefix + EMAIL_DOMAIN).setCpf(uniqueCpf()).build();
    }

    private static User newUser(String name, Role role, String passwordHash) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + EMAIL_DOMAIN);
        user.setCpf(uniqueCpf());
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        return user;
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
