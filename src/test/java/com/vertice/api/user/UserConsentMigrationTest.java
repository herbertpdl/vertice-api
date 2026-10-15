package com.vertice.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Re-runs V27 on existing data. Flyway has already applied it, so each test first puts the
 * {@code users} table back in its pre-V27 shape (no consent or timestamp columns, {@code cpf NOT
 * NULL} with the {@code uq_users_cpf} constraint), seeds rows, runs the file's SQL, and rolls
 * everything back.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19118", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class UserConsentMigrationTest {

    private static final String MIGRATION = "db/migration/V27__user_consent_optional_cpf_and_timestamps.sql";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void existingRows_getTimestamps_noConsent_andKeepTheirCpf() {
        inRolledBackTransaction(() -> {
            String cpf = uniqueCpf();
            long id = insertUser("TRAINER", cpf);

            jdbc.execute(migrationSql());

            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT cpf, created_at, updated_at, accepted_terms_version, terms_accepted_at,
                           health_data_consent_version, health_data_consent_at
                      FROM users WHERE id = ?
                    """, id);
            assertThat(row.get("cpf")).isEqualTo(cpf);
            assertThat(row.get("created_at")).isInstanceOf(Timestamp.class);
            assertThat(row.get("updated_at")).isInstanceOf(Timestamp.class);
            assertThat(row.get("accepted_terms_version")).isNull();
            assertThat(row.get("terms_accepted_at")).isNull();
            assertThat(row.get("health_data_consent_version")).isNull();
            assertThat(row.get("health_data_consent_at")).isNull();
        });
    }

    @Test
    void afterMigration_manyUsersWithoutCpf_areAllowed() {
        inRolledBackTransaction(() -> {
            jdbc.execute(migrationSql());

            insertUser("CLIENT", null);
            insertUser("CLIENT", null);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE cpf IS NULL", Integer.class)).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void afterMigration_aDuplicateCpf_isStillRefused() {
        inRolledBackTransaction(() -> {
            jdbc.execute(migrationSql());
            String cpf = uniqueCpf();
            insertUser("CLIENT", cpf);

            assertThatThrownBy(() -> insertUser("CLIENT", cpf))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("uq_users_cpf");
        });
    }

    @Test
    void afterMigration_aTrainerWithoutCpf_isRefused() {
        inRolledBackTransaction(() -> {
            jdbc.execute(migrationSql());

            assertThatThrownBy(() -> insertUser("TRAINER", null))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("ck_users_trainer_cpf");
        });
    }

    @Test
    void afterMigration_aTermsVersionWithoutItsTime_isRefused() {
        inRolledBackTransaction(() -> {
            jdbc.execute(migrationSql());
            long id = insertUser("CLIENT", null);

            assertThatThrownBy(() -> jdbc.update("UPDATE users SET accepted_terms_version = 'v1' WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("ck_users_terms_acceptance");
        });
    }

    @Test
    void afterMigration_aHealthDataConsentTimeWithoutItsVersion_isRefused() {
        inRolledBackTransaction(() -> {
            jdbc.execute(migrationSql());
            long id = insertUser("CLIENT", null);

            assertThatThrownBy(() -> jdbc.update("UPDATE users SET health_data_consent_at = now() WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("ck_users_health_data_consent");
        });
    }

    /**
     * Puts {@code users} back the way V26 left it, inside the caller's transaction. Rows other tests
     * left without a CPF get a placeholder so {@code NOT NULL} can come back (rolled back anyway).
     */
    private void restorePreV27Schema() {
        jdbc.execute("DROP INDEX uq_users_cpf");
        jdbc.execute("""
                ALTER TABLE users
                    DROP CONSTRAINT ck_users_trainer_cpf,
                    DROP COLUMN accepted_terms_version,
                    DROP COLUMN terms_accepted_at,
                    DROP COLUMN health_data_consent_version,
                    DROP COLUMN health_data_consent_at,
                    DROP COLUMN created_at,
                    DROP COLUMN updated_at
                """);
        jdbc.execute("UPDATE users SET cpf = '9' || lpad(id::TEXT, 10, '0') WHERE cpf IS NULL");
        jdbc.execute("ALTER TABLE users ALTER COLUMN cpf SET NOT NULL");
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT uq_users_cpf UNIQUE (cpf)");
    }

    private void inRolledBackTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            restorePreV27Schema();
            body.run();
        });
    }

    private long insertUser(String role, String cpf) {
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, cpf, role)
                VALUES ('Migration Test', ?, ?, ?)
                RETURNING id
                """, Long.class, "v27-" + System.nanoTime() + "@migration.example.com", cpf, role);
    }

    private static String uniqueCpf() {
        return "%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L));
    }

    private static String migrationSql() {
        try {
            return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
