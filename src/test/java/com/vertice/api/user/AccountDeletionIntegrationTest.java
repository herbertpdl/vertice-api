package com.vertice.api.user;

import com.vertice.api.common.time.BusinessDates;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
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
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenRequest;
import com.vertice.api.generated.grpc.user.v1.DeleteAccountRequest;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersRequest;
import com.vertice.api.generated.grpc.user.v1.ListUsersWithoutPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.SetUserPasswordRequest;
import com.vertice.api.generated.grpc.user.v1.UpdateUserRequest;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.ServiceSubject;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
    /** The current business week: sessions can only be started in it. */
    private static final String WEEK = BusinessDates.currentWeekStart(Clock.systemUTC()).toString();

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
    /** Another client of the same trainer, with the same kind of history: nothing of theirs may change. */
    private User bystander;
    private User admin;
    private TrainerClient link;
    private Exercise usedExercise;
    private Exercise unusedExercise;
    private long plan;
    private long workoutLog;
    private long bystanderPlan;
    private long bystanderWorkoutLog;
    /** Users a test saves beyond the fixture's, removed with it. */
    private final List<Long> extraUsers = new ArrayList<>();

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

        bystander = userRepository.save(newUser("Bystander", Role.CLIENT));
        TrainerClient bystanderLink = new TrainerClient();
        bystanderLink.setTrainer(trainer);
        bystanderLink.setClient(bystander);
        bystanderLink.setStartedAt(Instant.now());
        trainerClientRepository.save(bystanderLink);

        plan = seedPlan(client);
        workoutLog = seedHistory(client, plan, "Knee hurt a bit");
        bystanderPlan = seedPlan(bystander);
        bystanderWorkoutLog = seedHistory(bystander, bystanderPlan, "Felt strong");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        List<Long> ids = new ArrayList<>(List.of(trainer.getId(), client.getId(), bystander.getId(), admin.getId()));
        ids.addAll(extraUsers);
        Map<String, List<Long>> users = Map.of("ids", ids);
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
        assertThat(anonymised.getEmail()).isNull();
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

        // Missing to ADMIN too: nothing can be read from or written back into the anonymised row.
        assertCode(() -> asAdmin.getUser(GetUserRequest.newBuilder().setId(id).build()), Status.Code.NOT_FOUND);
        assertCode(() -> asAdmin.updateUser(UpdateUserRequest.newBuilder().setId(id).setUser(UserRequest.newBuilder()
                .setName("Back").setEmail("back@account-deletion.example.com")
                .setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT)).build()), Status.Code.NOT_FOUND);
        assertCode(() -> asAdmin.setUserPassword(SetUserPasswordRequest.newBuilder().setId(id)
                .setPassword("brandNewPassword1").build()), Status.Code.NOT_FOUND);
        assertThat(asAdmin.listUsers(ListUsersRequest.getDefaultInstance()).getUsersList())
                .extracting(UserResponse::getId).doesNotContain(id);

        assertThat(output.getAll())
                .contains("Account deleted: actorId=%d userId=%d role=CLIENT".formatted(id, id))
                .doesNotContain(client.getEmail())
                .doesNotContain(client.getCpf());
    }

    /** The other client of the same trainer: plan, log, set log, feedback, setup link and link all stay. */
    @Test
    void aClientsDeletion_leavesAnotherClientsDataUntouched() {
        as(client, UserServiceGrpc::newBlockingStub).deleteAccount(DeleteAccountRequest.getDefaultInstance());

        long id = bystander.getId();
        assertThat(count("SELECT count(*) FROM training_plans WHERE id = :id", bystanderPlan)).isOne();
        assertThat(count("SELECT count(*) FROM workouts WHERE training_plan_id = :id", bystanderPlan)).isOne();
        assertThat(count("SELECT count(*) FROM exercise_sets es"
                + " JOIN workout_exercises we ON we.id = es.workout_exercise_id"
                + " JOIN workouts w ON w.id = we.workout_id"
                + " WHERE w.training_plan_id = :id", bystanderPlan)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM workout_logs WHERE client_id = :id", id)).isOne();
        assertThat(count("SELECT count(*) FROM set_logs WHERE workout_log_id = :id", bystanderWorkoutLog)).isOne();
        assertThat(count("SELECT count(*) FROM workout_feedback WHERE workout_log_id = :id", bystanderWorkoutLog)).isOne();
        assertThat(count("SELECT count(*) FROM password_setup_tokens WHERE user_id = :id", id)).isOne();
        assertThat(count("SELECT count(*) FROM trainer_clients WHERE client_id = :id AND ended_at IS NULL", id)).isOne();
        User untouched = userRepository.findById(id).orElseThrow();
        assertThat(untouched.getName()).isEqualTo("Bystander");
        assertThat(untouched.getEmail()).isEqualTo(bystander.getEmail());
        assertThat(untouched.isDeleted()).isFalse();
        assertThat(as(bystander, TrainingPlanServiceGrpc::newBlockingStub)
                .getTrainingPlan(GetTrainingPlanRequest.newBuilder().setId(bystanderPlan).build()).getId()).isEqualTo(bystanderPlan);
    }

    /**
     * Review of #68: a predictable placeholder email could be taken by a live account first and
     * make the deletion fail on the unique index forever. The deleted row's email is NULL, so a
     * live row holding any look-alike address (here saved directly, as a row from before the
     * reserved-domain rule would be) can't block it; and no write path accepts a {@code .invalid}
     * address any more.
     */
    @Test
    void deletionSucceeds_whenALiveRowHoldsALookAlikeAddress_andReservedAddressesAreRefused() {
        String legacyPlaceholder = "deleted+%d@invalid".formatted(client.getId());
        User squatter = newUser("Squatter", Role.CLIENT);
        squatter.setEmail(legacyPlaceholder);
        squatter = userRepository.save(squatter);
        extraUsers.add(squatter.getId());

        as(client, UserServiceGrpc::newBlockingStub).deleteAccount(DeleteAccountRequest.getDefaultInstance());

        assertThat(userRepository.findById(client.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(userRepository.findById(squatter.getId()).orElseThrow().getEmail()).isEqualTo(legacyPlaceholder);

        // Self UpdateUser, a trainer updating their client, registration and create-client all refuse it.
        UserRequest.Builder reserved = UserRequest.newBuilder().setName("Bystander")
                .setEmail("deleted+1@invalid").setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT);
        assertCode(() -> as(bystander, UserServiceGrpc::newBlockingStub).updateUser(UpdateUserRequest.newBuilder()
                .setId(bystander.getId()).setUser(reserved).build()), Status.Code.INVALID_ARGUMENT);
        assertCode(() -> as(trainer, UserServiceGrpc::newBlockingStub).updateUser(UpdateUserRequest.newBuilder()
                .setId(bystander.getId()).setUser(reserved.setEmail("Someone@Mail.INVALID")).build()), Status.Code.INVALID_ARGUMENT);
        assertCode(() -> TestJwts.withBearer(UserServiceGrpc.newBlockingStub(channel), TestJwts.serviceToken(SECRET, ServiceSubject.BFF))
                .createUser(UserCreateRequest.newBuilder().setName("New").setEmail("deleted+2@invalid")
                        .setPassword("supersecret1").setRole(com.vertice.api.generated.grpc.user.v1.Role.CLIENT)
                        .setAcceptedTermsVersion("2026-01").build()), Status.Code.INVALID_ARGUMENT);
        assertCode(() -> as(trainer, TrainerClientServiceGrpc::newBlockingStub).createClientForTrainer(
                CreateClientForTrainerRequest.newBuilder().setTrainerId(trainer.getId()).setName("New")
                        .setEmail("new@clients.invalid").build()), Status.Code.INVALID_ARGUMENT);
        assertThat(userRepository.findById(bystander.getId()).orElseThrow().getEmail()).isEqualTo(bystander.getEmail());
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
        jdbc.update("UPDATE trainer_clients SET ended_at = now() WHERE trainer_id = :id AND ended_at IS NULL",
                Map.of("id", trainer.getId()));

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

        // Nothing new hangs off the deleted trainer, not even through ADMIN: no client, no plan.
        assertCode(() -> as(admin, TrainerClientServiceGrpc::newBlockingStub).createClientForTrainer(
                CreateClientForTrainerRequest.newBuilder().setTrainerId(trainer.getId()).setName("New")
                        .setEmail("new-of-deleted@account-deletion.example.com").build()), Status.Code.NOT_FOUND);
        assertThat(userRepository.findByEmail("new-of-deleted@account-deletion.example.com")).isEmpty();
        assertCode(() -> as(admin, TrainingPlanServiceGrpc::newBlockingStub).createTrainingPlan(
                TrainingPlanCreateRequest.newBuilder().setName("Under a deleted trainer").setTrainerId(trainer.getId())
                        .setClientId(client.getId()).setStartDate("2026-01-01").setEndDate("2026-12-31")
                        .setLevel(com.vertice.api.generated.grpc.plan.v1.PlanLevel.BEGINNER).build()), Status.Code.NOT_FOUND);
    }

    private long count(String sql, long id) {
        return jdbc.queryForObject(sql, Map.of("id", id), Long.class);
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    /**
     * A workout of the trainer's used exercise with two sets in {@code planId}; the client logs the
     * first set, completes the session and leaves {@code feedback}; they get a setup link.
     *
     * @return the workout log's id
     */
    private long seedHistory(User of, long planId, String feedback) {
        long workout = as(trainer, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder().setName("Leg Day").setTrainingPlanId(planId)
                        .setDayOfWeek(DayOfWeek.MONDAY)
                        .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(usedExercise.getId())
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
        passwordSetupTokenService.createToken(of.getId());
        return log;
    }

    private long seedPlan(User forClient) {
        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setName("Plan");
        trainingPlan.setTrainer(trainer);
        trainingPlan.setClient(forClient);
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
