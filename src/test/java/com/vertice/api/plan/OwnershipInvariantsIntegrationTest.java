package com.vertice.api.plan;

import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanServiceGrpc;
import com.vertice.api.generated.grpc.plan.v1.UpdateTrainingPlanRequest;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.GetWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.RecordSetLogRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutLogResponse;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.plan.session.SetLogRepository;
import com.vertice.api.plan.session.WorkoutLogRepository;
import com.vertice.api.plan.workout.DayOfWeek;
import com.vertice.api.plan.workout.ExerciseSet;
import com.vertice.api.plan.workout.SetStrategy;
import com.vertice.api.plan.workout.Workout;
import com.vertice.api.plan.workout.WorkoutExercise;
import com.vertice.api.plan.workout.WorkoutRepository;
import com.vertice.api.trainerclient.TrainerClient;
import com.vertice.api.trainerclient.TrainerClientRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * REL-API-001/002: the invariants that tie a set, a workout log and a training plan to the right
 * workout, client and trainer, run through the real gRPC + JPA stack against Postgres (lazy
 * {@code exerciseSet.workoutExercise.workout} and {@code workout.trainingPlan.client} chains
 * included). Seeds via repositories and cleans up in {@code @AfterEach}, same as
 * {@code ReplaceWorkoutExercisesIntegrationTest}.
 *
 * <p>Fixture: {@code trainer} has {@code client} as an active client and a plan for them;
 * {@code otherClient} has no relationship with {@code trainer} but owns a second plan (seeded
 * directly, as data from before these rules would look). Calls are made as the caller the P1-01
 * rules let through, so what's checked here is the service-level invariant behind them.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19107", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class OwnershipInvariantsIntegrationTest {

    private static final String WEEK = "2026-03-02";
    private static final AtomicLong CPF_BASE = new AtomicLong(ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L));

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainerClientRepository trainerClientRepository;
    @Autowired
    private TrainingPlanRepository trainingPlanRepository;
    @Autowired
    private WorkoutRepository workoutRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private WorkoutLogRepository workoutLogRepository;
    @Autowired
    private SetLogRepository setLogRepository;

    private ManagedChannel channel;
    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessionStub;
    private TrainingPlanServiceGrpc.TrainingPlanServiceBlockingStub planStub;
    /** The client's session calls and the trainer's plan calls; see {@link #setUp()}. */
    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub clientSession;
    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub trainerSession;
    private TrainingPlanServiceGrpc.TrainingPlanServiceBlockingStub trainerPlans;

    private User trainer;
    private User client;
    private User otherClient;
    private TrainerClient link;
    private Exercise exercise;
    private final List<Long> planIds = new ArrayList<>();
    private Workout workout;
    private Workout otherWorkout;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19107").usePlaintext().build();
        sessionStub = WorkoutSessionServiceGrpc.newBlockingStub(channel);
        planStub = TrainingPlanServiceGrpc.newBlockingStub(channel);

        trainer = userRepository.save(newUser("Trainer", Role.TRAINER));
        client = userRepository.save(newUser("Client", Role.CLIENT));
        otherClient = userRepository.save(newUser("Other", Role.CLIENT));

        link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        link = trainerClientRepository.save(link);

        clientSession = TestJwts.asCaller(sessionStub, client.getId(), Role.CLIENT);
        trainerSession = TestJwts.asCaller(sessionStub, trainer.getId(), Role.TRAINER);
        trainerPlans = TestJwts.asCaller(planStub, trainer.getId(), Role.TRAINER);

        exercise = new Exercise();
        exercise.setName("Squat");
        exercise = exerciseRepository.save(exercise);

        workout = seedWorkout(seedPlan(client));
        otherWorkout = seedWorkout(seedPlan(otherClient));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (Workout w : List.of(workout, otherWorkout)) {
            setLogRepository.deleteAll(setLogRepository.findByWorkoutId(w.getId()));
            for (User c : List.of(client, otherClient)) {
                workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(w.getId(), c.getId(), LocalDate.parse(WEEK))
                        .ifPresent(workoutLogRepository::delete);
            }
        }
        planIds.forEach(trainingPlanRepository::deleteById);
        exerciseRepository.deleteById(exercise.getId());
        trainerClientRepository.delete(link);
        userRepository.deleteById(otherClient.getId());
        userRepository.deleteById(client.getId());
        userRepository.deleteById(trainer.getId());

        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void recordSetLog_acceptsSetFromLogsWorkoutAndRejectsSetFromAnotherWorkout() {
        WorkoutLogResponse log = startLog(clientSession, workout, client);

        var accepted = clientSession.recordSetLog(recordSet(log.getId(), firstSetId(workout)));
        assertThat(accepted.getWorkoutLogId()).isEqualTo(log.getId());

        assertFailedPrecondition(() -> clientSession.recordSetLog(recordSet(log.getId(), firstSetId(otherWorkout))));
        assertThat(setLogRepository.findByWorkoutLogId(log.getId())).hasSize(1);
    }

    /**
     * The trainer owns both plans, so authorization lets the call through and the service's own
     * "workout assigned to this client" rule is what refuses it. (As the client, the plan check
     * would refuse it first, with PERMISSION_DENIED.)
     */
    @Test
    void getOrStartWorkoutLog_acceptsOwnWorkoutAndRejectsAnotherClientsWorkout() {
        WorkoutLogResponse log = startLog(clientSession, workout, client);
        assertThat(log.getClientId()).isEqualTo(client.getId());

        assertFailedPrecondition(() -> startLog(trainerSession, otherWorkout, client));
        assertThat(workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(
                otherWorkout.getId(), client.getId(), LocalDate.parse(WEEK))).isEmpty();
    }

    @Test
    void createTrainingPlan_acceptsActiveClientAndRejectsUnlinkedClient() {
        TrainingPlanResponse created = trainerPlans.createTrainingPlan(createPlan(client));
        planIds.add(created.getId());
        assertThat(created.getClientId()).isEqualTo(client.getId());

        assertFailedPrecondition(() -> trainerPlans.createTrainingPlan(createPlan(otherClient)));
        assertThat(trainingPlanRepository.findByClientId(otherClient.getId())).hasSize(1); // only the seeded one
    }

    @Test
    void updateTrainingPlan_acceptsSameClientAndRejectsClientChange() {
        Long planId = workout.getTrainingPlan().getId();

        var updated = trainerPlans.updateTrainingPlan(updatePlan(planId, client, "Renamed"));
        assertThat(updated.getName()).isEqualTo("Renamed");

        assertFailedPrecondition(() -> trainerPlans.updateTrainingPlan(updatePlan(planId, otherClient, "Moved")));
        assertThat(trainingPlanRepository.findById(planId)).get()
                .satisfies(plan -> assertThat(plan.getClient().getId()).isEqualTo(client.getId()));
    }

    /** NOT_FOUND is ADMIN's answer for a missing id; anyone else gets PERMISSION_DENIED (no existence oracle). */
    @Test
    void getWorkoutLog_returnsOwnershipFieldsAndNotFoundForMissingId() {
        WorkoutLogResponse started = startLog(clientSession, workout, client);

        WorkoutLogResponse fetched = clientSession.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(started.getId()).build());

        assertThat(fetched).isEqualTo(started);
        assertThat(fetched.getClientId()).isEqualTo(client.getId());
        assertThat(fetched.getWorkoutId()).isEqualTo(workout.getId());

        var admin = TestJwts.asCaller(sessionStub, 1L, Role.ADMIN);
        assertThatThrownBy(() -> admin.getWorkoutLog(GetWorkoutLogRequest.newBuilder().setId(Long.MAX_VALUE).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    private static WorkoutLogResponse startLog(WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub as, Workout w, User c) {
        return as.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(w.getId()).setClientId(c.getId()).setWeekStartDate(WEEK).build());
    }

    private static RecordSetLogRequest recordSet(long workoutLogId, long exerciseSetId) {
        return RecordSetLogRequest.newBuilder()
                .setWorkoutLogId(workoutLogId).setExerciseSetId(exerciseSetId).setWeight("80").setReps(5).build();
    }

    private TrainingPlanCreateRequest createPlan(User c) {
        return TrainingPlanCreateRequest.newBuilder()
                .setName("Created").setTrainerId(trainer.getId()).setClientId(c.getId())
                .setStartDate("2026-01-05").setEndDate("2026-03-30")
                .setLevel(com.vertice.api.generated.grpc.plan.v1.PlanLevel.BEGINNER)
                .build();
    }

    private static UpdateTrainingPlanRequest updatePlan(long planId, User c, String name) {
        return UpdateTrainingPlanRequest.newBuilder()
                .setId(planId)
                .setTrainingPlan(TrainingPlanRequest.newBuilder()
                        .setName(name).setClientId(c.getId())
                        .setStartDate("2026-01-05").setEndDate("2026-03-30")
                        .setLevel(com.vertice.api.generated.grpc.plan.v1.PlanLevel.BEGINNER))
                .build();
    }

    private static void assertFailedPrecondition(Executable call) {
        assertThatThrownBy(call::execute)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    /** Reads the in-memory tree {@link #seedWorkout} built, so no lazy loading outside a transaction. */
    private static long firstSetId(Workout w) {
        return w.getWorkoutExercises().getFirst().getExerciseSets().getFirst().getId();
    }

    private TrainingPlan seedPlan(User planClient) {
        TrainingPlan plan = new TrainingPlan();
        plan.setName("Plan");
        plan.setTrainer(trainer);
        plan.setClient(planClient);
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setLevel(PlanLevel.INTERMEDIATE);
        plan = trainingPlanRepository.save(plan);
        planIds.add(plan.getId());
        return plan;
    }

    /** One workout with one exercise and one set, persisted through the workout's cascade. */
    private Workout seedWorkout(TrainingPlan plan) {
        Workout w = new Workout();
        w.setName("Leg Day");
        w.setDayOfWeek(DayOfWeek.MONDAY);
        w.setTrainingPlan(plan);

        WorkoutExercise workoutExercise = new WorkoutExercise();
        workoutExercise.setWorkout(w);
        workoutExercise.setExercise(exercise);
        workoutExercise.setOrder(1);
        w.getWorkoutExercises().add(workoutExercise);

        ExerciseSet set = new ExerciseSet();
        set.setWorkoutExercise(workoutExercise);
        set.setSetNumber(1);
        set.setReps(5);
        set.setStrategy(SetStrategy.STRAIGHT);
        workoutExercise.getExerciseSets().add(set);

        return workoutRepository.save(w);
    }

    /**
     * A unique, check-digit-valid CPF per call: a random 9-digit base per run plus a counter, so
     * users created in the same run never collide on {@code uq_users_cpf}.
     */
    private static String nextCpf() {
        String base = "%09d".formatted(CPF_BASE.getAndIncrement() % 1_000_000_000L);
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) {
            digits[i] = base.charAt(i) - '0';
        }
        digits[9] = cpfCheckDigit(digits, 9);
        digits[10] = cpfCheckDigit(digits, 10);
        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    /** Same algorithm as {@code CpfValidator}. */
    private static int cpfCheckDigit(int[] digits, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += digits[i] * (count + 1 - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@example.com");
        user.setCpf(nextCpf());
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
