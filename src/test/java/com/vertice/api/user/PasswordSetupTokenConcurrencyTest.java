package com.vertice.api.user;

import com.vertice.api.common.exception.InvalidPasswordSetupTokenException;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two requests racing on the same setup token: each runs in its own committed transaction (no
 * test-level {@code @Transactional}), so this exercises the real row lock behind the conditional
 * "mark used" UPDATE. The user — and with it, by cascade, the token — is deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19110", "spring.datasource.hikari.maximum-pool-size=4"})
@ActiveProfiles("local")
class PasswordSetupTokenConcurrencyTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PasswordSetupTokenService tokenService;

    private Long clientId;

    @AfterEach
    void deleteClient() {
        if (clientId != null) {
            userService.deleteUser(clientId);
        }
    }

    @Test
    void twoConcurrentUsesOfOneToken_exactlyOneSucceeds() throws Exception {
        String email = "race-" + System.nanoTime() + "@example.com";
        clientId = userService.createUser(UserCreateRequest.newBuilder()
                .setName("Client").setEmail(email).setRole(Role.CLIENT)
                .setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L))).build())
                .getId();
        String token = tokenService.createToken(clientId).getToken();

        CountDownLatch start = new CountDownLatch(1);
        Callable<String> first = useTokenAfter(start, token, "firstPassword1");
        Callable<String> second = useTokenAfter(start, token, "secondPassword1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(pool.submit(first), pool.submit(second));
            start.countDown();
            List<String> outcomes = List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(outcome -> outcome.startsWith("set:")).hasSize(1);
            assertThat(outcomes).filteredOn("rejected"::equals).hasSize(1);

            String winner = outcomes.stream().filter(outcome -> outcome.startsWith("set:")).findFirst().orElseThrow()
                    .substring("set:".length());
            assertThat(userService.verifyCredentials(email, winner).getId()).isEqualTo(clientId);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<String> useTokenAfter(CountDownLatch start, String token, String password) {
        return () -> {
            start.await();
            try {
                tokenService.setPasswordWithToken(token, password);
                return "set:" + password;
            } catch (InvalidPasswordSetupTokenException e) {
                return "rejected";
            }
        };
    }
}
