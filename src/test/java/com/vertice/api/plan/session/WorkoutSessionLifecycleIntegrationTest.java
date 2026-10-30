package com.vertice.api.plan.session;

import com.vertice.api.plan.PlanLevel;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import com.vertice.api.common.time.BusinessDates;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.CompletionKind;
import com.vertice.api.generated.grpc.session.v1.DeleteSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.DiscardWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.FeedbackRating;
import com.vertice.api.generated.grpc.session.v1.GetLastSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.ListProgressExercisesRequest;
import com.vertice.api.generated.grpc.session.v1.ListSetLogsRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.ListWorkoutLogsRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.RestartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogStatus;
import com.vertice.api.generated.grpc.session.v1.ProgressExercise;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * The session lifecycle against the database (client workout experience, PRD R19–R28, R34, R38,
 * R66–R72): the partial unique indexes allow a redo next to a completed session; finishing the redo
 * supersedes the original (and only then); discarding a redo leaves the original; unticking and
 * blank weights; the weekly rollover; the feedback rating and the "replaced" flag.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19121", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + WorkoutSessionLifecycleIntegrationTest.SECRET})
class WorkoutSessionLifecycleIntegrationTest {

    static final String SECRET = "non-local-session-lifecycle-test-secret!";

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
    @Autowired
    private WorkoutWeekRollover rollover;

    private final String week = BusinessDates.currentWeekStart(Clock.systemUTC()).toString();
    private ManagedChannel channel;
    private User trainer;
    private User client;
    private long workout;
    private long exerciseId;
    private long firstSet;
    private long secondSet;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19121").usePlaintext().build();
        trainer = userRepository.save(newUser("Trainer", Role.TRAINER));
        client = userRepository.save(newUser("Client", Role.CLIENT));
        TrainerClient link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        trainerClientRepository.save(link);
        Exercise exercise = new Exercise();
        exercise.setName("Private of Trainer");
        exercise.setOwner(trainer);
        exercise = exerciseRepository.save(exercise);
        exerciseId = exercise.getId();

