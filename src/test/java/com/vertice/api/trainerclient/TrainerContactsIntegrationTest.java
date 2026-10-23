package com.vertice.api.trainerclient;

import com.vertice.api.generated.grpc.trainerclient.v1.ClientTrainerResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.EndTrainerClientRelationshipRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.GetClientTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.DeleteAccountRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * Trainer contacts (client workout experience R74-R79): a trainer sets WhatsApp and Instagram and
 * chooses what their clients see; the client reads their active trainer's card with only the shown
 * contacts; a field left out of an update keeps its value; nobody else sets contacts.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19122", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + TrainerContactsIntegrationTest.SECRET})
class TrainerContactsIntegrationTest {

    static final String SECRET = "non-local-trainer-contacts-test-secret!";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private User trainer;
    private User client;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19122").usePlaintext().build();
        trainer = userRepository.save(newUser("Rafael Lima", Role.TRAINER));
        client = userRepository.save(newUser("Marina", Role.CLIENT));
        TrainerClient link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        trainerClientRepository.save(link);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, List<Long>> users = Map.of("ids", List.of(trainer.getId(), client.getId()));
        jdbc.update("DELETE FROM trainer_clients WHERE trainer_id IN (:ids)", users);
        jdbc.update("DELETE FROM users WHERE id IN (:ids)", users);
    }

    @Test
    void theClientSeesOnlyTheContactsTheTrainerShows() {
        updateTrainer(base(trainer).setWhatsapp("11987654321").setInstagram("rafael.treina")
                .setShowEmail(false).setShowWhatsapp(true).setShowInstagram(false));

        ClientTrainerResponse card = trainerCard(client, 0);
        assertThat(card.getTrainerId()).isEqualTo(trainer.getId());
        assertThat(card.getName()).isEqualTo("Rafael Lima");
        assertThat(card.getWhatsapp()).isEqualTo("11987654321");
        assertThat(card.getEmail()).isEmpty();
        assertThat(card.getInstagram()).isEmpty();

        UserResponse self = as(trainer, UserServiceGrpc::newBlockingStub)
                .getUser(GetUserRequest.newBuilder().setId(trainer.getId()).build());
        assertThat(self.getInstagram()).isEqualTo("rafael.treina");
        assertThat(self.getShowWhatsapp()).isTrue();
        assertThat(self.getShowInstagram()).isFalse();
    }

    @Test
    void contactsAreHiddenByDefault_andAnUpdateWithoutThemKeepsThem() {
        assertThat(trainerCard(client, client.getId()).getEmail()).isEmpty();

        updateTrainer(base(trainer).setShowEmail(true).setInstagram("rafael.treina").setShowInstagram(true));
        updateTrainer(base(trainer).setName("Rafael L."));

        ClientTrainerResponse card = trainerCard(client, 0);
        assertThat(card.getName()).isEqualTo("Rafael L.");
        assertThat(card.getEmail()).isEqualTo(trainer.getEmail());
        assertThat(card.getInstagram()).isEqualTo("rafael.treina");
    }

    @Test
    void onlyTheClientThemself_andOnlyWithAnActiveTrainer() {
        assertCode(() -> as(trainer, TrainerClientServiceGrpc::newBlockingStub)
                .getClientTrainer(GetClientTrainerRequest.newBuilder().setClientId(client.getId()).build()), Status.Code.PERMISSION_DENIED);
        assertCode(() -> trainerCard(client, client.getId() + 1_000_000), Status.Code.PERMISSION_DENIED);

        as(trainer, TrainerClientServiceGrpc::newBlockingStub).endTrainerClientRelationship(
                EndTrainerClientRelationshipRequest.newBuilder().setTrainerId(trainer.getId()).setClientId(client.getId()).build());
        assertCode(() -> trainerCard(client, 0), Status.Code.NOT_FOUND);
    }

    @Test
    void contactsAreTrainersOnly_andValidated() {
        assertCode(() -> as(trainer, UserServiceGrpc::newBlockingStub).updateUser(UpdateUserRequest.newBuilder()
                .setId(client.getId()).setUser(base(client).setWhatsapp("11987654321")).build()), Status.Code.INVALID_ARGUMENT);
        assertCode(() -> updateTrainer(base(trainer).setWhatsapp("(11) 98765-4321")), Status.Code.INVALID_ARGUMENT);
        assertCode(() -> updateTrainer(base(trainer).setInstagram("@rafael")), Status.Code.INVALID_ARGUMENT);
    }

    /** E35: a contact switched on but left empty isn't shown; ADMIN reads any client's card. */
    @Test
    void anEmptyContactSwitchedOn_isLeftOut_andAdminReadsAnyCard() {
        updateTrainer(base(trainer).setShowWhatsapp(true).setShowInstagram(true).setInstagram("rafael.treina"));
        User admin = userRepository.save(newUser("Admin", Role.ADMIN));
        try {
            ClientTrainerResponse card = trainerCard(admin, client.getId());
            assertThat(card.getWhatsapp()).isEmpty();
            assertThat(card.getInstagram()).isEqualTo("rafael.treina");
            assertCode(() -> trainerCard(admin, 0), Status.Code.INVALID_ARGUMENT);
        } finally {
            userRepository.deleteById(admin.getId());
        }
    }

    /** Deleting the account clears the contacts (LGPD), and the trainer's card is gone for the client. */
    @Test
    void deletingTheTrainersAccount_clearsTheContacts() {
        updateTrainer(base(trainer).setWhatsapp("11987654321").setInstagram("rafael.treina")
                .setShowEmail(true).setShowWhatsapp(true).setShowInstagram(true));
        as(trainer, TrainerClientServiceGrpc::newBlockingStub).endTrainerClientRelationship(
                EndTrainerClientRelationshipRequest.newBuilder().setTrainerId(trainer.getId()).setClientId(client.getId()).build());

        as(trainer, UserServiceGrpc::newBlockingStub).deleteAccount(DeleteAccountRequest.getDefaultInstance());

        User deleted = userRepository.findById(trainer.getId()).orElseThrow();
        assertThat(deleted.getWhatsapp()).isNull();
        assertThat(deleted.getInstagram()).isNull();
        assertThat(deleted.isShowEmail() || deleted.isShowWhatsapp() || deleted.isShowInstagram()).isFalse();
    }

    /** A role change away from TRAINER drops the contacts: switching back never re-exposes them. */
    @Test
    void leavingTheTrainerRole_clearsTheContacts() {
        updateTrainer(base(trainer).setWhatsapp("11987654321").setInstagram("rafael.treina")
                .setShowEmail(true).setShowWhatsapp(true).setShowInstagram(true));
        as(trainer, TrainerClientServiceGrpc::newBlockingStub).endTrainerClientRelationship(
                EndTrainerClientRelationshipRequest.newBuilder().setTrainerId(trainer.getId()).setClientId(client.getId()).build());
        User admin = userRepository.save(newUser("Admin", Role.ADMIN));
        try {
            as(admin, UserServiceGrpc::newBlockingStub).updateUser(UpdateUserRequest.newBuilder().setId(trainer.getId())
                    .setUser(base(trainer).setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT)).build());
        } finally {
            userRepository.deleteById(admin.getId());
        }

        User demoted = userRepository.findById(trainer.getId()).orElseThrow();
        assertThat(demoted.getWhatsapp()).isNull();
        assertThat(demoted.getInstagram()).isNull();
        assertThat(demoted.isShowEmail() || demoted.isShowWhatsapp() || demoted.isShowInstagram()).isFalse();
    }

    private ClientTrainerResponse trainerCard(User caller, long clientId) {
        return as(caller, TrainerClientServiceGrpc::newBlockingStub)
                .getClientTrainer(GetClientTrainerRequest.newBuilder().setClientId(clientId).build());
    }

    private void updateTrainer(UserRequest.Builder user) {
        as(trainer, UserServiceGrpc::newBlockingStub)
                .updateUser(UpdateUserRequest.newBuilder().setId(trainer.getId()).setUser(user).build());
    }

    private static UserRequest.Builder base(User user) {
        return UserRequest.newBuilder().setName(user.getName()).setEmail(user.getEmail())
                .setCpf(user.getCpf() == null ? "" : user.getCpf())
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.valueOf(user.getRole().name()));
    }

    private void assertCode(ThrowingCallable call, Status.Code code) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(code);
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "-" + System.nanoTime() + "@trainer-contacts.example.com");
        user.setCpf(validCpf());
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }

    /** A random CPF with valid check digits (UpdateUser validates it). */
    private static String validCpf() {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) {
            d[i] = ThreadLocalRandom.current().nextInt(10);
        }
        for (int k = 9; k < 11; k++) {
            int sum = 0;
            for (int i = 0; i < k; i++) {
                sum += d[i] * (k + 1 - i);
            }
            int r = (sum * 10) % 11;
            d[k] = r == 10 ? 0 : r;
        }
        StringBuilder cpf = new StringBuilder();
        for (int digit : d) {
            cpf.append(digit);
        }
        return cpf.toString();
    }
}
