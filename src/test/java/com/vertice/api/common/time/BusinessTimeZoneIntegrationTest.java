package com.vertice.api.common.time;

import com.vertice.api.grpc.ProtoInstants;
import com.vertice.api.plan.session.WorkoutLog;
import com.vertice.api.plan.session.WorkoutLogRepository;
import com.vertice.api.plan.workout.DayOfWeek;
import com.vertice.api.plan.workout.Workout;
import com.vertice.api.plan.workout.WorkoutRepository;
import com.vertice.api.plan.PlanLevel;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The time-zone contract (P2-02, REL-API-008/XREPO-006), asserted so that it cannot depend on the
 * host: every assertion here must hold whatever the JVM's default zone is. The Gradle
 * {@code hostileTimeZoneTest} task runs this same class a second time with the JVM pinned to
 * {@code Pacific/Kiritimati} (UTC+14, i.e. on the far side of the date line from
 * {@code America/Sao_Paulo}), which is what proves the API doesn't lean on {@code TZ}/
 * {@code user.timezone} being sane. Both runs are part of {@code ./gradlew build}.
 *
 * <p>The two halves of the contract: an {@code Instant} column is a point in time (TIMESTAMPTZ,
 * returned over the wire with an explicit UTC designator), and a {@code DATE} column is a local
 * business calendar day in {@code America/Sao_Paulo} that no zone conversion may touch.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19125", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class BusinessTimeZoneIntegrationTest {

    /** The business time zone. Date-only values mean a calendar day here, not a UTC slice. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Every column mapped to a Java {@code Instant}, as {@code table.column}. */
    private static final List<String> INSTANT_COLUMNS = List.of(
            "workout_logs.started_at", "workout_logs.completed_at",
            "set_logs.recorded_at",
            "workout_feedback.created_at",
            "password_setup_tokens.created_at", "password_setup_tokens.expires_at",
            "password_setup_tokens.used_at",
            "trainer_clients.started_at", "trainer_clients.ended_at",
            "users.terms_accepted_at", "users.health_data_consent_at",
            "users.created_at", "users.updated_at", "users.deleted_at");

    /** Date-only business concepts. These must stay DATE: a zone would only corrupt them. */
    private static final List<String> BUSINESS_DATE_COLUMNS = List.of(
            "training_plans.start_date", "training_plans.end_date",
            "workout_logs.week_start_date");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainingPlanRepository trainingPlanRepository;
    @Autowired
    private WorkoutRepository workoutRepository;
    @Autowired
    private WorkoutLogRepository workoutLogRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void everyInstantColumnIsTimestamptz() {
        for (String column : INSTANT_COLUMNS) {
            assertThat(dataTypeOf(column))
                    .as("%s stores an Instant, so it must carry its zone", column)
                    .isEqualTo("timestamp with time zone");
        }
    }

    @Test
    void everyBusinessDateColumnStaysDate() {
        for (String column : BUSINESS_DATE_COLUMNS) {
            assertThat(dataTypeOf(column))
                    .as("%s is a local business day, not an instant", column)
                    .isEqualTo("date");
        }
    }

    /**
     * The core guarantee: an instant written through Hibernate comes back as the same instant, and
     * the database agrees, with no reference to the JVM's zone.
     */
    @Test
    void instantRoundTripsExactly_whateverTheJvmZoneIs() {
        Instant written = Instant.parse("2026-10-16T23:30:00Z");
        inRolledBackTransaction(() -> {
            User user = userRepository.saveAndFlush(client(written));

            assertThat(userRepository.findById(user.getId()).orElseThrow().getDeletedAt()).isEqualTo(written);
            // Read as the driver's own instant type, independent of any zone this JVM happens to be in.
            assertThat(jdbc.queryForObject("SELECT deleted_at FROM users WHERE id = ?",
                    OffsetDateTime.class, user.getId()).toInstant()).isEqualTo(written);
        });
    }

    /**
     * Brazil ran DST until 2019, so rows from before then were recorded at UTC−02:00, not −03:00.
     * A stored instant must survive regardless — nothing may assume a fixed offset.
     */
    @Test
    void instantFromBrazilsDstEraRoundTripsExactly() {
        Instant duringOldBrazilianDst = Instant.parse("2018-01-15T02:30:00Z");
        assertThat(BUSINESS_ZONE.getRules().getOffset(duringOldBrazilianDst))
                .as("precondition: Brazil observed DST in January 2018")
                .isEqualTo(ZoneOffset.ofHours(-2));

        inRolledBackTransaction(() -> {
            User user = userRepository.saveAndFlush(client(duringOldBrazilianDst));
            assertThat(userRepository.findById(user.getId()).orElseThrow().getDeletedAt())
                    .isEqualTo(duringOldBrazilianDst);
        });
    }

    /**
     * The regression this migration exists for. {@code now()} is TIMESTAMPTZ; casting it into a
     * bare TIMESTAMP used the session's zone, which pgjdbc takes from the JVM's — so before V31 a
     * row that let the default fire stored local wall clock into a column every reader read as UTC.
     */
    @Test
    void databaseSideDefaultNow_storesTheTrueInstant() {
        inRolledBackTransaction(() -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO users (name, email, role)
                    VALUES ('tz default', ?, 'CLIENT')
                    RETURNING id
                    """, Long.class, uniqueEmail());

            Instant createdAt = jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?",
                    OffsetDateTime.class, id).toInstant();
            assertThat(createdAt).isCloseTo(Instant.now(), within(5, ChronoUnit.MINUTES));
        });
    }

    /**
     * A date-only business value is a calendar day: it must survive storage unshifted even from a
     * JVM 14 hours away from the business zone, where a UTC-slicing round trip would land a day off.
     */
    @Test
    void localBusinessDateRoundTripsAsTheSameCalendarDay() {
        LocalDate monday = LocalDate.parse("2026-10-12");
        inRolledBackTransaction(() -> {
            WorkoutLog log = storeWorkoutLogFor(monday);

            assertThat(workoutLogRepository.findById(log.getId()).orElseThrow().getWeekStartDate())
                    .isEqualTo(monday);
            assertThat(jdbc.queryForObject("SELECT week_start_date::text FROM workout_logs WHERE id = ?",
                    String.class, log.getId())).isEqualTo("2026-10-12");
        });
    }

    /**
     * What the BFF and the clients mirror: an instant leaves the API with an explicit UTC
     * designator, so nothing downstream has to guess a zone (XREPO-006 — the BFF used to slice
     * {@code completedAt} into a UTC "HH:MM" and present it as a local time).
     */
    @Test
    void instantsCrossTheWireWithAnExplicitUtcDesignator() {
        assertThat(ProtoInstants.instantToString(Instant.parse("2026-10-17T01:00:00Z")))
                .isEqualTo("2026-10-17T01:00:00Z");
    }

    /**
     * The business-day rule, by example, so a later "it's always UTC−3" shortcut is caught: the
     * local day of an instant is its day in {@code America/Sao_Paulo}. The 2018 case is inside
     * Brazil's old DST window, where a hardcoded −03:00 picks the wrong day.
     */
    @Test
    void theBusinessDayOfAnInstantIsItsSaoPauloDay_notAFixedOffsetDay() {
        // 22:00 in Sao Paulo, already the next day in UTC.
        assertThat(LocalDate.ofInstant(Instant.parse("2026-10-17T01:00:00Z"), BUSINESS_ZONE))
                .isEqualTo(LocalDate.parse("2026-10-16"));
        // DST era: 00:30 local at UTC-02:00. A fixed -03:00 would call this the 14th.
        assertThat(LocalDate.ofInstant(Instant.parse("2018-01-15T02:30:00Z"), BUSINESS_ZONE))
                .isEqualTo(LocalDate.parse("2018-01-15"));
    }

    /**
     * Guards the {@code hostileTimeZoneTest} task itself: if it ever stopped pinning the zone, the
     * run above would silently become a duplicate of the ordinary {@code test} run.
     */
    @Test
    @EnabledIfSystemProperty(named = "vertice.test.expectedTimeZone", matches = ".+")
    void theHostileTimeZoneTaskReallyPinsTheJvmZone() {
        String expected = System.getProperty("vertice.test.expectedTimeZone");
        assertThat(TimeZone.getDefault().getID()).isEqualTo(expected);
        assertThat(ZoneId.systemDefault().getRules().getOffset(Instant.now()))
                .as("the pinned zone must actually differ from UTC, or it proves nothing")
                .isNotEqualTo(ZoneOffset.UTC);
        // pgjdbc hands the JVM's zone to the session, which is how the DB-side default got it wrong.
        assertThat(jdbc.queryForObject("SHOW TimeZone", String.class)).isEqualTo(expected);
    }

    private String dataTypeOf(String tableAndColumn) {
        String[] parts = tableAndColumn.split("\\.", 2);
        return jdbc.queryForObject("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, parts[0], parts[1]);
    }

    private User client(Instant deletedAt) {
        User user = new User();
        user.setName("tz contract");
        user.setEmail(uniqueEmail());
        user.setRole(Role.CLIENT);
        user.setDeletedAt(deletedAt);
        return user;
    }

    /** A workout log needs its whole plan tree; built here so the date assertion stands alone. */
    private WorkoutLog storeWorkoutLogFor(LocalDate weekStartDate) {
        User trainer = new User();
        trainer.setName("tz trainer");
        trainer.setEmail(uniqueEmail());
        trainer.setCpf("39053344705");
        trainer.setRole(Role.TRAINER);
        trainer = userRepository.saveAndFlush(trainer);

        User client = new User();
        client.setName("tz client");
        client.setEmail(uniqueEmail());
        client.setRole(Role.CLIENT);
        client = userRepository.saveAndFlush(client);

        TrainingPlan plan = new TrainingPlan();
        plan.setName("tz plan");
        plan.setTrainer(trainer);
        plan.setClient(client);
        plan.setLevel(PlanLevel.BEGINNER);
        plan.setStartDate(weekStartDate);
        plan.setEndDate(weekStartDate.plusDays(30));
        plan = trainingPlanRepository.saveAndFlush(plan);

        Workout workout = new Workout();
        workout.setName("tz workout");
        workout.setDayOfWeek(DayOfWeek.MONDAY);
        workout.setTrainingPlan(plan);
        workout = workoutRepository.saveAndFlush(workout);

        WorkoutLog log = new WorkoutLog();
        log.setWorkout(workout);
        log.setClient(client);
        log.setWeekStartDate(weekStartDate);
        log.setStartedAt(DbInstants.now());
        return workoutLogRepository.saveAndFlush(log);
    }

    private static String uniqueEmail() {
        return "tz-" + System.nanoTime() + "@example.com";
    }

    private void inRolledBackTransaction(Runnable body) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        });
    }
}
