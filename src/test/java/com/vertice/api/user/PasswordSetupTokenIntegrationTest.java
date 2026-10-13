package com.vertice.api.user;

import com.vertice.api.common.exception.InvalidCredentialsException;
import com.vertice.api.common.exception.InvalidPasswordSetupTokenException;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenResponse;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The password setup lifecycle against a real database: the conditional "mark used" UPDATE, the
 * expiry check and the {@code lower(email)} lookup all live in SQL, so mocks can't prove them. Each
 * test runs in a transaction that is rolled back.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19108", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@Transactional
class PasswordSetupTokenIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PasswordSetupTokenService tokenService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordSetupProperties properties;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager entityManager;

    private long clientId;
    private String email;

    @BeforeEach
    void createClientWithoutPassword() {
        email = "client-" + System.nanoTime() + "@example.com";
        clientId = userService.createUser(UserCreateRequest.newBuilder()
                .setName("Client").setEmail(email.toUpperCase()).setCpf(uniqueCpf()).setRole(Role.CLIENT).build())
                .getId();
    }

    @Test
    void clientCreatedWithoutPassword_isStoredNormalized_andCannotLogIn() {
        User client = userRepository.findById(clientId).orElseThrow();
        assertThat(client.getEmail()).isEqualTo(email);
        assertThat(client.getPasswordHash()).isNull();
        assertThat(userService.listUsersWithoutPassword(com.vertice.api.user.Role.CLIENT))
                .extracting(response -> response.getId()).contains(clientId);

        assertThatThrownBy(() -> userService.verifyCredentials(email, "anything123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void issuedToken_isUrlSafe_storedHashed_andExpiresAfterTheConfiguredTtl() {
        Instant before = Instant.now();
        CreatePasswordSetupTokenResponse issued = tokenService.createToken(clientId);

        assertThat(issued.getToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Instant.parse(issued.getExpiresAt()))
                .isBetween(before.plus(properties.tokenTtl()).minusSeconds(5), Instant.now().plus(properties.tokenTtl()));
        assertThat(jdbc.queryForList("SELECT token_hash FROM password_setup_tokens WHERE user_id = ?", String.class, clientId))
                .containsExactly(PasswordSetupTokenService.sha256Hex(issued.getToken()))
                .doesNotContain(issued.getToken());
    }

    @Test
    void usingToken_setsPassword_thenClientLogsInWithAnyEmailCase() {
        String token = tokenService.createToken(clientId).getToken();

        tokenService.setPasswordWithToken(token, "myNewPassword1");

        assertThat(userService.verifyCredentials("  " + email.toUpperCase() + " ", "myNewPassword1").getId())
                .isEqualTo(clientId);
        assertThatThrownBy(() -> userService.verifyCredentials(email, "wrongPassword1"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(userService.listUsersWithoutPassword(null)).extracting(response -> response.getId()).doesNotContain(clientId);
    }

    @Test
    void reusingToken_isRejected_andKeepsTheFirstPassword() {
        String token = tokenService.createToken(clientId).getToken();
        tokenService.setPasswordWithToken(token, "firstPassword1");

        assertThatThrownBy(() -> tokenService.setPasswordWithToken(token, "secondPassword1"))
                .isInstanceOf(InvalidPasswordSetupTokenException.class);
        assertThat(userService.verifyCredentials(email, "firstPassword1").getId()).isEqualTo(clientId);
    }

    @Test
    void expiredToken_isRejected() {
        String token = tokenService.createToken(clientId).getToken();
        // Relative to created_at, so the test doesn't depend on how the JVM and DB time zones line up.
        jdbc.update("UPDATE password_setup_tokens SET expires_at = created_at - INTERVAL '1 minute' WHERE user_id = ?", clientId);

        assertThatThrownBy(() -> tokenService.setPasswordWithToken(token, "myNewPassword1"))
                .isInstanceOf(InvalidPasswordSetupTokenException.class);
        assertThat(userRepository.findById(clientId).orElseThrow().getPasswordHash()).isNull();
    }

    @Test
    void newToken_invalidatesThePreviousUnusedOne() {
        String oldToken = tokenService.createToken(clientId).getToken();
        String newToken = tokenService.createToken(clientId).getToken();

        assertThatThrownBy(() -> tokenService.setPasswordWithToken(oldToken, "myNewPassword1"))
                .isInstanceOf(InvalidPasswordSetupTokenException.class);
        tokenService.setPasswordWithToken(newToken, "myNewPassword1");
        assertThat(userService.verifyCredentials(email, "myNewPassword1").getId()).isEqualTo(clientId);
    }

    @Test
    void unknownToken_isRejectedWithTheSameError() {
        assertThatThrownBy(() -> tokenService.setPasswordWithToken("not-a-real-token", "myNewPassword1"))
                .isInstanceOf(InvalidPasswordSetupTokenException.class)
                .hasMessage("Invalid or expired password setup token");
    }

    @Test
    void deletingTheUser_deletesTheirTokens() {
        tokenService.createToken(clientId);
        // DeleteUser runs in its own transaction in production; start from an empty persistence
        // context the same way, so the delete reaches the database and its ON DELETE CASCADE.
        entityManager.flush();
        entityManager.clear();

        userService.deleteUser(clientId);
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_setup_tokens WHERE user_id = ?", Integer.class, clientId))
                .isZero();
    }

    /** cpf is only format-checked at the gRPC edge, so any unique 11 digits will do here. */
    private static String uniqueCpf() {
        return "%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L));
    }
}
