package com.vertice.api.plan.session;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Re-runs V32 on existing data. Flyway has already applied it, so each test first puts
 * {@code workout_logs} and {@code workout_feedback} back in their pre-V32 shape, seeds rows, runs
 * the file's SQL, and rolls everything back.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19123", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class WorkoutLogStatusMigrationTest {

    private static final String MIGRATION = "db/migration/V32__workout_log_status_and_feedback_rating.sql";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void existingLogs_areBackfilledFromCompletedAt() {
        inRolledBackTransaction(() -> {
            long workout = seedWorkout();
            long open = insertLog(workout, "2026-03-02", false);
            long done = insertLog(workout, "2026-03-09", true);

            jdbc.execute(migrationSql());

            assertThat(row(open)).containsEntry("status", "IN_PROGRESS").containsEntry("completion_kind", null);
            assertThat(row(done)).containsEntry("status", "COMPLETED").containsEntry("completion_kind", "CLIENT");
        });
    }

    @Test
    void afterMigration_oneCompletedAndOneInProgressPerWeek_butNotTwoCompleted() {
        inRolledBackTransaction(() -> {
            long workout = seedWorkout();
            jdbc.execute(migrationSql());
            insertCompleted(workout, "2026-03-02");
            insertLog(workout, "2026-03-02", false);

            assertThatThrownBy(() -> insertCompleted(workout, "2026-03-02"))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_workout_logs_completed");
        });
    }

    @Test
    void afterMigration_aCompletedStatusWithoutItsTime_isRefused() {
        inRolledBackTransaction(() -> {
            long workout = seedWorkout();
            jdbc.execute(migrationSql());
            long open = insertLog(workout, "2026-03-02", false);
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE workout_logs SET status = 'COMPLETED', completion_kind = 'CLIENT' WHERE id = ?", open))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("ck_workout_logs_completion");
        });
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT status, completion_kind FROM workout_logs WHERE id = ?", id);
    }

    /** Puts both tables back the way V31 left them, inside the caller's transaction. */
    private void restorePreV32Schema() {
        jdbc.execute("DELETE FROM workout_feedback WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE status = 'SUPERSEDED')");
        jdbc.execute("DELETE FROM set_logs WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE status = 'SUPERSEDED')");
        jdbc.execute("DELETE FROM workout_logs WHERE status = 'SUPERSEDED'");
        jdbc.execute("DROP INDEX uq_workout_logs_in_progress, uq_workout_logs_completed, idx_workout_logs_in_progress_week, "
                + "idx_workout_logs_workout_client_week");
        jdbc.execute("""
                ALTER TABLE workout_logs
                    DROP CONSTRAINT ck_workout_logs_status,
                    DROP CONSTRAINT ck_workout_logs_completion,
                    DROP COLUMN status,
                    DROP COLUMN completion_kind
                """);
        jdbc.execute("DELETE FROM workout_logs l USING workout_logs o WHERE l.workout_id = o.workout_id "
                + "AND l.client_id = o.client_id AND l.week_start_date = o.week_start_date AND l.id > o.id "
                + "AND NOT EXISTS (SELECT 1 FROM set_logs s WHERE s.workout_log_id = l.id) "
                + "AND NOT EXISTS (SELECT 1 FROM workout_feedback f WHERE f.workout_log_id = l.id)");
        jdbc.execute("ALTER TABLE workout_logs ADD CONSTRAINT uq_workout_logs_workout_client_week UNIQUE (workout_id, client_id, week_start_date)");
        jdbc.execute("ALTER TABLE workout_feedback DROP CONSTRAINT ck_workout_feedback_rating, DROP COLUMN rating");
    }

    private void inRolledBackTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            restorePreV32Schema();
            body.run();
        });
    }

    private long seedWorkout() {
        long trainer = insertUser("TRAINER");
        long client = insertUser("CLIENT");
        long plan = jdbc.queryForObject("""
                INSERT INTO training_plans (name, trainer_id, client_id, start_date, end_date, level)
                VALUES ('V32 plan', ?, ?, DATE '2026-01-01', DATE '2026-12-31', 'BEGINNER') RETURNING id
                """, Long.class, trainer, client);
        long workout = jdbc.queryForObject(
                "INSERT INTO workouts (name, training_plan_id, day_of_week) VALUES ('V32', ?, 'MONDAY') RETURNING id", Long.class, plan);
        clientOf.put(workout, client);
        return workout;
    }

    private final Map<Long, Long> clientOf = new java.util.HashMap<>();

    private long insertLog(long workout, String week, boolean completed) {
        return jdbc.queryForObject("""
                INSERT INTO workout_logs (workout_id, client_id, week_start_date, started_at, completed_at)
                VALUES (?, ?, CAST(? AS DATE), now(), CASE WHEN ? THEN now() END) RETURNING id
                """, Long.class, workout, clientOf.get(workout), week, completed);
    }

    private long insertCompleted(long workout, String week) {
        return jdbc.queryForObject("""
                INSERT INTO workout_logs (workout_id, client_id, week_start_date, started_at, completed_at, status, completion_kind)
                VALUES (?, ?, CAST(? AS DATE), now(), now(), 'COMPLETED', 'CLIENT') RETURNING id
                """, Long.class, workout, clientOf.get(workout), week);
    }

    private long insertUser(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, cpf, role)
                VALUES ('Migration Test', ?, ?, ?)
                RETURNING id
                """, Long.class, "v32-" + System.nanoTime() + "@migration.example.com",
                "%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)), role);
    }

    private static String migrationSql() {
        try {
            return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
