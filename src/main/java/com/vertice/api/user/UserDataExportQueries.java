package com.vertice.api.user;

import com.vertice.api.common.exception.ExportTooLargeException;
import com.vertice.api.user.UserDataExport.Consent;
import com.vertice.api.user.UserDataExport.Exercise;
import com.vertice.api.user.UserDataExport.ExerciseSet;
import com.vertice.api.user.UserDataExport.Feedback;
import com.vertice.api.user.UserDataExport.Party;
import com.vertice.api.user.UserDataExport.Plan;
import com.vertice.api.user.UserDataExport.Profile;
import com.vertice.api.user.UserDataExport.Relationship;
import com.vertice.api.user.UserDataExport.SetLog;
import com.vertice.api.user.UserDataExport.Workout;
import com.vertice.api.user.UserDataExport.WorkoutExercise;
import com.vertice.api.user.UserDataExport.WorkoutLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The reads behind {@code ExportUserData}: one set-based query per section, whatever the volume.
 * Children are loaded first and grouped by parent id, so each parent row is built complete in one
 * pass (sets → workout exercises → workouts → plans; set logs and feedback → workout logs).
 *
 * <p>Plain SQL rather than JPA: the export crosses every aggregate, needs no entity behaviour, and
 * a lazy association touched while serializing would turn into one query per row. Every list is
 * read with {@code LIMIT maxRows + 1}, so an oversized section is detected without loading it.
 */
@Repository
@RequiredArgsConstructor
class UserDataExportQueries {

    /** The caller's plans, as their trainer or their client; reused by every plan-tree query. */
    private static final String PLANS_OF_USER =
            "SELECT id FROM training_plans WHERE trainer_id = :userId OR client_id = :userId";
    private static final String LOGS_OF_USER = "SELECT id FROM workout_logs WHERE client_id = :userId";

    private final NamedParameterJdbcTemplate jdbc;
    private final UserDataExportProperties properties;

    Optional<Profile> profile(long userId) {
        return jdbc.query("""
                        SELECT id, name, email, cpf, cref, role, created_at, updated_at
                        FROM users
                        WHERE id = :userId AND deleted_at IS NULL
                        """, Map.of("userId", userId),
                (rs, n) -> new Profile(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("cpf"), rs.getString("cref"), rs.getString("role"),
                        instant(rs, "created_at"), instant(rs, "updated_at")))
                .stream().findFirst();
    }

    Consent consent(long userId) {
        return jdbc.queryForObject("""
                        SELECT accepted_terms_version, terms_accepted_at, health_data_consent_version, health_data_consent_at
                        FROM users
                        WHERE id = :userId
                        """, Map.of("userId", userId),
                (rs, n) -> new Consent(rs.getString("accepted_terms_version"), instant(rs, "terms_accepted_at"),
                        rs.getString("health_data_consent_version"), instant(rs, "health_data_consent_at")));
    }

    List<Relationship> relationships(long userId) {
        return list("relationships", """
                SELECT tc.id, tc.trainer_id, t.name AS trainer_name, tc.client_id, c.name AS client_name,
                       tc.started_at, tc.ended_at
                FROM trainer_clients tc
                JOIN users t ON t.id = tc.trainer_id
                JOIN users c ON c.id = tc.client_id
                WHERE tc.trainer_id = :userId OR tc.client_id = :userId
                ORDER BY tc.started_at, tc.id
                """, userId, (rs, n) -> new Relationship(rs.getLong("id"),
                rs.getLong("trainer_id") == userId ? "TRAINER" : "CLIENT",
                party(rs, "trainer_id", "trainer_name"), party(rs, "client_id", "client_name"),
                instant(rs, "started_at"), instant(rs, "ended_at")));
    }

    List<Plan> plans(long userId) {
        Map<Long, List<ExerciseSet>> setsByWorkoutExercise = grouped(list("exercise sets", """
                SELECT es.id, es.workout_exercise_id, es.set_number, es.reps, es.duration_seconds, es.weight,
                       es.load_percentage, es.strategy, es.rest_seconds, es.notes
                FROM exercise_sets es
                JOIN workout_exercises we ON we.id = es.workout_exercise_id
                JOIN workouts w ON w.id = we.workout_id
                WHERE w.training_plan_id IN (%s)
                ORDER BY es.workout_exercise_id, es.set_number, es.id
                """.formatted(PLANS_OF_USER), userId, (rs, n) -> new Child<>(rs.getLong("workout_exercise_id"),
                new ExerciseSet(rs.getLong("id"), integer(rs, "set_number"), integer(rs, "reps"),
                        integer(rs, "duration_seconds"), rs.getBigDecimal("weight"), rs.getBigDecimal("load_percentage"),
                        rs.getString("strategy"), integer(rs, "rest_seconds"), rs.getString("notes")))));

        Map<Long, List<WorkoutExercise>> exercisesByWorkout = grouped(list("workout exercises", """
                SELECT we.id, we.workout_id, we.exercise_id, e.name AS exercise_name, we.exercise_order,
                       we.rest_seconds_between_sets, we.notes
                FROM workout_exercises we
                JOIN exercises e ON e.id = we.exercise_id
                JOIN workouts w ON w.id = we.workout_id
                WHERE w.training_plan_id IN (%s)
                ORDER BY we.workout_id, we.exercise_order, we.id
                """.formatted(PLANS_OF_USER), userId, (rs, n) -> new Child<>(rs.getLong("workout_id"),
                new WorkoutExercise(rs.getLong("id"), rs.getLong("exercise_id"), rs.getString("exercise_name"),
                        integer(rs, "exercise_order"), integer(rs, "rest_seconds_between_sets"), rs.getString("notes"),
                        setsByWorkoutExercise.getOrDefault(rs.getLong("id"), List.of())))));

        Map<Long, List<Workout>> workoutsByPlan = grouped(list("workouts", """
                SELECT w.id, w.training_plan_id, w.name, w.day_of_week
                FROM workouts w
                WHERE w.training_plan_id IN (%s)
                ORDER BY w.training_plan_id, w.id
                """.formatted(PLANS_OF_USER), userId, (rs, n) -> new Child<>(rs.getLong("training_plan_id"),
                new Workout(rs.getLong("id"), rs.getString("name"), rs.getString("day_of_week"),
                        exercisesByWorkout.getOrDefault(rs.getLong("id"), List.of())))));

        return list("plans", """
                SELECT p.id, p.trainer_id, t.name AS trainer_name, p.client_id, c.name AS client_name,
                       p.name, p.description, p.start_date, p.end_date, p.level
                FROM training_plans p
                JOIN users t ON t.id = p.trainer_id
                JOIN users c ON c.id = p.client_id
                WHERE p.trainer_id = :userId OR p.client_id = :userId
                ORDER BY p.id
                """, userId, (rs, n) -> new Plan(rs.getLong("id"),
                rs.getLong("trainer_id") == userId ? "TRAINER" : "CLIENT",
                party(rs, "trainer_id", "trainer_name"), party(rs, "client_id", "client_name"),
                rs.getString("name"), rs.getString("description"), date(rs, "start_date"), date(rs, "end_date"),
                rs.getString("level"), workoutsByPlan.getOrDefault(rs.getLong("id"), List.of())));
    }

