package com.vertice.api.user;

import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.VerifyCredentialsRequest;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.trainerclient.TrainerClientService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * The user-domain rules end to end, as production runs them: no {@code local} profile (a scoped
 * token is required on every call), the real decoder, and real trainer↔client rows. Data is
 * committed (the gRPC call runs on another thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19111", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + UserAuthorizationIntegrationTest.SECRET})
class UserAuthorizationIntegrationTest {

    static final String SECRET = "non-local-authz-test-secret-32-bytes!";
    private static final String PASSWORD = "supersecret1";

    @Autowired
    private UserService userService;
    @Autowired
    private TrainerClientService trainerClientService;
    @Autowired
    private UserRepository userRepository;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub anonymous;
    private final List<Long> clientIds = new ArrayList<>();
    private final List<Long> trainerIds = new ArrayList<>();

    private UserResponse trainer;
    private UserResponse otherTrainer;
    private UserResponse client;
    private UserResponse otherTrainersClient;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19111").usePlaintext().build();
        anonymous = UserServiceGrpc.newBlockingStub(channel);

        trainer = newTrainer();
        otherTrainer = newTrainer();
        client = newClientOf(trainer);
        otherTrainersClient = newClientOf(otherTrainer);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        // Clients first: their trainer_clients rows cascade, which frees the trainers.
        userRepository.deleteAllById(clientIds);
        userRepository.deleteAllById(trainerIds);
    }

    @Test
    void listUsers_withTrainerToken_isPermissionDenied() {
        assertCode(() -> as(trainer).listUsers(ListUsersRequest.getDefaultInstance()), Status.Code.PERMISSION_DENIED);
    }

    @Test
    void listUsers_withOpsToken_isAllowed() {
        assertThat(asService(ServiceSubject.OPS).listUsers(ListUsersRequest.getDefaultInstance()).getUsersList())
                .extracting(UserResponse::getId)
                .contains(trainer.getId(), client.getId());
    }

    @Test
    void getUser_ownClient_isAllowed_butAnotherTrainersClient_isPermissionDenied() {
        assertThat(as(trainer).getUser(GetUserRequest.newBuilder().setId(client.getId()).build()).getId())
                .isEqualTo(client.getId());

        assertCode(() -> as(trainer).getUser(GetUserRequest.newBuilder().setId(otherTrainersClient.getId()).build()),
                Status.Code.PERMISSION_DENIED);
    }

    @Test
    void listUsersWithoutPassword_withTrainerToken_onlyListsTheirOwnClients() {
        List<Long> ids = as(trainer).listUsersWithoutPassword(ListUsersRequest.getDefaultInstance()).getUsersList().stream()
                .map(UserResponse::getId)
                .toList();

        assertThat(ids).containsExactly(client.getId());
    }

    @Test
    void createPasswordSetupToken_trainerForOwnClient_isAllowed_butNotForAnotherTrainersClient() {
        assertThat(as(trainer).createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder()
                .setUserId(client.getId()).build()).getToken()).isNotBlank();

        assertCode(() -> as(trainer).createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder()
                .setUserId(otherTrainersClient.getId()).build()), Status.Code.PERMISSION_DENIED);
    }

    @Test
    void verifyCredentials_asBffService_logsIn_butAUserTokenCannotCheckSomeoneElse() {
        VerifyCredentialsRequest login = VerifyCredentialsRequest.newBuilder()
                .setEmail(trainer.getEmail()).setPassword(PASSWORD).build();

        assertThat(asService(ServiceSubject.BFF).verifyCredentials(login).getId()).isEqualTo(trainer.getId());
        assertThat(as(trainer).verifyCredentials(login).getId()).isEqualTo(trainer.getId());
        assertCode(() -> as(otherTrainer).verifyCredentials(login), Status.Code.PERMISSION_DENIED);
    }

    @Test
    void unscopedOrMissingToken_isUnauthenticated() {
        String withoutAudience = TestJwts.sign(SECRET, "{\"sub\":\"%d\",\"role\":\"TRAINER\",\"iss\":\"vertice-bff\",\"exp\":%d}"
                .formatted(trainer.getId(), System.currentTimeMillis() / 1000 + 3600));

        assertCode(() -> TestJwts.withBearer(anonymous, withoutAudience).getUser(GetUserRequest.newBuilder()
                .setId(trainer.getId()).build()), Status.Code.UNAUTHENTICATED);
        assertCode(() -> anonymous.listUsers(ListUsersRequest.getDefaultInstance()), Status.Code.UNAUTHENTICATED);
    }

    private UserServiceGrpc.UserServiceBlockingStub as(UserResponse user) {
        return TestJwts.withBearer(anonymous, TestJwts.token(SECRET, user.getId(), Role.valueOf(user.getRole().name())));
    }

    private UserServiceGrpc.UserServiceBlockingStub asService(ServiceSubject subject) {
        return TestJwts.withBearer(anonymous, TestJwts.serviceToken(SECRET, subject));
    }

    private UserResponse newTrainer() {
        UserResponse created = userService.createUser(UserCreateRequest.newBuilder()
                .setName("Trainer").setEmail(uniqueEmail("trainer")).setPassword(PASSWORD).setCpf(uniqueCpf())
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.TRAINER).build());
        trainerIds.add(created.getId());
        return created;
    }

    private UserResponse newClientOf(UserResponse trainer) {
        UserResponse created = trainerClientService.createClientForTrainer(CreateClientForTrainerRequest.newBuilder()
                .setTrainerId(trainer.getId()).setName("Client").setEmail(uniqueEmail("client")).setCpf(uniqueCpf()).build());
        clientIds.add(created.getId());
        return created;
    }

    private static void assertCode(ThrowingCallable callable, Status.Code code) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(code);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@authz.example.com";
    }

    private static String uniqueCpf() {
        return "%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L));
    }
}
