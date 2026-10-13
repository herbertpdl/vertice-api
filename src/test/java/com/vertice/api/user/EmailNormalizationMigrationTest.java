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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Re-runs V25 on mixed-case data. Flyway has already applied it, so each test first puts the
 * {@code users} table back in its pre-V25 shape (case-sensitive {@code uq_users_email}, no
 * {@code lower(email)} index), seeds rows, runs the file's SQL, and rolls everything back.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19109", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class EmailNormalizationMigrationTest {

    private static final String MIGRATION = "db/migration/V25__normalize_user_emails_and_optional_password.sql";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void mixedCaseEmails_areLowerCasedAndTrimmed_andLegacyEmptyHashesBecomeNull() {
        inRolledBackTransaction(() -> {
            String suffix = suffix();
            long mixed = insertUser("  Ana." + suffix + "@Example.COM ", "$2a$10$hash");
            long lower = insertUser("bia." + suffix + "@example.com", "");

            jdbc.execute(migrationSql());

            assertThat(emailOf(mixed)).isEqualTo("ana." + suffix + "@example.com");
            assertThat(emailOf(lower)).isEqualTo("bia." + suffix + "@example.com");
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, mixed))
                    .isEqualTo("$2a$10$hash");
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, lower))
                    .isNull();
        });
    }

    @Test
    void afterMigration_anEmailDifferingOnlyByCase_isRejected() {
        inRolledBackTransaction(() -> {
            String suffix = suffix();
            insertUser("ana." + suffix + "@example.com", "$2a$10$hash");
            jdbc.execute(migrationSql());

            assertThatThrownBy(() -> insertUser("ANA." + suffix + "@example.com", "$2a$10$hash"))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("uq_users_email_lower");
        });
    }

    @Test
    void caseInsensitiveCollision_failsTheMigrationWithTheUserIds() {
        inRolledBackTransaction(() -> {
            String suffix = suffix();
            long first = insertUser("Ana." + suffix + "@example.com", "$2a$10$hash");
            long second = insertUser("ana." + suffix + "@example.com ", "$2a$10$hash");

            assertThatThrownBy(() -> jdbc.execute(migrationSql()))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("users share an email when compared case-insensitively")
                    .hasMessageContaining("[" + first + ", " + second + "]")
                    .hasMessageNotContaining(suffix);
        });
    }

    /** Puts {@code users} back the way V24 left it, inside the caller's transaction. */
    private void restorePreV25Schema() {
        jdbc.execute("DROP INDEX uq_users_email_lower");
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email)");
    }

    private void inRolledBackTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            restorePreV25Schema();
            body.run();
        });
    }

    private long insertUser(String email, String passwordHash) {
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, cpf, password_hash, role)
                VALUES ('Migration Test', ?, ?, ?, 'CLIENT')
                RETURNING id
                """, Long.class, email, "%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)), passwordHash);
    }

    private String emailOf(long id) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id);
    }

    private static String suffix() {
        return Long.toString(System.nanoTime());
    }

    private static String migrationSql() {
        try {
            return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
