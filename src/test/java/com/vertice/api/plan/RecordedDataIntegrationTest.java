package com.vertice.api.plan;

import com.vertice.api.generated.grpc.exercise.v1.DeleteExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.DayOfWeek;
import com.vertice.api.generated.grpc.plan.v1.DeleteExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.DeleteTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.DeleteWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.GetTrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.GetWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.ListExerciseSetsRequest;
import com.vertice.api.generated.grpc.plan.v1.ListWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.CompleteWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.FeedbackRating;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.generated.grpc.trainerclient.v1.EndTrainerClientRelationshipRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.DeleteUserRequest;
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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.vertice.api.plan.PlanCallers.assertDenied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * Deletes against a populated database (REL-API-003, P1-05): once a client has trained with
 * something, deleting it is refused with {@code FAILED_PRECONDITION} and a message naming what
 * blocks it, never a raw foreign-key error, and nothing is removed. Ending the trainer-client
 * relationship leaves that history in place.
 *
 * <p>The trainer has a plan for their client with one workout of one exercise and two sets; the
 * client logged the first set, completed the session and left feedback. Data is committed (the gRPC call runs on another
 * thread) and deleted afterwards.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19120", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.jwt.secret=" + RecordedDataIntegrationTest.SECRET})
class RecordedDataIntegrationTest {

    static final String SECRET = "non-local-recorded-data-test-secret!";
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
    private NamedParameterJdbcTemplate jdbc;

    private ManagedChannel channel;
    private User trainer;
    private User client;
    private User otherTrainer;
    private TrainerClient link;
    private Exercise exercise;
    private long plan;
    private long workout;
    private long workoutExercise;
    private long loggedSet;
    private long unloggedSet;
    private long workoutLog;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19120").usePlaintext().build();
        trainer = userRepository.save(newUser("Trainer", Role.TRAINER));
        client = userRepository.save(newUser("Client", Role.CLIENT));
        otherTrainer = userRepository.save(newUser("OtherTrainer", Role.TRAINER));
        link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        link = trainerClientRepository.save(link);
        exercise = new Exercise();
        exercise.setName("Private of Trainer");
        exercise.setOwner(trainer);
        exercise = exerciseRepository.save(exercise);