        TrainingPlan trainingPlan = new TrainingPlan();
        trainingPlan.setName("Plan");
        trainingPlan.setTrainer(trainer);
        trainingPlan.setClient(client);
        trainingPlan.setStartDate(LocalDate.of(2026, 1, 1));
        trainingPlan.setEndDate(LocalDate.of(2027, 12, 31));
        trainingPlan.setLevel(PlanLevel.INTERMEDIATE);
        long plan = trainingPlanRepository.save(trainingPlan).getId();
        workout = as(trainer, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder().setName("Leg Day").setTrainingPlanId(plan)
                        .setDayOfWeek(DayOfWeek.MONDAY)
                        .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(exercise.getId())
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5))
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5)))
                        .build()).getId();
        long workoutExercise = as(trainer, WorkoutExerciseServiceGrpc::newBlockingStub).listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(workout).build()).getWorkoutExercises(0).getId();
        List<ExerciseSetResponse> sets = as(trainer, ExerciseSetServiceGrpc::newBlockingStub).listExerciseSets(
                ListExerciseSetsRequest.newBuilder().setWorkoutExerciseId(workoutExercise).build()).getExerciseSetsList();
        firstSet = sets.get(0).getId();
        secondSet = sets.get(1).getId();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, List<Long>> users = Map.of("ids", List.of(trainer.getId(), client.getId()));
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
    void aRedo_replacesTheCompletedSession_onlyOnceItIsFinished() {
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions = sessions();
        WorkoutLogResponse original = finishWithWeight(start(week), "80");
        as(client, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder()
                .setWorkoutLogId(original.getId()).setRating(FeedbackRating.FEEDBACK_RATING_HARD).build());

        // Opening the finished workout again returns the completed session; a redo is explicit.
        assertThat(start(week).getId()).isEqualTo(original.getId());
        WorkoutLogResponse redo = sessions.restartWorkoutLog(RestartWorkoutLogRequest.newBuilder()
                .setWorkoutId(workout).setClientId(client.getId()).setWeekStartDate(week).build());
        assertThat(redo.getId()).isNotEqualTo(original.getId());
        assertThat(redo.getStatus()).isEqualTo(WorkoutLogStatus.WORKOUT_LOG_STATUS_IN_PROGRESS);
        assertThat(start(week).getId()).as("the redo is what's resumed now").isEqualTo(redo.getId());
        assertThat(statusOf(original.getId())).isEqualTo("COMPLETED");

        finishWithWeight(redo, "90");

        assertThat(statusOf(original.getId())).isEqualTo("SUPERSEDED");
        assertThat(weekLogs()).extracting(WorkoutLogResponse::getId).containsExactly(redo.getId());
        assertThat(sessions.getLastSetLogs(GetLastSetLogsRequest.newBuilder().setClientId(client.getId()).setWorkoutId(workout).build())
                .getSetLogsList()).extracting(s -> s.getWeight()).containsExactly("90.00");
        var feedback = as(trainer, WorkoutFeedbackServiceGrpc::newBlockingStub)
                .listWorkoutFeedback(ListWorkoutFeedbackRequest.newBuilder().setTrainerId(trainer.getId()).build())
                .getWorkoutFeedbackList();
        assertThat(feedback).singleElement().satisfies(f -> {
            assertThat(f.getRating()).isEqualTo(FeedbackRating.FEEDBACK_RATING_HARD);
            assertThat(f.getText()).isEmpty();
            assertThat(f.getReplaced()).isTrue();
        });
    }

    @Test
    void discardingARedo_keepsTheCompletedSession_andACompletedOneCantBeDiscarded() {
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions = sessions();
        WorkoutLogResponse original = finishWithWeight(start(week), "80");
        WorkoutLogResponse redo = sessions.restartWorkoutLog(RestartWorkoutLogRequest.newBuilder()
                .setWorkoutId(workout).setClientId(client.getId()).setWeekStartDate(week).build());
        record(redo.getId(), firstSet, "85");

        sessions.discardWorkoutLog(DiscardWorkoutLogRequest.newBuilder().setId(redo.getId()).build());

        assertThat(count("SELECT count(*) FROM workout_logs WHERE id = :id", redo.getId())).isZero();
        assertThat(count("SELECT count(*) FROM set_logs WHERE workout_log_id = :id", redo.getId())).isZero();
        assertThat(statusOf(original.getId())).isEqualTo("COMPLETED");
        assertFailedPrecondition(() -> sessions.discardWorkoutLog(DiscardWorkoutLogRequest.newBuilder().setId(original.getId()).build()));
    }

    @Test
    void aSetCanBeUnticked_andTickedWithoutAWeight_untilTheSessionIsFinished() {
        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions = sessions();
        WorkoutLogResponse log = start(week);
        record(log.getId(), firstSet, "40");
        record(log.getId(), secondSet, "");

        sessions.deleteSetLog(DeleteSetLogRequest.newBuilder().setWorkoutLogId(log.getId()).setExerciseSetId(firstSet).build());
        sessions.deleteSetLog(DeleteSetLogRequest.newBuilder().setWorkoutLogId(log.getId()).setExerciseSetId(firstSet).build());

        var logged = sessions.listSetLogs(ListSetLogsRequest.newBuilder().setWorkoutLogId(log.getId()).build()).getSetLogsList();
        assertThat(logged).singleElement().satisfies(s -> {
            assertThat(s.getExerciseSetId()).isEqualTo(secondSet);
            assertThat(s.getWeight()).isEmpty();
        });
        sessions.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(log.getId()).build());
        assertFailedPrecondition(() -> sessions.deleteSetLog(
                DeleteSetLogRequest.newBuilder().setWorkoutLogId(log.getId()).setExerciseSetId(secondSet).build()));
    }

    @Test
    void theWeeklyRollover_finishesLeftoversWithASet_andDropsEmptyOnes() {
        long withSet = seedLeftover(1);
        seedSetLog(withSet, firstSet);
        long empty = seedLeftover(2);

        rollover.rollOver();

        assertThat(statusOf(withSet)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT completion_kind FROM workout_logs WHERE id = :id",
                Map.of("id", withSet), String.class)).isEqualTo("AUTO");
        assertThat(count("SELECT count(*) FROM workout_logs WHERE id = :id", empty)).isZero();
    }

    /**
     * An automatic finish takes the time of the last set, not the rollover's: an old partial session
     * closed today doesn't outrank last week's full one as "last time", and takes no feedback.
     */
    @Test
    void anAutomaticFinish_keepsTheTimeOfTheLastSet_andTakesNoFeedback() {
        long lastWeek = seedLeftover(1);
        jdbc.update("UPDATE workout_logs SET status = 'COMPLETED', completion_kind = 'CLIENT', completed_at = now() WHERE id = :id",
                Map.of("id", lastWeek));
        jdbc.update("INSERT INTO set_logs (workout_log_id, exercise_set_id, weight, reps, recorded_at) VALUES (:log, :set, 80, 5, now())",
                Map.of("log", lastWeek, "set", firstSet));
        long twoWeeksAgo = seedLeftover(2);
        jdbc.update("INSERT INTO set_logs (workout_log_id, exercise_set_id, weight, reps, recorded_at) "
                + "VALUES (:log, :set, 60, 5, TIMESTAMPTZ '2026-01-05 10:00:00+00')", Map.of("log", twoWeeksAgo, "set", firstSet));

        start(week);

        assertThat(jdbc.queryForObject("SELECT completed_at = TIMESTAMPTZ '2026-01-05 10:00:00+00' FROM workout_logs WHERE id = :id",
                Map.of("id", twoWeeksAgo), Boolean.class)).isTrue();
        assertThat(sessions().getLastSetLogs(GetLastSetLogsRequest.newBuilder().setClientId(client.getId()).setWorkoutId(workout).build())
                .getSetLogsList()).extracting(s -> s.getWeight()).containsExactly("80.00");
        assertFailedPrecondition(() -> as(client, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(
                SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(twoWeeksAgo).setRating(FeedbackRating.FEEDBACK_RATING_HARD).build()));
    }

    @Test
    void startingANewWeek_rollsOverTheClientsLeftovers_too() {
        long lastWeek = seedLeftover(1);
        seedSetLog(lastWeek, firstSet);

        start(week);

        assertThat(statusOf(lastWeek)).isEqualTo("COMPLETED");
    }

    /** Two passes over the same leftover (two devices on Monday morning) both succeed. */
    @Test
    void concurrentRolloverPasses_bothSucceed() throws Exception {
        long empty = seedLeftover(1);
        ListWorkoutLogsRequest list = ListWorkoutLogsRequest.newBuilder().setClientId(client.getId())
                .setTrainingPlanId(planId()).setWeekStartDate(week).build();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> calls = List.of(pool.submit(() -> sessions().listWorkoutLogs(list)),
                    pool.submit(() -> sessions().listWorkoutLogs(list)));
            for (Future<?> call : calls) {
                call.get(30, TimeUnit.SECONDS);
            }
        }
        assertThat(count("SELECT count(*) FROM workout_logs WHERE id = :id", empty)).isZero();
    }

    @Test
    void aSessionCanOnlyBeStartedInTheCurrentWeek() {
        String lastWeek = LocalDate.parse(week).minusWeeks(1).toString();
        assertFailedPrecondition(() -> start(lastWeek));
        assertFailedPrecondition(() -> sessions().restartWorkoutLog(RestartWorkoutLogRequest.newBuilder()
                .setWorkoutId(workout).setClientId(client.getId()).setWeekStartDate(lastWeek).build()));
    }

    @Test
    void feedbackNeedsARating() {
        WorkoutLogResponse log = finishWithWeight(start(week), "80");
        assertThatThrownBy(() -> as(client, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(
                SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(log.getId()).setText("Só texto").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    /** The app resends a feedback whose answer was lost: the same key gives back the same row. */
    @Test
    void feedbackWithAClientKey_isStoredOnce() {
        WorkoutLogResponse log = finishWithWeight(start(week), "80");
        var feedback = as(client, WorkoutFeedbackServiceGrpc::newBlockingStub);
        var first = feedback.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(log.getId())
                .setRating(FeedbackRating.FEEDBACK_RATING_HARD).setText("Pesado").setClientKey("k-1").build());
        var resend = feedback.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(log.getId())
                .setRating(FeedbackRating.FEEDBACK_RATING_HARD).setText("Pesado").setClientKey("k-1").build());
        assertThat(resend.getId()).isEqualTo(first.getId());
        assertThat(feedbackRows(log.getId())).isEqualTo(1);

        feedback.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(log.getId())
                .setRating(FeedbackRating.FEEDBACK_RATING_EASY).setClientKey("k-2").build());
        assertThat(feedbackRows(log.getId())).isEqualTo(2);

        // The index stands behind the lock: a duplicate key for the session can't be inserted.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workout_feedback (workout_log_id, text, rating, created_at, client_key) "
                + "VALUES (:log, '', 'HARD', now(), 'k-1')", Map.of("log", log.getId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThatThrownBy(() -> feedback.submitWorkoutFeedback(SubmitWorkoutFeedbackRequest.newBuilder().setWorkoutLogId(log.getId())
                .setRating(FeedbackRating.FEEDBACK_RATING_EASY).setClientKey("a b").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    /**
     * The progress picker's options (lazy-exercise-search §6 follow-up): only a weighted set in a
     * completed session counts, as for GetExerciseProgress; in-progress and weightless sets don't.
     */
    @Test
    void progressExercises_areThoseWithAWeightedCompletedSet() {
        WorkoutLogResponse log = start(week);
        record(log.getId(), firstSet, "60");
        assertThat(progressExercises(trainer)).as("still in progress").isEmpty();

        long weightless = seedLeftover(1);
        jdbc.update("UPDATE workout_logs SET status = 'COMPLETED', completion_kind = 'CLIENT', completed_at = now() WHERE id = :id",
                Map.of("id", weightless));
        jdbc.update("INSERT INTO set_logs (workout_log_id, exercise_set_id, weight, reps, recorded_at) VALUES (:log, :set, NULL, 5, now())",
                Map.of("log", weightless, "set", firstSet));
        assertThat(progressExercises(trainer)).as("no weight recorded").isEmpty();

        sessions().completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(log.getId()).build());
        assertThat(progressExercises(trainer)).extracting(ProgressExercise::getId, ProgressExercise::getName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(exerciseId, "Private of Trainer"));
        assertThat(progressExercises(client)).extracting(ProgressExercise::getId).containsExactly(exerciseId);
    }

    /** Another trainer's private exercise in the client's history: the client sees it, this trainer doesn't. */
    @Test
    void progressExercises_hideAnotherTrainersPrivateExercise_fromTheTrainer() {
        finishWithWeight(start(week), "80");
        User other = userRepository.save(newUser("Other", Role.TRAINER));
        try {
            jdbc.update("UPDATE exercises SET owner_id = :other WHERE id = :id", Map.of("other", other.getId(), "id", exerciseId));

            assertThat(progressExercises(trainer)).isEmpty();
            assertThat(progressExercises(client)).extracting(ProgressExercise::getId).containsExactly(exerciseId);
        } finally {
            jdbc.update("UPDATE exercises SET owner_id = :trainer WHERE id = :id", Map.of("trainer", trainer.getId(), "id", exerciseId));
            jdbc.update("DELETE FROM users WHERE id = :id", Map.of("id", other.getId()));
        }
    }

    private List<ProgressExercise> progressExercises(User caller) {
        return as(caller, WorkoutSessionServiceGrpc::newBlockingStub).listProgressExercises(
                ListProgressExercisesRequest.newBuilder().setClientId(client.getId()).build()).getExercisesList();
    }

    private int feedbackRows(long logId) {
        return jdbc.queryForObject("SELECT count(*) FROM workout_feedback WHERE workout_log_id = :id", Map.of("id", logId), Integer.class);
    }

    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions() {
        return as(client, WorkoutSessionServiceGrpc::newBlockingStub);
    }

    private WorkoutLogResponse start(String weekStartDate) {
        return sessions().getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder().setWorkoutId(workout)
                .setClientId(client.getId()).setWeekStartDate(weekStartDate).build());
    }

    private void record(long logId, long setId, String weight) {
        sessions().recordSetLog(RecordSetLogRequest.newBuilder().setWorkoutLogId(logId).setExerciseSetId(setId)
                .setWeight(weight).setReps(5).build());
    }

    private WorkoutLogResponse finishWithWeight(WorkoutLogResponse log, String weight) {
        record(log.getId(), firstSet, weight);
        WorkoutLogResponse done = sessions().completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(log.getId()).build());
        assertThat(done.getStatus()).isEqualTo(WorkoutLogStatus.WORKOUT_LOG_STATUS_COMPLETED);
        assertThat(done.getCompletionKind()).isEqualTo(CompletionKind.COMPLETION_KIND_CLIENT);
        return done;
    }

    private List<WorkoutLogResponse> weekLogs() {
        return sessions().listWorkoutLogs(ListWorkoutLogsRequest.newBuilder().setClientId(client.getId())
                .setTrainingPlanId(planId()).setWeekStartDate(week).build()).getWorkoutLogsList();
    }

    private long planId() {
        return jdbc.queryForObject("SELECT training_plan_id FROM workouts WHERE id = :id", Map.of("id", workout), Long.class);
    }

    /** A session left in progress {@code weeksAgo} weeks back (the API only starts current-week ones). */
    private long seedLeftover(int weeksAgo) {
        return jdbc.queryForObject("""
                INSERT INTO workout_logs (workout_id, client_id, week_start_date, started_at, status)
                VALUES (:workout, :client, :week, now(), 'IN_PROGRESS') RETURNING id""",
                Map.of("workout", workout, "client", client.getId(), "week", LocalDate.parse(week).minusWeeks(weeksAgo)), Long.class);
    }

    private void seedSetLog(long logId, long setId) {
        jdbc.update("INSERT INTO set_logs (workout_log_id, exercise_set_id, weight, reps, recorded_at) VALUES (:log, :set, 70, 5, now())",
                Map.of("log", logId, "set", setId));
    }

    private String statusOf(long logId) {
        return jdbc.queryForObject("SELECT status FROM workout_logs WHERE id = :id", Map.of("id", logId), String.class);
    }

    private void assertFailedPrecondition(ThrowingCallable call) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    private long count(String sql, long id) {
        return jdbc.queryForObject(sql, Map.of("id", id), Long.class);
    }

    private <S extends AbstractStub<S>> S as(User user, Function<ManagedChannel, S> newStub) {
        return TestJwts.withBearer(newStub.apply(channel), TestJwts.token(SECRET, user.getId(), user.getRole()));
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@session-lifecycle.example.com");
        user.setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)));
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
