package com.vertice.api.user;

import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.DeleteAccountRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersWithoutPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.PlanLevel;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.vertice.api.plan.PlanCallers.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * DeleteAccount (P1-05 (b), D5) against a populated database, as production runs it: no
 * {@code local} profile, scoped tokens, real rows. The trainer has a client, a plan for them (one
 * workout of the trainer's private exercise, two sets) and a second private exercise nobody uses;
 * the client logged a set, completed the session, left feedback, consented and has a setup link.
 * Data is committed (the gRPC call runs on another thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19121", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + AccountDeletionIntegrationTest.SECRET})
@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionIntegrationTest {

    static final String SECRET = "non-local-account-deletion-secret-32b";
    /** A Monday, as {@code week_start_date} requires. */
    private static final String WEEK = "2026-03-02";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private TrainingPlanRepository trainingPlanRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private PasswordSetupTokenService passwordSetupTokenService;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private User trainer;
    private User client;
    private User admin;
    private TrainerClient link;
    private Exercise usedExercise;
    private Exercise unusedExercise;
    private long plan;
    private long workoutLog;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19121").usePlaintext().build();
        trainer = userRepository.save(newUser("Trainer", Role.TRAINER));
        User newClient = newUser("Client", Role.CLIENT);
        newClient.acceptTerms("2026-01", Instant.now());
        newClient.consentToHealthData("2026-01", Instant.now());
        client = userRepository.save(newClient);
        admin = userRepository.save(newUser("Admin", Role.ADMIN));
        link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        link = trainerClientRepository.save(link);
        usedExercise = exerciseRepository.save(exerciseOf(trainer, "Used"));
        unusedExercise = exerciseRepository.save(exerciseOf(trainer, "Unused"));

        plan = seedPlan();
        long workout = as(trainer, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder().setName("Leg Day").setTrainingPlanId(plan)
                        .setDayOfWeek(DayOfWeek.MONDAY)
                        .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(usedExercise.getId())
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5))
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5)))
                        .build()).getId();
        long workoutExercise = as(trainer, WorkoutExerciseServiceGrpc::newBlockingStub).listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(workout).build()).getWorkoutExercises(0).getId();
        long set = as(trainer, ExerciseSetServiceGrpc::newBlockingStub).listExerciseSets(
                ListExerciseSetsRequest.newBuilder().setWorkoutExerciseId(workoutExercise).build()).getExerciseSets(0).getId();

        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions = as(client, WorkoutSessionServiceGrpc::newBlockingStub);
        workoutLog = sessions.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder().setWorkoutId(workout)
                .setClientId(client.getId()).setWeekStartDate(WEEK).build()).getId();
        sessions.recordSetLog(RecordSetLogRequest.newBuilder().setWorkoutLogId(workoutLog).setExerciseSetId(set)
                .setWeight("80").setReps(5).build());
        sessions.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(workoutLog).build());
        as(client, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(
                SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(workoutLog).setText("Knee hurt a bit").build());
        passwordSetupTokenService.createToken(client.getId());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, List<Long>> users = Map.of("ids", List.of(trainer.getId(), client.getId(), admin.getId()));
        for (String delete : List.of(
                "DELETE FROM workout_feedback WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE client_id IN (:ids))",
                "DELETE FROM set_logs WHERE workout_log_id IN (SELECT id FROM workout_logs WHERE client_id IN (:ids))",
                "DELETE FROM workout_logs WHERE client_id IN (:ids)",
                """
                DELETE FROM exercise_sets WHERE workout_exercise_id IN (
                    SELECT we.id FROM workout_exercises we
                    JOIN workouts w ON w.id = we.workout_id
                    JOIN training_plans p ON p.id = w.training_plan_id
                    WHERE p.trainer_id IN (:ids))""",
                """
                DELETE FROM workout_exercises WHERE workout_id IN (
                    SELECT w.id FROM workouts w JOIN training_plans p ON p.id = w.training_plan_id
                    WHERE p.trainer_id IN (:ids))""",
                "DELETE FROM workouts WHERE training_plan_id IN (SELECT id FROM training_plans WHERE trainer_id IN (:ids))",
                "DELETE FROM training_plans WHERE trainer_id IN (:ids)",
                "DELETE FROM exercises WHERE owner_id IN (:ids)",
                "DELETE FROM trainer_clients WHERE trainer_id IN (:ids)",
                "DELETE FROM password_setup_tokens WHERE user_id IN (:ids)",
                "DELETE FROM users WHERE id IN (:ids)")) {
            jdbc.update(delete, users);
        }
    }

    /**
     * The acceptance criterion: a client with plans, logs and feedback deletes their account, and
     * their identifiers are gone. Health-related data is deleted, the row is anonymised, the token
     * stops working, and the audit line names ids only.
     */
    @Test
    void aClientDeletesTheirAccount_dataGoesAndTheRowIsAnonymised(CapturedOutput output) {
        UserServiceGrpc.UserServiceBlockingStub asClient = as(client, UserServiceGrpc::newBlockingStub);

        asClient.deleteAccount(DeleteAccountRequest.getDefaultInstance());

        long id = client.getId();
        assertThat(count("SELECT count(*) FROM workout_feedback WHERE workout_log_id = :id", workoutLog)).isZero();
        assertThat(count("SELECT count(*) FROM set_logs WHERE workout_log_id = :id", workoutLog)).isZero();
        assertThat(count("SELECT count(*) FROM workout_logs WHERE client_id = :id", id)).isZero();
        assertThat(count("SELECT count(*) FROM training_plans WHERE client_id = :id", id)).isZero();
        assertThat(count("SELECT count(*) FROM password_setup_tokens WHERE user_id = :id", id)).isZero();
        assertThat(trainerClientRepository.findById(link.getId()).orElseThrow().getEndedAt()).isNotNull();

        User anonymised = userRepository.findById(id).orElseThrow();
        assertThat(anonymised.getName()).isEqualTo(User.ANONYMISED_NAME);
        assertThat(anonymised.getEmail()).isEqualTo("deleted+%d@invalid".formatted(id));
        assertThat(anonymised.getCpf()).isNull();
        assertThat(anonymised.getPasswordHash()).isNull();
        assertThat(anonymised.getAcceptedTermsVersion()).isNull();
        assertThat(anonymised.getHealthDataConsentVersion()).isNull();
        assertThat(anonymised.getRole()).isEqualTo(Role.CLIENT);
        assertThat(anonymised.isDeleted()).isTrue();

        // The same, unexpired token is a stale session now; a repeat call can't run twice.
        assertCode(() -> asClient.getUser(GetUserRequest.newBuilder().setId(id).build()), Status.Code.UNAUTHENTICATED);
        assertCode(() -> asClient.deleteAccount(DeleteAccountRequest.getDefaultInstance()), Status.Code.UNAUTHENTICATED);

        // The trainer's exercise survives: it is theirs, not the client's.
        assertThat(exerciseRepository.findById(usedExercise.getId())).isPresent();

        // Never offered a setup link again, and never listed as waiting for one.
        UserServiceGrpc.UserServiceBlockingStub asAdmin = as(admin, UserServiceGrpc::newBlockingStub);
        assertCode(() -> asAdmin.createPasswordSetupToken(CreatePasswordSetupTokenRequest.newBuilder().setUserId(id).build()),
                Status.Code.NOT_FOUND);
        assertThat(asAdmin.listUsersWithoutPassword(ListUsersWithoutPasswordRequest.getDefaultInstance()).getUsersList())
                .extracting(UserResponse::getId).doesNotContain(id);

        assertThat(output.getAll())
                .contains("Account deleted: actorId=%d userId=%d role=CLIENT".formatted(id, id))
                .doesNotContain(client.getEmail())
                .doesNotContain(client.getCpf());
    }

    @Test
    void aTrainerWithActiveClients_isRefused_andNothingChanges() {
        assertThatThrownBy(() -> as(trainer, UserServiceGrpc::newBlockingStub).deleteAccount(DeleteAccountRequest.getDefaultInstance()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(ex.getStatus().getDescription()).isEqualTo(
                            "End your client relationships first: an account with active clients cannot be deleted");
                });

        User unchanged = userRepository.findById(trainer.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Trainer");
        assertThat(unchanged.isDeleted()).isFalse();
        assertThat(exerciseRepository.findById(unusedExercise.getId())).isPresent();
    }

    /**
     * Once the relationships are ended, a trainer can leave. What they wrote for clients stays with
     * those clients (plan, used exercise, the client's own logs), now under an anonymised trainer;
     * their unused private exercise goes.
     */
    @Test
    void aTrainerWithoutActiveClients_isAnonymised_andTheirClientsKeepTheirPlans() {
        link.setEndedAt(Instant.now());
        trainerClientRepository.save(link);

        as(trainer, UserServiceGrpc::newBlockingStub).deleteAccount(DeleteAccountRequest.getDefaultInstance());

        User anonymised = userRepository.findById(trainer.getId()).orElseThrow();
        assertThat(anonymised.getName()).isEqualTo(User.ANONYMISED_NAME);
        assertThat(anonymised.getCpf()).isNull();
        assertThat(anonymised.getCref()).isNull();
        assertThat(anonymised.isDeleted()).isTrue();
        assertThat(exerciseRepository.findById(unusedExercise.getId())).isEmpty();
        assertThat(exerciseRepository.findById(usedExercise.getId())).isPresent();

        assertThat(as(client, TrainingPlanServiceGrpc::newBlockingStub)
                .getTrainingPlan(GetTrainingPlanRequest.newBuilder().setId(plan).build()).getTrainerId())
                .isEqualTo(trainer.getId());
        assertThat(count("SELECT count(*) FROM set_logs WHERE workout_log_id = :id", workoutLog)).isOne();
        assertThat(count("SELECT count(*) FROM workout_feedback WHERE workout_log_id = :id", workoutLog)).isOne();
        assertCode(() -> as(trainer, UserServiceGrpc::newBlockingStub)
                .getUser(GetUserRequest.newBuilder().setId(trainer.getId()).build()), Status.Code.UNAUTHENTICATED);
    }

    private long count(String sql, long id) {
        return jdbc.queryForObject(sql, Map.of("id", id), Long.class);
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private long seedPlan() {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setName("Plan");
        trainingPlan.setTrainer(trainer);
        trainingPlan.setClient(client);
        trainingPlan.setStartDate(LocalDate.of(2026, 1, 1));
        trainingPlan.setEndDate(LocalDate.of(2026, 12, 31));
        trainingPlan.setLevel(PlanLevel.INTERMEDIATE);
        return trainingPlanRepository.save(trainingPlan).getId();
    }

    private static Exercise exerciseOf(User owner, String name) {
        Exercise exercise = new Exercise();
        exercise.setName(name + " of " + owner.getName());
        exercise.setOwner(owner);
        return exercise;
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@account-deletion.example.com");
        user.setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)));
        user.setCref(role == Role.TRAINER ? "123456-G/SP" : null);
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