        plan = seedPlan();
        workout = as(trainer, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder().setName("Leg Day").setTrainingPlanId(plan)
                        .setDayOfWeek(DayOfWeek.MONDAY)
                        .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(exercise.getId())
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5))
                                .addSets(ExerciseSetEntry.newBuilder().setReps(5)))
                        .build()).getId();
        workoutExercise = as(trainer, WorkoutExerciseServiceGrpc::newBlockingStub).listWorkoutExercises(
                ListWorkoutExercisesRequest.newBuilder().setWorkoutId(workout).build()).getWorkoutExercises(0).getId();
        List<ExerciseSetResponse> sets = as(trainer, ExerciseSetServiceGrpc::newBlockingStub).listExerciseSets(
                ListExerciseSetsRequest.newBuilder().setWorkoutExerciseId(workoutExercise).build()).getExerciseSetsList();
        loggedSet = sets.get(0).getId();
        unloggedSet = sets.get(1).getId();

        WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessions = as(client, WorkoutSessionServiceGrpc::newBlockingStub);
        workoutLog = sessions.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder().setWorkoutId(workout)
                .setClientId(client.getId()).setWeekStartDate(WEEK).build()).getId();
        sessions.recordSetLog(RecordSetLogRequest.newBuilder().setWorkoutLogId(workoutLog).setExerciseSetId(loggedSet)
                .setWeight("80").setReps(5).build());
        sessions.completeWorkoutLog(CompleteWorkoutLogRequest.newBuilder().setId(workoutLog).build());
        as(client, WorkoutFeedbackServiceGrpc::newBlockingStub).submitWorkoutFeedback(
                SubmitWorkoutFeedbackRequest.newBuilder().setRating(FeedbackRating.FEEDBACK_RATING_HARD).setWorkoutLogId(workoutLog).setText("Heavy today").build());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        Map<String, List<Long>> users = Map.of("ids", List.of(trainer.getId(), client.getId(), otherTrainer.getId()));
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
    void everyDeleteOverLoggedData_isRefused_andRemovesNothing() {
        assertRefused(() -> as(trainer, ExerciseSetServiceGrpc::newBlockingStub)
                        .deleteExerciseSet(DeleteExerciseSetRequest.newBuilder().setId(loggedSet).build()),
                "ExerciseSet %d has logged results and cannot be deleted".formatted(loggedSet));
        assertRefused(() -> as(trainer, WorkoutExerciseServiceGrpc::newBlockingStub)
                        .deleteWorkoutExercise(DeleteWorkoutExerciseRequest.newBuilder().setId(workoutExercise).build()),
                "WorkoutExercise %d has logged sets and cannot be deleted".formatted(workoutExercise));
        assertRefused(() -> as(trainer, WorkoutServiceGrpc::newBlockingStub)
                        .deleteWorkout(DeleteWorkoutRequest.newBuilder().setId(workout).build()),
                "Workout %d has workout logs and cannot be deleted".formatted(workout));
        assertRefused(() -> as(trainer, TrainingPlanServiceGrpc::newBlockingStub)
                        .deleteTrainingPlan(DeleteTrainingPlanRequest.newBuilder().setId(plan).build()),
                "TrainingPlan %d has workout logs and cannot be deleted".formatted(plan));
        assertRefused(() -> as(trainer, ExerciseServiceGrpc::newBlockingStub)
                        .deleteExercise(DeleteExerciseRequest.newBuilder().setId(exercise.getId()).build()),
                "Exercise %d is used by a workout and cannot be deleted".formatted(exercise.getId()));

        assertThat(count("SELECT count(*) FROM training_plans WHERE id = :id", plan)).isOne();
        assertThat(count("SELECT count(*) FROM exercise_sets WHERE workout_exercise_id = :id", workoutExercise)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM set_logs WHERE workout_log_id = :id", workoutLog)).isOne();
        assertThat(count("SELECT count(*) FROM workout_feedback WHERE workout_log_id = :id", workoutLog)).isOne();
    }

    /** The guards are about recorded data, not about the tree: what nobody logged still deletes. */
    @Test
    void whatNobodyLogged_stillDeletes() {
        as(trainer, ExerciseSetServiceGrpc::newBlockingStub)
                .deleteExerciseSet(DeleteExerciseSetRequest.newBuilder().setId(unloggedSet).build());
        assertThat(count("SELECT count(*) FROM exercise_sets WHERE id = :id", unloggedSet)).isZero();

        long emptyPlan = seedPlan();
        long emptyWorkout = as(trainer, WorkoutServiceGrpc::newBlockingStub).createWorkoutWithExercises(
                CreateWorkoutWithExercisesRequest.newBuilder().setName("Unused").setTrainingPlanId(emptyPlan)
                        .setDayOfWeek(DayOfWeek.FRIDAY)
                        .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(exercise.getId())
                                .addSets(ExerciseSetEntry.newBuilder().setReps(8)))
                        .build()).getId();
        as(trainer, TrainingPlanServiceGrpc::newBlockingStub)
                .deleteTrainingPlan(DeleteTrainingPlanRequest.newBuilder().setId(emptyPlan).build());
        assertThat(count("SELECT count(*) FROM training_plans WHERE id = :id", emptyPlan)).isZero();
        assertThat(count("SELECT count(*) FROM workouts WHERE id = :id", emptyWorkout)).isZero();
    }

    /**
     * DeleteUser used to fail on a foreign key for anyone with data (REL-API-003): now it is a
     * clear refusal for the client (plan and logs) and the trainer (plan, client, exercise), the
     * message carries no personal data, and both accounts stay. A user without data still deletes.
     */
    @Test
    void deleteUser_withTrainingData_isRefused_withoutPersonalDataInTheMessage() {
        for (User user : List.of(client, trainer)) {
            assertRefused(() -> as(user, UserServiceGrpc::newBlockingStub)
                            .deleteUser(DeleteUserRequest.newBuilder().setId(user.getId()).build()),
                    "User %d has training plans, workout logs, clients or exercises and cannot be deleted"
                            .formatted(user.getId()));
            assertThat(userRepository.findById(user.getId())).isPresent();
        }

        as(otherTrainer, UserServiceGrpc::newBlockingStub)
                .deleteUser(DeleteUserRequest.newBuilder().setId(otherTrainer.getId()).build());
        assertThat(userRepository.findById(otherTrainer.getId())).isEmpty();
    }

    /**
     * Ending the relationship (P1-05) only sets {@code ended_at}: plans, sets, logs and feedback
     * stay, the client keeps reading them, and the trainer keeps the plan-tree access of the plan
     * they wrote while losing what the active link gave (the client's detail view).
     */
    @Test
    void endingTheRelationship_keepsPlansAndHistory() {
        UserServiceGrpc.UserServiceBlockingStub usersAsTrainer = as(trainer, UserServiceGrpc::newBlockingStub);
        GetUserRequest getClient = GetUserRequest.newBuilder().setId(client.getId()).build();
        assertThat(usersAsTrainer.getUser(getClient).getEmail()).isEqualTo(client.getEmail());

        as(trainer, TrainerClientServiceGrpc::newBlockingStub).endTrainerClientRelationship(
                EndTrainerClientRelationshipRequest.newBuilder().setTrainerId(trainer.getId()).setClientId(client.getId()).build());

        assertThat(trainerClientRepository.findById(link.getId()).orElseThrow().getEndedAt()).isNotNull();
        assertThat(count("SELECT count(*) FROM set_logs WHERE workout_log_id = :id", workoutLog)).isOne();
        assertThat(count("SELECT count(*) FROM workout_feedback WHERE workout_log_id = :id", workoutLog)).isOne();

        GetTrainingPlanRequest getPlan = GetTrainingPlanRequest.newBuilder().setId(plan).build();
        assertThat(as(client, TrainingPlanServiceGrpc::newBlockingStub).getTrainingPlan(getPlan).getId()).isEqualTo(plan);
        assertThat(as(client, WorkoutServiceGrpc::newBlockingStub)
                .getWorkout(GetWorkoutRequest.newBuilder().setId(workout).build()).getId()).isEqualTo(workout);
        assertThat(as(trainer, TrainingPlanServiceGrpc::newBlockingStub).getTrainingPlan(getPlan).getId()).isEqualTo(plan);
        assertThat(as(trainer, WorkoutSessionServiceGrpc::newBlockingStub)
                .getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(workoutLog).build()).getId()).isEqualTo(workoutLog);

        // Name and role only now: the former client's contact details went with the link.
        UserResponse summary = usersAsTrainer.getUser(getClient);
        assertThat(summary.getName()).isEqualTo("Client");
        assertThat(summary.getEmail()).isEmpty();
        assertThat(userRepository.findById(client.getId()).orElseThrow().getEmail()).isEqualTo(client.getEmail());

        // The client is free for another trainer: the one-active-trainer index no longer holds them.
        TrainerClient next = new TrainerClient();
        next.setTrainer(otherTrainer);
        next.setClient(client);
        next.setStartedAt(Instant.now());
        trainerClientRepository.save(next);
        assertDenied(() -> as(trainer, TrainerClientServiceGrpc::newBlockingStub).endTrainerClientRelationship(
                EndTrainerClientRelationshipRequest.newBuilder().setTrainerId(trainer.getId()).setClientId(client.getId()).build()));
    }

    private void assertRefused(ThrowingCallable call, String description) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(ex.getStatus().getDescription()).isEqualTo(description);
                });
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

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@recorded-data.example.com");
        user.setCpf("%011d".formatted(Math.floorMod(System.nanoTime(), 100_000_000_000L)));
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
