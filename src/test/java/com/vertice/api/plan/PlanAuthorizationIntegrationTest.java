package com.vertice.api.plan;

import com.vertice.api.common.time.BusinessDates;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.DeleteExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.GetExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListTrainingPlansRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutsRequest;
import com.vertice.api.generated.grpc.plan.v1.ReplaceWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.SetStrategy;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.UpdateExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.UpdateWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.DeleteSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.RestartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.DiscardWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.FeedbackRating;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.GetUserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.vertice.api.plan.PlanCallers.assertDenied;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plan-tree rules end to end, as production runs them: no {@code local} profile (a scoped
 * token on every call), the real decoder, real ownership queries against Postgres. Trainer A has
 * client A and a plan for them, with a workout built through the RPCs; trainer B and client B are
 * the outsiders. Data is committed (the gRPC call runs on another thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19112", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + PlanAuthorizationIntegrationTest.SECRET})
class PlanAuthorizationIntegrationTest {

    static final String SECRET = "non-local-plan-authz-test-secret-32b!";
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
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;

    private User trainerA;
    private User clientA;
    private User trainerB;
    private User clientB;
    private TrainerClient linkA;
    private Exercise ownExerciseOfA;
    private Exercise privateExerciseOfB;
    private long planA;
    private long workoutA;
    private long workoutExerciseA;
    private long setA;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19112").usePlaintext().build();

        trainerA = userRepository.save(newUser("TrainerA", Role.TRAINER));
        clientA = userRepository.save(newUser("ClientA", Role.CLIENT));
        trainerB = userRepository.save(newUser("TrainerB", Role.TRAINER));
        clientB = userRepository.save(newUser("ClientB", Role.CLIENT));
        linkA = link(trainerA, clientA);
        link(trainerB, clientB);
        ownExerciseOfA = exerciseOwnedBy(trainerA);
        privateExerciseOfB = exerciseOwnedBy(trainerB);

        planA = seedPlan(trainerA, clientA);
        WorkoutResponse workout = as(trainerA, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder()
                        .setName("Leg Day").setTrainingPlanId(planA).setDayOfWeek(DayOfWeek.MONDAY)
                        .addExercises(entry(ownExerciseOfA.getId()))
                        .build());
        workoutA = workout.getId();
        workoutExerciseA = as(clientA, WorkoutExerciseServiceGrpc::newBlockingStub).listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(workoutA).build()).getWorkoutExercises(0).getId();
        setA = as(clientA, ExerciseSetServiceGrpc::newBlockingStub).listExerciseSets(
                ListExerciseSetsRequest.newBuilder().setWorkoutExerciseId(workoutExerciseA).build()).getExerciseSets(0).getId();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, List<Long>> users = Map.of("ids",
                List.of(trainerA.getId(), clientA.getId(), trainerB.getId(), clientB.getId()));
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
                "DELETE FROM users WHERE id IN (:ids)")) {
            jdbc.update(delete, users);
        }
    }

    @Test
    void anotherTrainer_isPermissionDenied_onThePlanAndEverythingUnderIt() {
        TrainingPlanServiceGrpc.TrainingPlanServiceBlockingStub plansAsB = as(trainerB, TrainingPlanServiceGrpc::newBlockingStub);

        assertDenied(() -> plansAsB.getTrainingPlan(GetTrainingPlanRequest.newBuilder().setId(planA).build()));
        assertDenied(() -> plansAsB.listTrainingPlans(ListTrainingPlansRequest.newBuilder().setTrainerId(trainerA.getId()).build()));
        assertDenied(() -> as(trainerB, WorkoutServiceGrpc::newBlockingStub).updateWorkout(renameWorkout("Hijacked")));
        assertDenied(() -> as(trainerB, WorkoutSessionServiceGrpc::newBlockingStub).getOrStartWorkoutLog(startLog(clientA)));
        assertDenied(() -> as(trainerB, WorkoutFeedbackServiceGrpc::newBlockingStub).listWorkoutFeedback(
                ListWorkoutFeedbackRequest.newBuilder().setTrainerId(trainerA.getId()).build()));

        // Trainer B's own list doesn't contain trainer A's plan; trainer A's does.
        assertThat(plansAsB.listTrainingPlans(ListTrainingPlansRequest.getDefaultInstance()).getTrainingPlansList())
                .extracting(TrainingPlanResponse::getId).doesNotContain(planA);
        assertThat(as(trainerA, TrainingPlanServiceGrpc::newBlockingStub)
                .listTrainingPlans(ListTrainingPlansRequest.getDefaultInstance()).getTrainingPlansList())
                .extracting(TrainingPlanResponse::getId).containsExactly(planA);
    }

    /**
     * The writes keyed by a set or workout-exercise id resolve the plan through
     * {@code PlanAccessRepository.findByExerciseSetId}/{@code findByWorkoutExerciseId}; CloneWorkout
     * checks both ends. Trainer B's requests are valid (B's own clone succeeds), so each refusal is
     * the ownership rule, and trainer A's tree is left as it was.
     */
    @Test
    void anotherTrainer_isPermissionDenied_onSetAndWorkoutExerciseWrites_andCannotCloneIntoOrOutOfThePlan() {
        long planB = seedPlan(trainerB, clientB);
        WorkoutServiceGrpc.WorkoutServiceBlockingStub workoutsAsB = as(trainerB, WorkoutServiceGrpc::newBlockingStub);
        long workoutB = workoutsAsB.createWorkoutWithExercises(CreateWorkoutWithExercisesRequest.newBuilder()
                .setName("B Day").setTrainingPlanId(planB).setDayOfWeek(DayOfWeek.MONDAY)
                .addExercises(entry(privateExerciseOfB.getId())).build()).getId();
        ExerciseSetServiceGrpc.ExerciseSetServiceBlockingStub setsAsB = as(trainerB, ExerciseSetServiceGrpc::newBlockingStub);

        assertDenied(() -> setsAsB.updateExerciseSet(UpdateExerciseSetRequest.newBuilder().setId(setA)
                .setExerciseSet(ExerciseSetRequest.newBuilder().setSetNumber(1).setReps(20).setStrategy(SetStrategy.STRAIGHT)).build()));
        assertDenied(() -> setsAsB.deleteExerciseSet(DeleteExerciseSetRequest.newBuilder().setId(setA).build()));
        assertDenied(() -> as(trainerB, WorkoutExerciseServiceGrpc::newBlockingStub).updateWorkoutExercise(
                UpdateWorkoutExerciseRequest.newBuilder().setId(workoutExerciseA)
                        .setWorkoutExercise(WorkoutExerciseRequest.newBuilder().setOrder(9)).build()));
        assertDenied(() -> workoutsAsB.cloneWorkout(clone(workoutB, planA)));
        assertDenied(() -> workoutsAsB.cloneWorkout(clone(workoutA, planB)));

        assertThat(workoutsAsB.cloneWorkout(clone(workoutB, planB)).getTrainingPlanId()).isEqualTo(planB);
        assertThat(as(trainerA, ExerciseSetServiceGrpc::newBlockingStub)
                .getExerciseSet(GetExerciseSetRequest.newBuilder().setId(setA).build()).getReps()).isEqualTo(5);
        assertThat(as(trainerA, WorkoutExerciseServiceGrpc::newBlockingStub)
                .getWorkoutExercise(GetWorkoutExerciseRequest.newBuilder().setId(workoutExerciseA).build()).getOrder())
                .isEqualTo(1);
        assertThat(as(trainerA, WorkoutServiceGrpc::newBlockingStub)
                .listWorkouts(ListWorkoutsRequest.newBuilder().setTrainingPlanId(planA).build()).getWorkoutsList())
                .extracting(WorkoutResponse::getId).containsExactly(workoutA);
    }

    @Test
    void anotherClient_isPermissionDenied_onTheLogOfAnotherClient_whoRunsTheirSessionEndToEnd() {
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessionAsA = as(clientA, WorkoutSessionServiceGrpc::newBlockingStub);
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessionAsB = as(clientB, WorkoutSessionServiceGrpc::newBlockingStub);
        WorkoutFeedbackServiceGrpc.WorkoutFeedbackServiceBlockingStub feedbackAsB = as(clientB, WorkoutFeedbackServiceGrpc::newBlockingStub);

        WorkoutLogResponse log = sessionAsA.getOrStartWorkoutLog(startLog(clientA));
        sessionAsA.recordSetLog(recordSet(log.getId()));

        assertDenied(() -> sessionAsB.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(log.getId()).build()));
        assertDenied(() -> sessionAsB.recordSetLog(recordSet(log.getId())));
        assertDenied(() -> sessionAsB.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(log.getId()).build()));
        assertDenied(() -> sessionAsB.getOrStartWorkoutLog(startLog(clientB)));
        assertDenied(() -> sessionAsB.getOrStartWorkoutLog(startLog(clientA)));
        // The lifecycle RPCs: another client neither restarts, discards, unticks nor reads A's sets.
        assertDenied(() -> sessionAsB.restartWorkoutLog(restartLog(clientB)));
        assertDenied(() -> sessionAsB.restartWorkoutLog(restartLog(clientA)));
        assertDenied(() -> sessionAsB.discardWorkoutLog(DiscardWorkoutLogRequest.newBuilder().setId(log.getId()).build()));
        assertDenied(() -> sessionAsB.deleteSetLog(untick(log.getId())));
        assertDenied(() -> sessionAsB.listSetLogs(ListSetLogsRequest.newBuilder().setWorkoutLogId(log.getId()).build()));
        // The plan's trainer reads the sets but never restarts, discards or unticks for the client.
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessionAsTrainer = as(trainerA, WorkoutSessionServiceGrpc::newBlockingStub);
        assertDenied(() -> sessionAsTrainer.restartWorkoutLog(restartLog(clientA)));
        assertDenied(() -> sessionAsTrainer.discardWorkoutLog(DiscardWorkoutLogRequest.newBuilder().setId(log.getId()).build()));
        assertDenied(() -> sessionAsTrainer.deleteSetLog(untick(log.getId())));
        assertThat(sessionAsTrainer.listSetLogs(ListSetLogsRequest.newBuilder().setWorkoutLogId(log.getId()).build())
                .getSetLogsList()).hasSize(1);
        // Nothing of A's was removed by the refused calls.
        assertThat(sessionAsA.listSetLogs(ListSetLogsRequest.newBuilder().setWorkoutLogId(log.getId()).build())
                .getSetLogsList()).hasSize(1);

        WorkoutLogResponse completed = sessionAsA.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(log.getId()).build());
        assertThat(completed.getCompletedAt()).isNotBlank();
        assertDenied(() -> feedbackAsB.submitWorkoutFeedback(feedback(log.getId())));
        // Trainer A reads the log, but writing into it is the client's.
        assertThat(as(trainerA, WorkoutSessionServiceGrpc::newBlockingStub)
                .getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(log.getId()).build()).getId()).isEqualTo(log.getId());
        assertDenied(() -> as(trainerA, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(feedback(log.getId())));

        as(clientA, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(feedback(log.getId()));
        assertThat(as(trainerA, WorkoutFeedbackServiceGrpc::newBlockingStub).listWorkoutFeedback(
                ListWorkoutFeedbackRequest.newBuilder().setTrainerId(trainerA.getId()).build()).getWorkoutFeedbackList())
                .hasSize(1);
    }

    @Test
    void thePlansClient_readsButNeverWritesTheWorkout() {
        assertThat(as(clientA, WorkoutServiceGrpc::newBlockingStub)
                .getWorkout(GetWorkoutRequest.newBuilder().setId(workoutA).build())
                .getId()).isEqualTo(workoutA);

        assertDenied(() -> as(clientA, WorkoutServiceGrpc::newBlockingStub).updateWorkout(renameWorkout("Mine now")));
        assertDenied(() -> as(clientA, WorkoutExerciseServiceGrpc::newBlockingStub).createWorkoutExercise(
                WorkoutExerciseCreateRequest.newBuilder().setWorkoutId(workoutA).setExerciseId(ownExerciseOfA.getId()).setOrder(2).build()));
    }

    /** Spec exercise-starter-catalog PR9 (R17): another trainer's private exercise never goes into a workout. */
    @Test
    void anotherTrainersPrivateExercise_cannotGoIntoAWorkout_throughAnyWritePath() {
        WorkoutServiceGrpc.WorkoutServiceBlockingStub workoutsAsA = as(trainerA, WorkoutServiceGrpc::newBlockingStub);

        assertDenied(() -> as(trainerA, WorkoutExerciseServiceGrpc::newBlockingStub).createWorkoutExercise(
                WorkoutExerciseCreateRequest.newBuilder().setWorkoutId(workoutA).setExerciseId(privateExerciseOfB.getId()).setOrder(2).build()));
        assertDenied(() -> workoutsAsA.createWorkoutWithExercises(CreateWorkoutWithExercisesRequest.newBuilder()
                .setName("Day 2").setTrainingPlanId(planA).setDayOfWeek(DayOfWeek.TUESDAY)
                .addExercises(entry(privateExerciseOfB.getId())).build()));
        assertDenied(() -> workoutsAsA.replaceWorkoutExercises(ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(workoutA).addExercises(entry(privateExerciseOfB.getId())).build()));

        // The workout kept its own exercise; a starter one is fine.
        assertThat(as(trainerA, WorkoutExerciseServiceGrpc::newBlockingStub).listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(workoutA).build()).getWorkoutExercisesList())
                .extracting(we -> we.getExerciseId()).containsExactly(ownExerciseOfA.getId());
        workoutsAsA.replaceWorkoutExercises(ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(workoutA).addExercises(entry(starterExerciseId())).build());
    }

    /** No existence oracle: to a non-admin, a missing id looks exactly like someone else's. */
    @Test
    void missingIds_arePermissionDenied_notNotFound() {
        assertDenied(() -> as(trainerA, TrainingPlanServiceGrpc::newBlockingStub)
                .getTrainingPlan(GetTrainingPlanRequest.newBuilder().setId(Long.MAX_VALUE).build()));
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessionAsA = as(clientA, WorkoutSessionServiceGrpc::newBlockingStub);
        assertDenied(() -> sessionAsA.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(Long.MAX_VALUE).build()));
        assertDenied(() -> sessionAsA.discardWorkoutLog(DiscardWorkoutLogRequest.newBuilder().setId(Long.MAX_VALUE).build()));
        assertDenied(() -> sessionAsA.deleteSetLog(untick(Long.MAX_VALUE)));
        assertDenied(() -> sessionAsA.listSetLogs(ListSetLogsRequest.newBuilder().setWorkoutLogId(Long.MAX_VALUE).build()));
        assertDenied(() -> sessionAsA.restartWorkoutLog(RestartWorkoutLogRequest.newBuilder().setWorkoutId(Long.MAX_VALUE)
                .setClientId(clientA.getId()).setWeekStartDate(WEEK).build()));
    }

    /**
     * The dashboard names the client of every plan the trainer wrote. Once the client left the
     * roster, GetUser still answers, with the name and role only.
     */
    @Test
    void getUser_formerClientWithAPlan_returnsNameAndRoleOnly() {
        UserServiceGrpc.UserServiceBlockingStub usersAsA = as(trainerA, UserServiceGrpc::newBlockingStub);
        GetUserRequest request = GetUserRequest.newBuilder().setId(clientA.getId()).build();
        assertThat(usersAsA.getUser(request).getEmail()).isEqualTo(clientA.getEmail());

        linkA.setEndedAt(Instant.now());
        trainerClientRepository.save(linkA);

        UserResponse summary = usersAsA.getUser(request);
        assertThat(summary.getName()).isEqualTo("ClientA");
        assertThat(summary.getRole()).isEqualTo(com.vertice.api.generated.grpc.user.v1.Role.CLIENT);
        assertThat(summary.getEmail()).isEmpty();
        assertThat(summary.getCpf()).isEmpty();
        // A trainer who never had a plan with them still gets nothing.
        assertDenied(() -> as(trainerB, UserServiceGrpc::newBlockingStub).getUser(request));
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private UpdateWorkoutRequest renameWorkout(String name) {
        return UpdateWorkoutRequest.newBuilder().setId(workoutA)
                .setWorkout(WorkoutRequest.newBuilder().setName(name).setDayOfWeek(DayOfWeek.MONDAY)).build();
    }

    private static CloneWorkoutRequest clone(long sourceWorkoutId, long targetPlanId) {
        return CloneWorkoutRequest.newBuilder().setSourceWorkoutId(sourceWorkoutId)
                .setTargetTrainingPlanId(targetPlanId).setName("Copy").setDayOfWeek(DayOfWeek.FRIDAY).build();
    }

    private GetOrStartWorkoutLogRequest startLog(User client) {
        return GetOrStartWorkoutLogRequest.newBuilder().setWorkoutId(workoutA).setClientId(client.getId()).setWeekStartDate(WEEK).build();
    }

    private RestartWorkoutLogRequest restartLog(User client) {
        return RestartWorkoutLogRequest.newBuilder().setWorkoutId(workoutA).setClientId(client.getId()).setWeekStartDate(WEEK).build();
    }

    private DeleteSetLogRequest untick(long workoutLogId) {
        return DeleteSetLogRequest.newBuilder().setWorkoutLogId(workoutLogId).setExerciseSetId(setA).build();
    }

    private RecordSetLogRequest recordSet(long workoutLogId) {
        return RecordSetLogRequest.newBuilder().setWorkoutLogId(workoutLogId).setExerciseSetId(setA).setWeight("80").setReps(5).build();
    }

    private static SubmitWorkoutFeedbackRequest feedback(long workoutLogId) {
        return SubmitWorkoutFeedbackRequest.newBuilder().setRating(FeedbackRating.FEEDBACK_RATING_HARD).setWorkoutLogId(workoutLogId).setText("Heavy today").build();
    }

    private static WorkoutExerciseEntry entry(long exerciseId) {
        return WorkoutExerciseEntry.newBuilder().setExerciseId(exerciseId)
                .addSets(ExerciseSetEntry.newBuilder().setReps(5).build()).build();
    }

    private long starterExerciseId() {
        return jdbc.queryForObject("SELECT MIN(id) FROM exercises WHERE owner_id IS NULL", Map.of(), Long.class);
    }

    private TrainerClient link(User trainer, User client) {
        TrainerClient link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        return trainerClientRepository.save(link);
    }

    private Exercise exerciseOwnedBy(User trainer) {
        Exercise exercise = new Exercise();
        exercise.setName("Private of " + trainer.getName());
        exercise.setOwner(trainer);
        return exerciseRepository.save(exercise);
    }

    private long seedPlan(User trainer, User client) {
        TrainingPlan plan = new TrainingPlan();
        plan.setName("Plan of " + trainer.getName());
        plan.setTrainer(trainer);
        plan.setClient(client);
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setLevel(PlanLevel.INTERMEDIATE);
        return trainingPlanRepository.save(plan).getId();
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@plan-authz.example.com");
        user.setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)));
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
