package com.vertice.api.user;

import com.vertice.api.generated.grpc.plan.v1.PlanLevel;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersResponse;
import com.vertice.api.generated.grpc.user.v1.ListUsersWithoutPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.VerifyCredentialsRequest;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * P1-03 end to end, as production runs it (no {@code local} profile, real rows): lists never carry a
 * CPF, {@code GetUser} returns the detail view only to the user themself, ADMIN and the active
 * trainer, {@code ListUsers} pages through every user, and a duplicate never echoes the value.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19116", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + UserDataMinimisationIntegrationTest.SECRET})
class UserDataMinimisationIntegrationTest {

    static final String SECRET = "non-local-data-minimisation-secret-32!";
    private static final String EMAIL_DOMAIN = "@data-minimisation.example.com";
    private static final String PASSWORD = "supersecret1";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private User admin;
    private User trainer;
    private User otherTrainer;
    private User client;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19116").usePlaintext().build();
        admin = userRepository.save(newUser("Admin", Role.ADMIN, passwordEncoder.encode(PASSWORD)));
        trainer = userRepository.save(newUser("Trainer", Role.TRAINER, passwordEncoder.encode(PASSWORD)));
        trainer.setCref("123456-G/SP");
        trainer = userRepository.save(trainer);
        otherTrainer = userRepository.save(newUser("OtherTrainer", Role.TRAINER, passwordEncoder.encode(PASSWORD)));
        // No password yet, so ListUsersWithoutPassword lists them.
        client = userRepository.save(newUser("Client", Role.CLIENT, null));
        link(trainer, client);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, Object> params = Map.of("domain", "%" + EMAIL_DOMAIN);
        String ours = "SELECT id FROM users WHERE email LIKE :domain";
        jdbc.update("DELETE FROM training_plans WHERE trainer_id IN (" + ours + ")", params);
        jdbc.update("DELETE FROM trainer_clients WHERE trainer_id IN (" + ours + ")", params);
        jdbc.update("DELETE FROM users WHERE email LIKE :domain", params);
    }

    @Test
    void listUsers_pagesThroughEveryUser_inTheContactView() {
        UserServiceGrpc.UserServiceBlockingStub ops = asService(ServiceSubject.OPS);
        List<UserResponse> seen = new ArrayList<>();
        String token = "";
        do {
            ListUsersResponse page = ops.listUsers(ListUsersRequest.newBuilder().setPageSize(2).setPageToken(token).build());
            assertThat(page.getUsersCount()).isLessThanOrEqualTo(2);
            seen.addAll(page.getUsersList());
            token = page.getNextPageToken();
        } while (!token.isEmpty());

        assertThat(seen).extracting(UserResponse::getId)
                .contains(admin.getId(), trainer.getId(), otherTrainer.getId(), client.getId())
                .doesNotHaveDuplicates()
                .isSorted();
        assertThat(seen).extracting(UserResponse::getCpf).containsOnly("");
        assertThat(seen).extracting(UserResponse::getCref).containsOnly("");
        // The ops setup-link script hands each link to its client by email.
        assertThat(seen).filteredOn(user -> user.getId() == client.getId())
                .extracting(UserResponse::getEmail).containsExactly(client.getEmail());
    }

    @Test
    void listUsers_withARoleFilter_pagesThroughThatRoleOnly() {
        ListUsersResponse page = asService(ServiceSubject.OPS).listUsers(ListUsersRequest.newBuilder()
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT).setPageSize(UserService.MAX_PAGE_SIZE * 10).build());

        assertThat(page.getUsersCount()).isLessThanOrEqualTo(UserService.MAX_PAGE_SIZE);
        assertThat(page.getUsersList()).extracting(UserResponse::getRole)
                .containsOnly(com.vertice.api.generated.grpc.user.v1.Role.CLIENT);
    }

    @Test
    void theRosterAndTheSetupLinkList_carryNoEmailOrCpf() {
        List<UserResponse> roster = as(trainer, TrainerClientServiceGrpc::newBlockingStub)
                .listClientsForTrainer(ListClientsForTrainerRequest.newBuilder().setTrainerId(trainer.getId()).build())
                .getClientsList();
        assertThat(roster).containsExactly(UserResponse.newBuilder().setId(client.getId()).setName("Client")
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT).build());

        List<UserResponse> withoutPassword = as(trainer, UserServiceGrpc::newBlockingStub)
                .listUsersWithoutPassword(ListUsersWithoutPasswordRequest.getDefaultInstance()).getUsersList();
        assertThat(withoutPassword).containsExactly(UserResponse.newBuilder().setId(client.getId()).setName("Client")
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT).build());
    }

    @Test
    void getUser_returnsTheDetailView_onlyToSelfAdminAndTheActiveTrainer() {
        GetUserRequest getClient = GetUserRequest.newBuilder().setId(client.getId()).build();
        for (User allowed : List.of(client, admin, trainer)) {
            UserResponse response = as(allowed, UserServiceGrpc::newBlockingStub).getUser(getClient);
            assertThat(response.getCpf()).as("CPF for %s", allowed.getName()).isEqualTo(client.getCpf());
            assertThat(response.getEmail()).isEqualTo(client.getEmail());
        }
        assertThat(as(trainer, UserServiceGrpc::newBlockingStub)
                .getUser(GetUserRequest.newBuilder().setId(trainer.getId()).build()).getCref()).isEqualTo("123456-G/SP");

        // Another trainer has no link at all: refused.
        StatusRuntimeException refused = catchThrowableOfType(StatusRuntimeException.class,
                () -> as(otherTrainer, UserServiceGrpc::newBlockingStub).getUser(getClient));
        assertThat(refused.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void getUser_aFormerTrainerWithAPlan_getsTheSummaryOnly() {
        as(trainer, TrainingPlanServiceGrpc::newBlockingStub).createTrainingPlan(TrainingPlanCreateRequest.newBuilder()
                .setName("Plan").setTrainerId(trainer.getId()).setClientId(client.getId())
                .setStartDate("2026-01-01").setEndDate("2026-12-31").setLevel(PlanLevel.BEGINNER).build());
        jdbc.update("UPDATE trainer_clients SET ended_at = now() WHERE client_id = :id", Map.of("id", client.getId()));

        UserResponse response = as(trainer, UserServiceGrpc::newBlockingStub)
                .getUser(GetUserRequest.newBuilder().setId(client.getId()).build());

        assertThat(response).isEqualTo(UserResponse.newBuilder().setId(client.getId()).setName("Client")
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT).build());
    }

    @Test
    void loginAndRegistration_returnTheContactView() {
        UserServiceGrpc.UserServiceBlockingStub bff = asService(ServiceSubject.BFF);

        UserResponse loggedIn = bff.verifyCredentials(VerifyCredentialsRequest.newBuilder()
                .setEmail(trainer.getEmail()).setPassword(PASSWORD).build());
        assertThat(loggedIn.getEmail()).isEqualTo(trainer.getEmail());
        assertThat(loggedIn.getCpf()).isEmpty();
        assertThat(loggedIn.getCref()).isEmpty();

        UserResponse registered = bff.createUser(registration("registered" + System.nanoTime() + EMAIL_DOMAIN, uniqueCpf()));
        assertThat(registered.getEmail()).endsWith(EMAIL_DOMAIN);
        assertThat(registered.getCpf()).isEmpty();
    }

    @Test
    void duplicateEmailOrCpf_atRegistration_neverEchoesTheValue() {
        UserServiceGrpc.UserServiceBlockingStub bff = asService(ServiceSubject.BFF);

        StatusRuntimeException duplicateEmail = catchThrowableOfType(StatusRuntimeException.class,
                () -> bff.createUser(registration(trainer.getEmail(), uniqueCpf())));
        StatusRuntimeException duplicateCpf = catchThrowableOfType(StatusRuntimeException.class,
                () -> bff.createUser(registration("fresh" + System.nanoTime() + EMAIL_DOMAIN, trainer.getCpf())));

        assertThat(duplicateEmail.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(duplicateEmail.getStatus().getDescription()).isEqualTo("Email already in use");
        assertThat(duplicateCpf.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(duplicateCpf.getStatus().getDescription()).isEqualTo("CPF already in use");
        for (StatusRuntimeException ex : List.of(duplicateEmail, duplicateCpf)) {
            assertThat(ex.getStatus().getDescription()).doesNotContainPattern("\\d").doesNotContain("@");
        }
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private UserServiceGrpc.UserServiceBlockingStub asService(ServiceSubject subject) {
        return TestJwts.withBearer(UserServiceGrpc.newBlockingStub(channel), TestJwts.serviceToken(SECRET, subject));
    }

    private void link(User trainer, User client) {
        TrainerClient link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        trainerClientRepository.save(link);
    }

    private static UserCreateRequest registration(String email, String cpf) {
        return UserCreateRequest.newBuilder().setName("Registered").setEmail(email).setPassword(PASSWORD).setCpf(cpf)
                .setAcceptedTermsVersion("2026-10-14").setRole(com.vertice.api.generated.grpc.user.v1.Role.TRAINER).build();
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
