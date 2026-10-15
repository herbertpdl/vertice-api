package com.vertice.api.user;

import com.vertice.api.generated.grpc.user.v1.ExportUserDataRequest;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * The export's row bound (vertice.export.max-rows, set to 1 here): a section past it refuses the
 * whole export with RESOURCE_EXHAUSTED instead of returning a truncated document, and the message
 * names the section and limit, not the data.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19124", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.export.max-rows=1"})
@ActiveProfiles("local")
class UserDataExportLimitsTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        jdbc.update("DELETE FROM trainer_clients WHERE trainer_id IN (:ids)", Map.of("ids", users));
        jdbc.update("DELETE FROM users WHERE id IN (:ids)", Map.of("ids", users));
    }

    @Test
    void aSectionPastTheRowBound_refusesTheWholeExport() {
        User trainer = save("Trainer", Role.TRAINER);
        link(trainer, save("First Client", Role.CLIENT));
        link(trainer, save("Second Client", Role.CLIENT));
        channel = NettyChannelBuilder.forTarget("localhost:19124").usePlaintext().build();

        assertThatThrownBy(() -> TestJwts.asCaller(UserServiceGrpc.newBlockingStub(channel), trainer.getId(), Role.TRAINER)
                .exportUserData(ExportUserDataRequest.getDefaultInstance()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
                    assertThat(ex.getStatus().getDescription())
                            .isEqualTo("Data export too large for one response: relationships exceed 1 rows");
                });
    }

    private User save(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(' ', '-') + "-" + System.nanoTime() + "@export-limits.example.com");
        user.setCpf(role == Role.TRAINER ? "%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)) : null);
        user.setRole(role);
        user = userRepository.save(user);
        users.add(user.getId());
        return user;
    }

    private void link(User trainer, User client) {
        TrainerClient link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        trainerClientRepository.save(link);
    }
}
