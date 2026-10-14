package com.vertice.api.grpc;

import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * A still-unexpired token stops working the moment its user is demoted or deleted: no {@code local}
 * profile, the real decoder, and the change made straight in the database.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19115", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + StaleSessionIntegrationTest.SECRET})
class StaleSessionIntegrationTest {

    static final String SECRET = "non-local-stale-session-test-secret!";
    private static final String EMAIL_DOMAIN = "@stale-session.example.com";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private ManagedChannel channel;
    private User trainer;
    private TrainerClientServiceGrpc.TrainerClientServiceBlockingStub asTrainer;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19115").usePlaintext().build();
        User user = new User();
        user.setName("Trainer");
        user.setEmail("trainer-" + System.nanoTime() + EMAIL_DOMAIN);
        user.setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)));
        user.setPasswordHash("hash");
        user.setRole(Role.TRAINER);
        trainer = userRepository.save(user);
        asTrainer = TestJwts.withBearer(TrainerClientServiceGrpc.newBlockingStub(channel),
                TestJwts.token(SECRET, trainer.getId(), Role.TRAINER));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + EMAIL_DOMAIN);
    }

    @Test
    void trainerDemotedToClient_isSignedOutOnTheNextCall() {
        assertThat(asTrainer.listClientsForTrainer(ownRoster()).getClientsList()).isEmpty();

        jdbc.update("UPDATE users SET role = 'CLIENT' WHERE id = ?", trainer.getId());

        assertSessionNoLongerValid();
    }

    @Test
    void deletedTrainer_isSignedOutOnTheNextCall() {
        assertThat(asTrainer.listClientsForTrainer(ownRoster()).getClientsList()).isEmpty();

        jdbc.update("DELETE FROM users WHERE id = ?", trainer.getId());

        assertSessionNoLongerValid();
    }

    private ListClientsForTrainerRequest ownRoster() {
        return ListClientsForTrainerRequest.newBuilder().setTrainerId(trainer.getId()).build();
    }

    private void assertSessionNoLongerValid() {
        assertThatThrownBy(() -> asTrainer.listClientsForTrainer(ownRoster()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Session no longer valid");
                });
    }
}