    /** The caller's own sessions as a client. A trainer's clients' sessions are those clients' data. */
    List<WorkoutLog> workoutLogs(long userId) {
        Map<Long, List<SetLog>> setLogsByLog = grouped(list("set logs", """
                SELECT sl.id, sl.workout_log_id, sl.exercise_set_id, sl.weight, sl.reps, sl.recorded_at
                FROM set_logs sl
                WHERE sl.workout_log_id IN (%s)
                ORDER BY sl.workout_log_id, sl.id
                """.formatted(LOGS_OF_USER), userId, (rs, n) -> new Child<>(rs.getLong("workout_log_id"),
                new SetLog(rs.getLong("id"), rs.getLong("exercise_set_id"), rs.getBigDecimal("weight"),
                        integer(rs, "reps"), instant(rs, "recorded_at")))));

        Map<Long, List<Feedback>> feedbackByLog = grouped(list("feedback", """
                SELECT f.id, f.workout_log_id, f.text, f.created_at
                FROM workout_feedback f
                WHERE f.workout_log_id IN (%s)
                ORDER BY f.workout_log_id, f.id
                """.formatted(LOGS_OF_USER), userId, (rs, n) -> new Child<>(rs.getLong("workout_log_id"),
                new Feedback(rs.getLong("id"), rs.getString("text"), instant(rs, "created_at")))));

        return list("workout logs", """
                SELECT l.id, l.workout_id, w.training_plan_id, l.week_start_date, l.started_at, l.completed_at
                FROM workout_logs l
                JOIN workouts w ON w.id = l.workout_id
                WHERE l.client_id = :userId
                ORDER BY l.week_start_date, l.id
                """, userId, (rs, n) -> new WorkoutLog(rs.getLong("id"), rs.getLong("workout_id"),
                rs.getLong("training_plan_id"), date(rs, "week_start_date"), instant(rs, "started_at"),
                instant(rs, "completed_at"), setLogsByLog.getOrDefault(rs.getLong("id"), List.of()),
                feedbackByLog.getOrDefault(rs.getLong("id"), List.of())));
    }

    List<Exercise> exercises(long userId) {
        return list("exercises", """
                SELECT id, name, description, video_url
                FROM exercises
                WHERE owner_id = :userId
                ORDER BY id
                """, userId, (rs, n) -> new Exercise(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("video_url")));
    }

    /** One row of a child section, keyed by its parent's id. */
    private record Child<T>(long parentId, T value) {
    }

    private <T> List<T> list(String section, String sql, long userId, RowMapper<T> mapper) {
        int maxRows = properties.maxRows();
        List<T> rows = jdbc.query(sql + " LIMIT :limit", Map.of("userId", userId, "limit", maxRows + 1), mapper);
        if (rows.size() > maxRows) {
            throw new ExportTooLargeException("%s exceed %d rows".formatted(section, maxRows));
        }
        return rows;
    }

    /** Keeps each group in query order. */
    private static <T> Map<Long, List<T>> grouped(List<Child<T>> rows) {
        return rows.stream().collect(Collectors.groupingBy(Child::parentId,
                Collectors.mapping(Child::value, Collectors.toList())));
    }

    private static Party party(ResultSet rs, String idColumn, String nameColumn) throws SQLException {
        return new Party(rs.getLong(idColumn), rs.getString(nameColumn));
    }

    /**
     * These columns are {@code TIMESTAMPTZ} (V31), so the driver knows the instant exactly and no
     * zone has to be assumed here. {@link OffsetDateTime} rather than {@code getTimestamp} is a
     * house-style choice, not a correctness one: for a {@code TIMESTAMPTZ} column
     * {@code getTimestamp} yields the right instant too (measured in both zones). It is the legacy
     * {@code java.sql.Timestamp} semantics that are worth avoiding — against the bare
     * {@code TIMESTAMP} column this used to be, {@code getTimestamp} genuinely did resolve the
     * stored wall clock through the JVM's zone, and the pre-V31 code had to compensate by reading
     * {@code LocalDateTime} at {@code ZoneOffset.UTC}.
     */
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Integer.class);
    }
}
