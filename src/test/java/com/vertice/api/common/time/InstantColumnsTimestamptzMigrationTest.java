package com.vertice.api.common.time;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Re-runs V31 on existing data. Flyway has already applied it, so each test first puts every
 * {@code Instant} column back in its pre-V31 shape — bare {@code TIMESTAMP}, holding UTC wall clock,
 * with the two {@code DEFAULT now()} clauses as they were — seeds a row with known values, runs the
 * real migration file, and rolls everything back.
 *
 * <p>What matters here is that the conversion is a *reinterpretation*, not a rewrite: each stored
 * wall-clock reading keeps the instant it already meant. That holds only because V31 says
 * {@code USING col AT TIME ZONE 'UTC'}; a bare {@code ALTER COLUMN … TYPE TIMESTAMPTZ} would
 * resolve against the migrating session's zone and shift every historic row. The
 * {@code hostileTimeZoneTest} task runs this class at UTC+14, which is what proves the difference.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19126", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class InstantColumnsTimestamptzMigrationTest {

    private static final String MIGRATION = "db/migration/V31__instant_columns_to_timestamptz.sql";

    /** Every column V31 converts, as {@code table.column}. */
    private static final List<String> INSTANT_COLUMNS = List.of(
            "workout_logs.started_at", "workout_logs.completed_at",
            "set_logs.recorded_at",
            "workout_feedback.created_at",
            "password_setup_tokens.created_at", "password_setup_tokens.expires_at",
            "password_setup_tokens.used_at",
            "trainer_clients.started_at", "trainer_clients.ended_at",
            "users.terms_accepted_at", "users.health_data_consent_at",
            "users.created_at", "users.updated_at", "users.deleted_at");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * A row written the old way keeps its instant. The 2018 value sits inside Brazil's former DST
     * window, where reinterpreting through the business zone (rather than UTC) would be off by two
     * hours rather than the usual three.
     */
    @Test
    void existingWallClockValues_keepTheInstantTheyAlreadyMeant() {
        inRolledBackTransaction(() -> {
            revertToPreV31();
            Long id = jdbc.queryForObject("""
                    INSERT INTO users (name, email, role, created_at, updated_at, deleted_at)
                    VALUES ('pre-v31', ?, 'CLIENT',
                            TIMESTAMP '2018-01-15 02:30:00',
                            TIMESTAMP '2026-03-02 10:15:30.123456',
                            TIMESTAMP '2019-11-03 01:00:00')
                    RETURNING id
                    """, Long.class, "tz-migration-" + System.nanoTime() + "@example.com");

            jdbc.execute(migrationSql());

            assertThat(instantOf(id, "created_at")).isEqualTo(Instant.parse("2018-01-15T02:30:00Z"));
            assertThat(instantOf(id, "updated_at")).isEqualTo(Instant.parse("2026-03-02T10:15:30.123456Z"));
            assertThat(instantOf(id, "deleted_at")).isEqualTo(Instant.parse("2019-11-03T01:00:00Z"));
        });
    }

    @Test
    void afterMigration_everyInstantColumnCarriesItsZone() {
        inRolledBackTransaction(() -> {
            revertToPreV31();
            for (String column : INSTANT_COLUMNS) {
                assertThat(dataTypeOf(column)).as("%s before V31", column).isEqualTo("timestamp without time zone");
            }

            jdbc.execute(migrationSql());

            for (String column : INSTANT_COLUMNS) {
                assertThat(dataTypeOf(column)).as("%s after V31", column).isEqualTo("timestamp with time zone");
            }
        });
    }

    /**
     * The restated {@code DEFAULT now()} is the point of the last three statements: ALTER TYPE
     * re-casts the old default through the new type, which would keep the broken
     * session-zone-dependent reading.
     */
    @Test
    void afterMigration_theDatabaseSideDefaultStoresTheTrueInstant() {
        inRolledBackTransaction(() -> {
            revertToPreV31();

            jdbc.execute(migrationSql());

            Long id = jdbc.queryForObject("""
                    INSERT INTO users (name, email, role) VALUES ('post-v31 default', ?, 'CLIENT')
                    RETURNING id
                    """, Long.class, "tz-migration-" + System.nanoTime() + "@example.com");
            assertThat(instantOf(id, "created_at")).isCloseTo(Instant.now(), within(5, ChronoUnit.MINUTES));
            assertThat(instantOf(id, "updated_at")).isCloseTo(Instant.now(), within(5, ChronoUnit.MINUTES));
        });
    }

    /**
     * Puts the schema back the way V30 left it: bare TIMESTAMP holding UTC wall clock, and the two
     * {@code DEFAULT now()} clauses that silently picked up the session's zone.
     */
    private void revertToPreV31() {
        for (String column : INSTANT_COLUMNS) {
            String[] parts = column.split("\\.", 2);
            jdbc.execute("ALTER TABLE %s ALTER COLUMN %s TYPE TIMESTAMP USING %s AT TIME ZONE 'UTC'"
                    .formatted(parts[0], parts[1], parts[1]));
        }
        jdbc.execute("ALTER TABLE trainer_clients ALTER COLUMN started_at SET DEFAULT now()");
        jdbc.execute("ALTER TABLE users ALTER COLUMN created_at SET DEFAULT now()");
        jdbc.execute("ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT now()");
    }

    private Instant instantOf(Long userId, String column) {
        OffsetDateTime value = jdbc.queryForObject(
                "SELECT %s FROM users WHERE id = ?".formatted(column), OffsetDateTime.class, userId);
        return value == null ? null : value.toInstant();
    }

    private String dataTypeOf(String tableAndColumn) {
        String[] parts = tableAndColumn.split("\\.", 2);
        return jdbc.queryForObject("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, parts[0], parts[1]);
    }

    private static String migrationSql() {
        try {
            return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void inRolledBackTransaction(Runnable body) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        });
    }
}
