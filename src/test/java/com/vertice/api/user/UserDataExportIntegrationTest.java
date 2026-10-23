package com.vertice.api.user;

import com.vertice.api.common.time.BusinessDates;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.FeedbackRating;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.DeleteAccountRequest;
import com.vertice.api.generated.grpc.user.v1.ExportUserDataRequest;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.PlanLevel;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.plan.session.WorkoutLogRepository;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.vertice.api.plan.PlanCallers.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExportUserData (P1-05 (c)) against a populated database, as production runs it: no {@code local}
 * profile, scoped tokens, real rows. The trainer has two clients; each has a plan (one workout of
 * the trainer's private exercise, two sets), logged the first set, completed the session and left
 * feedback. The client's export must hold all of theirs and nothing of the other client's; the
 * trainer's holds both plans but neither client's sessions. Data is committed (the gRPC call runs
 * on another thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19123", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + UserDataExportIntegrationTest.SECRET})
class UserDataExportIntegrationTest {

    static final String SECRET = "non-local-user-data-export-secret-32b";
    /** A Monday, as {@code week_start_date} requires. */
    /** The current business week: sessions can only be started in it. */
    private static final String WEEK = BusinessDates.currentWeekStart(Clock.systemUTC()).toString();
    /**
     * Not UTC, so any zone the export read relied on would be visibly the wrong one here, on CI
     * (which runs in UTC) as much as locally. Against the bare {@code TIMESTAMP} columns these were
     * before V31 that surfaced as a shifted instant; now they are {@code TIMESTAMPTZ} it surfaces as
     * a failure to read the column at all (see
     * {@link #timestamps_areExportedAsTheUtcInstantTheColumnHolds}).
     */
    private static final TimeZone NON_UTC = TimeZone.getTimeZone("America/Sao_Paulo");
    private static TimeZone originalZone;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private TrainingPlanRepository trainingPlanRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private WorkoutLogRepository workoutLogRepository;
    @Autowired
    private PasswordSetupTokenService passwordSetupTokenService;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private User trainer;
    private User client;
    private User otherClient;
    private Exercise exercise;
    private long plan;
    private long otherPlan;
    private long workoutLog;

    /**
     * Pinned for this class only. Not in a static initializer: JUnit loads every test class during
     * discovery, which would move the whole run off the default zone.
     */
    @BeforeAll
    static void pinNonUtcZone() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(NON_UTC);
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19123").usePlaintext().build();
        trainer = userRepository.save(newUser("Trainer", Role.TRAINER));
        User newClient = newUser("Client", Role.CLIENT);
        newClient.acceptTerms("2026-01", Instant.now());
        newClient.consentToHealthData("2026-02", Instant.now());
        client = userRepository.save(newClient);
        otherClient = userRepository.save(newUser("OtherClient", Role.CLIENT));
        link(client);
        link(otherClient);
        exercise = new Exercise();
        exercise.setName("Bulgarian split squat");
        exercise.setDescription("Rear foot on a bench");
        exercise.setOwner(trainer);
        exercise = exerciseRepository.save(exercise);

        plan = seedPlan(client);
        workoutLog = seedHistory(client, plan, "Knee hurt a bit");
        otherPlan = seedPlan(otherClient);
        seedHistory(otherClient, otherPlan, "Other client's private note");
        // A credential: it must never appear in any export.
        passwordSetupTokenService.createToken(client.getId());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, List<Long>> users = Map.of("ids", List.of(trainer.getId(), client.getId(), otherClient.getId()));
        for (String delete : List.of(
                "DELETE FROM workout_feedback WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE client_id IN (:ids))",
                "DELETE FROM set_logs WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE client_id IN (:ids))",
                "DELETE FROM workout_logs WHERE client_id IN (:ids)",
                "DELETE FROM exercise_sets WHERE workout_exercise_id IN (SELECT we.id FROM workout_exercises we"
                        + " JOIN workouts w ON w.id = we.workout_id JOIN training_plans p ON p.id = w.training_plan_id"
                        + " WHERE p.trainer_id IN (:ids))",
                "DELETE FROM workout_exercises WHERE workout_id IN (SELECT w.id FROM workouts w"
                        + " JOIN training_plans p ON p.id = w.training_plan_id WHERE p.trainer_id IN (:ids))",
                "DELETE FROM workouts WHERE training_plan_id IN (SELECT id FROM training_plans WHERE trainer_id IN (:ids))",
                "DELETE FROM training_plans WHERE trainer_id IN (:ids)",
                "DELETE FROM exercises WHERE owner_id IN (:ids)",
                "DELETE FROM trainer_clients WHERE trainer_id IN (:ids)",
                "DELETE FROM password_setup_tokens WHERE user_id IN (:ids)",
                "DELETE FROM users WHERE id IN (:ids)")) {
            jdbc.update(delete, users);
        }
    }

    @Test
    void aClientsExport_holdsEverythingOfTheirs_andNothingOfAnyoneElse() {
        String json = export(client);
        JsonNode root = jsonMapper.readTree(json);

        assertThat(root.get("formatVersion").asInt()).isEqualTo(1);
        JsonNode profile = root.get("profile");
        assertThat(profile.get("id").asLong()).isEqualTo(client.getId());
        assertThat(profile.get("email").asString()).isEqualTo(client.getEmail());
        assertThat(profile.get("cpf").asString()).isEqualTo(client.getCpf());
        assertThat(profile.get("role").asString()).isEqualTo("CLIENT");
        assertThat(root.at("/consent/acceptedTermsVersion").asString()).isEqualTo("2026-01");
        assertThat(root.at("/consent/healthDataConsentVersion").asString()).isEqualTo("2026-02");

        JsonNode relationships = root.get("relationships");
        assertThat(relationships).hasSize(1);
        assertThat(relationships.get(0).get("as").asString()).isEqualTo("CLIENT");
        assertThat(relationships.get(0).at("/trainer/name").asString()).isEqualTo("Trainer");
        assertThat(relationships.get(0).get("endedAt").isNull()).isTrue();

        JsonNode plans = root.get("plans");
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).get("id").asLong()).isEqualTo(plan);
        assertThat(plans.get(0).get("as").asString()).isEqualTo("CLIENT");
        assertThat(plans.get(0).at("/workouts/0/exercises/0/exerciseName").asString()).isEqualTo("Bulgarian split squat");
        assertThat(plans.get(0).at("/workouts/0/exercises/0/sets")).hasSize(2);
        assertThat(plans.get(0).at("/workouts/0/exercises/0/sets/0/reps").asInt()).isEqualTo(5);

        JsonNode logs = root.get("workoutLogs");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("id").asLong()).isEqualTo(workoutLog);
        assertThat(logs.get(0).get("planId").asLong()).isEqualTo(plan);
        assertThat(logs.get(0).at("/setLogs/0/weight").decimalValue()).isEqualByComparingTo("80");
        assertThat(logs.get(0).at("/feedback/0/text").asString()).isEqualTo("Knee hurt a bit");
        // The same instants the entities (and every other RPC) see, not shifted by a time zone.
        Instant startedAt = workoutLogRepository.findById(workoutLog).orElseThrow().getStartedAt();
        assertThat(Instant.parse(logs.get(0).get("startedAt").asString())).isEqualTo(startedAt);

        assertThat(root.get("exercises")).isEmpty();
        assertThat(json)
                .doesNotContain("Other client's private note")
                .doesNotContain(otherClient.getEmail())
                .doesNotContain("passwordHash")
                .doesNotContain("token");
    }

    /**
     * A known instant written straight into the {@code TIMESTAMPTZ} column (V31) comes back as that
     * same instant, with the JVM in a non-UTC zone (see {@link #NON_UTC}); Hibernate reads it the
     * same way. The literal carries its offset explicitly: a bare {@code TIMESTAMP '…'} would be
     * resolved against the session's zone, which is exactly the ambiguity V31 removed — before V31
     * that bare form was the convention, and this test asserted 10:15:30.123456Z against a reader
     * that would otherwise have shifted it to 13:15:30.123456Z. Post-V31 the old
     * {@code LocalDateTime}-at-UTC reader doesn't shift the value, it fails outright
     * ("Cannot convert the column of type TIMESTAMPTZ to … LocalDateTime"), so what this now pins is
     * that the column holds a true instant and the export reports exactly it.
     */
    @Test
    void timestamps_areExportedAsTheUtcInstantTheColumnHolds() {
        assertThat(TimeZone.getDefault()).isEqualTo(NON_UTC);
        jdbc.update("UPDATE workout_logs SET started_at = TIMESTAMPTZ '2026-03-02 10:15:30.123456+00' WHERE id = :id",
                Map.of("id", workoutLog));

        JsonNode log = jsonMapper.readTree(export(client)).at("/workoutLogs/0");

        assertThat(log.get("startedAt").asString()).isEqualTo("2026-03-02T10:15:30.123456Z");
        assertThat(workoutLogRepository.findById(workoutLog).orElseThrow().getStartedAt())
                .isEqualTo(Instant.parse("2026-03-02T10:15:30.123456Z"));
    }

    /** Both plans they wrote and their own exercise; their clients' sessions belong to those clients. */
    @Test
    void aTrainersExport_holdsTheirPlansAndCatalogue_butNotTheirClientsSessions() {
        JsonNode root = jsonMapper.readTree(export(trainer));

        assertThat(root.at("/profile/cref").asString()).isEqualTo("123456-G/SP");
        assertThat(root.get("relationships")).hasSize(2);
        assertThat(root.get("relationships").get(0).get("as").asString()).isEqualTo("TRAINER");
        assertThat(root.get("plans")).hasSize(2);
        assertThat(root.get("plans").valueStream().map(p -> p.get("as").asString())).containsOnly("TRAINER");
        assertThat(root.get("plans").valueStream().map(p -> p.get("id").asLong())).containsExactly(plan, otherPlan);
        assertThat(root.get("workoutLogs")).isEmpty();
        assertThat(root.get("exercises")).hasSize(1);
        assertThat(root.at("/exercises/0/description").asString()).isEqualTo("Rear foot on a bench");
        assertThat(root.toString())
                .doesNotContain("Knee hurt a bit").doesNotContain("Other client's private note")
                // A client appears in a trainer's export by id and name only, never by email or CPF.
                .doesNotContain(client.getEmail()).doesNotContain(client.getCpf())
                .doesNotContain(otherClient.getEmail()).doesNotContain(otherClient.getCpf());
    }

    /** Self only, and only while the account exists: a deleted account's token is already dead. */
    @Test
    void servicesAreRefused_andADeletedAccountCannotExport() {
        for (ServiceSubject service : ServiceSubject.values()) {
            assertCode(() -> TestJwts.withBearer(UserServiceGrpc.newBlockingStub(channel), TestJwts.serviceToken(SECRET, service))
                    .exportUserData(ExportUserDataRequest.getDefaultInstance()), Status.Code.PERMISSION_DENIED);
        }

        UserServiceGrpc.UserServiceBlockingStub asOther = as(otherClient, UserServiceGrpc::newBlockingStub);
        asOther.deleteAccount(DeleteAccountRequest.getDefaultInstance());
        assertCode(() -> asOther.exportUserData(ExportUserDataRequest.getDefaultInstance()), Status.Code.UNAUTHENTICATED);
    }

    private String export(User user) {
        return as(user, UserServiceGrpc::newBlockingStub).exportUserData(ExportUserDataRequest.getDefaultInstance()).getJson();
    }

    private void link(User of) {
        TrainerClient link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(of);
        link.setStartedAt(Instant.now());
        trainerClientRepository.save(link);
    }

    /** One workout of the trainer's exercise, two sets; the client logs one, completes and leaves feedback. */
    private long seedHistory(User of, long planId, String feedback) {
        long workout = as(trainer, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder().setName("Leg Day").setTrainingPlanId(planId)
                        .setDayOfWeek(DayOfWeek.MONDAY)
                        .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(exercise.getId())
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5))
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5)))
                        .build()).getId();
        long workoutExercise = as(trainer, WorkoutExerciseServiceGrpc::newBlockingStub).listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(workout).build()).getWorkoutExercises(0).getId();
        long set = as(trainer, ExerciseSetServiceGrpc::newBlockingStub).listExerciseSets(
                ListExerciseSetsRequest.newBuilder().setWorkoutExerciseId(workoutExercise).build()).getExerciseSets(0).getId();

        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions = as(of, WorkoutSessionServiceGrpc::newBlockingStub);
        long log = sessions.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder().setWorkoutId(workout)
                .setClientId(of.getId()).setWeekStartDate(WEEK).build()).getId();
        sessions.recordSetLog(RecordSetLogRequest.newBuilder().setWorkoutLogId(log).setExerciseSetId(set)
                .setWeight("80").setReps(5).build());
        sessions.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(log).build());
        as(of, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(
                SubmitWorkoutFeedbackRequest.newBuilder().setRating(FeedbackRating.FEEDBACK_RATING_HARD).setWorkoutLogId(log).setText(feedback).build());
        return log;
    }

    private long seedPlan(User forClient) {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setName("Plan for " + forClient.getName());
        trainingPlan.setTrainer(trainer);
        trainingPlan.setClient(forClient);
        trainingPlan.setStartDate(LocalDate.of(2026, 1, 1));
        trainingPlan.setEndDate(LocalDate.of(2026, 12, 31));
        trainingPlan.setLevel(PlanLevel.INTERMEDIATE);
        return trainingPlanRepository.save(trainingPlan).getId();
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@user-data-export.example.com");
        user.setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)));
        user.setCref(role == Role.TRAINER ? "123456-G/SP" : null);
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
