package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Proves V23's keep/copy/refile/remove routine (spec §3 V23, §7 PR4). The shipped keep-list is
 * empty, so on a real boot the routine only removes; here the file's SQL is re-executed with a
 * test-supplied keep-list spliced in after the {@code -- @keep-list} marker, on top of a fixture,
 * inside a transaction that is always rolled back — nothing it deletes survives the test.
 *
 * <p>Fixture: trainers T1 &lt; T2, one plan/workout/client session each. X-kept is used by both
 * workouts, X-solo by T2 only, X-drop by T1 only, X-idle by nobody; X-kept, X-solo and X-idle are
 * on the keep-list. Every used exercise has one prescribed set with one logged value, and each
 * session has feedback.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19106", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class PreStarterCleanupMigrationTest {

    private static final String MIGRATION = "db/migration/V23__remove_pre_starter_exercises.sql";
    private static final String KEEP_LIST_MARKER = "-- @keep-list\n";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ExerciseService exerciseService;

    @Test
    void unkeptExercise_removedWithEntriesSetsAndLogs() {
        runWithFixture(f -> {
            assertThat(exists("exercises", f.drop.exerciseId)).isFalse();
            assertThat(exists("workout_exercises", f.drop.entryId)).isFalse();
            assertThat(exists("exercise_sets", f.drop.setId)).isFalse();
            assertThat(exists("set_logs", f.drop.setLogId)).isFalse();
        });
    }

    @Test
    void sessionsAndFeedback_survive() {
        runWithFixture(f -> {
            assertThat(exists("workout_logs", f.t1.workoutLogId)).isTrue();
            assertThat(exists("workout_logs", f.t2.workoutLogId)).isTrue();
            assertThat(exists("workout_feedback", f.t1.feedbackId)).isTrue();
            assertThat(exists("workout_feedback", f.t2.feedbackId)).isTrue();
            assertThat(exists("workouts", f.t1.workoutId)).isTrue();
            assertThat(exists("training_plans", f.t2.planId)).isTrue();
        });
    }

    @Test
    void keptExerciseUsedByOneTrainer_becomesPrivateToThatTrainer() {
        runWithFixture(f -> {
            assertThat(ownerOf(f.solo.exerciseId)).isEqualTo(f.t2.trainerId);
            assertThat(exerciseIdOf(f.solo.entryId)).isEqualTo(f.solo.exerciseId);
            assertThat(countByName("X-solo")).isEqualTo(1);
            assertThat(exists("set_logs", f.solo.setLogId)).isTrue();
        });
    }

    @Test
    void keptExerciseUsedByTwoTrainers_oneCopyPerTrainer_entriesAndLogsFollow() {
        runWithFixture(f -> {
            // The lowest trainer id keeps the original row, and its entry still points there.
            assertThat(ownerOf(f.kept.exerciseId)).isEqualTo(f.t1.trainerId);
            assertThat(exerciseIdOf(f.kept.entryId)).isEqualTo(f.kept.exerciseId);

            // T2 gets one identical copy; its entry (same id) now points at the copy.
            List<Long> copies = jdbc.queryForList(
                    "SELECT id FROM exercises WHERE name = 'X-kept' AND owner_id = ?", Long.class, f.t2.trainerId);
            assertThat(copies).hasSize(1);
            Long copyId = copies.getFirst();
            assertThat(copyId).isNotEqualTo(f.kept.exerciseId);
            assertThat(jdbc.queryForObject("SELECT description FROM exercises WHERE id = ?", String.class, copyId))
                    .isEqualTo("kept description");
            assertThat(exerciseIdOf(f.keptT2EntryId)).isEqualTo(copyId);

            // Prescribed sets and logged values hang off the unchanged entry ids.
            assertThat(exists("exercise_sets", f.keptT2SetId)).isTrue();
            assertThat(exists("set_logs", f.keptT2SetLogId)).isTrue();
            assertThat(exists("set_logs", f.kept.setLogId)).isTrue();
        });
    }

    @Test
    void keptExerciseGroups_filedFromKeepList_noPrimary_orderNull() {
        runWithFixture(f -> {
            Long copyId = jdbc.queryForObject(
                    "SELECT id FROM exercises WHERE name = 'X-kept' AND owner_id = ?", Long.class, f.t2.trainerId);
            for (Long exerciseId : List.of(f.kept.exerciseId, copyId)) {
                assertThat(jdbc.query("""
                                SELECT mg.name, emg.is_primary, emg.catalog_order
                                FROM exercise_muscle_groups emg
                                JOIN muscle_groups mg ON mg.id = emg.muscle_group_id
                                WHERE emg.exercise_id = ?
                                ORDER BY mg.id
                                """,
                        (rs, i) -> tuple(rs.getString(1), rs.getBoolean(2), rs.getObject(3)), exerciseId))
                        .containsExactly(tuple("Peito", false, null), tuple("Tríceps", false, null));
            }
        });
    }

    @Test
    void keptExercise_readThroughService_groupsInIdOrder_notStarter() {
        runWithFixture(f -> {
            // The keep-list names Tríceps (id 5) before Peito (id 1); with no primary the API lists by id.
            // The lowest trainer (T1) keeps the original row, so it is T1's own exercise.
            CallerIdentity owner = new CallerIdentity(f.t1.trainerId, Role.TRAINER);
            ExerciseResponse kept = exerciseService.getExercise(owner, f.kept.exerciseId);
            assertThat(kept.getIsStarter()).isFalse();
            assertThat(kept.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                    .containsExactly(tuple(1L, "Peito"), tuple(5L, "Tríceps"));

            assertThat(exerciseService.listExercises(owner, 0, ""))
                    .filteredOn(exercise -> exercise.getId() == f.kept.exerciseId)
                    .singleElement()
                    .satisfies(listed -> {
                        assertThat(listed.getIsStarter()).isFalse();
                        assertThat(listed.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(1L, 5L);
                    });
        });
    }

    @Test
    void keptButUnusedExercise_isRemoved() {
        runWithFixture(f -> assertThat(exists("exercises", f.idleExerciseId)).isFalse());
    }

    @Test
    void unknownGroupNameInKeepList_failsMigration() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            Fixture f = seedFixture();
            jdbc.execute(migrationWithKeepList(
                    "INSERT INTO keep_list VALUES (%d, 'Chest');\n".formatted(f.kept.exerciseId)));
        }))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("unknown muscle group name \"Chest\"");
    }

    @Test
    void unknownExerciseIdInKeepList_failsMigration() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            seedFixture();
            jdbc.execute(migrationWithKeepList("INSERT INTO keep_list VALUES (-1, 'Peito');\n"));
        }))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("unknown exercise id -1");
    }

    @Test
    void shippedKeepList_isEmpty() throws IOException {
        assertThat(migrationSql().lines().map(String::strip))
                .noneMatch(line -> line.toUpperCase().startsWith("INSERT INTO KEEP_LIST"))
                .contains(KEEP_LIST_MARKER.strip());
    }

    @Test
    void runsBeforeStarterSeed_byVersionNumber() {
        // The routine identifies pre-starter rows as "every row in exercises", which only holds
        // while no starter row exists yet: V23 must be applied before any later migration.
        Integer v23Rank = jdbc.queryForObject(
                "SELECT installed_rank FROM flyway_schema_history WHERE version = '23' AND success", Integer.class);
        List<Integer> laterRanks = jdbc.queryForList(
                "SELECT installed_rank FROM flyway_schema_history WHERE version::int > 23", Integer.class);

        assertThat(laterRanks).allMatch(rank -> rank > v23Rank);
    }

    private void runWithFixture(Consumer<Fixture> assertions) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            Fixture f = seedFixture();
            jdbc.execute(migrationWithKeepList("""
                    INSERT INTO keep_list VALUES (%1$d, 'Tríceps');
                    INSERT INTO keep_list VALUES (%1$d, 'Peito');
                    INSERT INTO keep_list VALUES (%1$d, 'Peito');
                    INSERT INTO keep_list VALUES (%2$d, 'Costas');
                    INSERT INTO keep_list VALUES (%3$d, 'Lombar');
                    """.formatted(f.kept.exerciseId, f.solo.exerciseId, f.idleExerciseId)));
            assertions.accept(f);
        });
    }

    private String migrationWithKeepList(String keepListInserts) {
        try {
            String sql = migrationSql();
            assertThat(sql).contains(KEEP_LIST_MARKER);
            return sql.replace(KEEP_LIST_MARKER, KEEP_LIST_MARKER + keepListInserts);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String migrationSql() throws IOException {
        return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
    }

    private Fixture seedFixture() {
        Side t1 = seedSide("T1");
        Side t2 = seedSide("T2");

        Long keptId = insertExercise("X-kept", "kept description");
        Long soloId = insertExercise("X-solo", null);
        Long dropId = insertExercise("X-drop", null);
        Long idleId = insertExercise("X-idle", null);

        Usage kept = use(keptId, t1);
        Usage keptOnT2 = use(keptId, t2);
        Usage solo = use(soloId, t2);
        Usage drop = use(dropId, t1);

        return new Fixture(t1, t2, kept, keptOnT2.entryId, keptOnT2.setId, keptOnT2.setLogId, solo, drop, idleId);
    }

    private Side seedSide(String name) {
        Long trainerId = insertUser(name, "TRAINER");
        Long clientId = insertUser(name + "-client", "CLIENT");
        Long planId = jdbc.queryForObject("""
                INSERT INTO training_plans (name, trainer_id, client_id, start_date, end_date, level)
                VALUES (?, ?, ?, ?, ?, 'BEGINNER') RETURNING id
                """, Long.class, name + " plan", trainerId, clientId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        Long workoutId = jdbc.queryForObject(
                "INSERT INTO workouts (name, training_plan_id, day_of_week) VALUES (?, ?, 'MONDAY') RETURNING id",
                Long.class, name + " workout", planId);
        Long workoutLogId = jdbc.queryForObject("""
                INSERT INTO workout_logs (workout_id, client_id, week_start_date, started_at)
                VALUES (?, ?, ?, now()) RETURNING id
                """, Long.class, workoutId, clientId, LocalDate.of(2026, 3, 2));
        Long feedbackId = jdbc.queryForObject(
                "INSERT INTO workout_feedback (workout_log_id, text, created_at) VALUES (?, 'Good session', now()) RETURNING id",
                Long.class, workoutLogId);
        return new Side(trainerId, planId, workoutId, workoutLogId, feedbackId);
    }

    private Usage use(Long exerciseId, Side side) {
        Long entryId = jdbc.queryForObject(
                "INSERT INTO workout_exercises (workout_id, exercise_id, exercise_order) VALUES (?, ?, 1) RETURNING id",
                Long.class, side.workoutId, exerciseId);
        Long setId = jdbc.queryForObject(
                "INSERT INTO exercise_sets (workout_exercise_id, set_number, reps, strategy) VALUES (?, 1, 10, 'STRAIGHT') RETURNING id",
                Long.class, entryId);
        Long setLogId = jdbc.queryForObject(
                "INSERT INTO set_logs (workout_log_id, exercise_set_id, weight, reps, recorded_at) VALUES (?, ?, 60, 10, now()) RETURNING id",
                Long.class, side.workoutLogId, setId);
        return new Usage(exerciseId, entryId, setId, setLogId);
    }

    private Long insertUser(String name, String role) {
        String unique = String.valueOf(System.nanoTime());
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, cpf, password_hash, role) VALUES (?, ?, ?, 'hash', ?) RETURNING id
                """, Long.class, name, name.toLowerCase() + "-" + unique + "@example.com",
                unique.substring(unique.length() - 11), role);
    }

    private Long insertExercise(String name, String description) {
        return jdbc.queryForObject("INSERT INTO exercises (name, description) VALUES (?, ?) RETURNING id",
                Long.class, name, description);
    }

    private boolean exists(String table, Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)", Boolean.class, id));
    }

    private Long ownerOf(Long exerciseId) {
        return jdbc.queryForObject("SELECT owner_id FROM exercises WHERE id = ?", Long.class, exerciseId);
    }

    private Long exerciseIdOf(Long entryId) {
        return jdbc.queryForObject("SELECT exercise_id FROM workout_exercises WHERE id = ?", Long.class, entryId);
    }

    private int countByName(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM exercises WHERE name = ?", Integer.class, name);
    }

    private record Side(Long trainerId, Long planId, Long workoutId, Long workoutLogId, Long feedbackId) {
    }

    private record Usage(Long exerciseId, Long entryId, Long setId, Long setLogId) {
    }

    private record Fixture(Side t1, Side t2, Usage kept, Long keptT2EntryId, Long keptT2SetId, Long keptT2SetLogId,
                           Usage solo, Usage drop, Long idleExerciseId) {
    }
}
