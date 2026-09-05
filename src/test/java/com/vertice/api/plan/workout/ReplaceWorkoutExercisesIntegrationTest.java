package com.vertice.api.plan.workout;

import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ReplaceWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.generated.grpc.plan.v1.WorkoutServiceGrpc;
import com.vertice.api.generated.grpc.session.v1.GetOrStartWorkoutLogRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutSessionServiceGrpc;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.plan.exercise.MuscleGroup;
import com.vertice.api.plan.session.SetLog;
import com.vertice.api.plan.session.SetLogRepository;
import com.vertice.api.plan.session.WorkoutLog;
import com.vertice.api.plan.session.WorkoutLogRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * Covers R12/R13/E8/R14/E9 of docs/specs/create-workout-with-exercises/spec.md §7: whether
 * {@code ReplaceWorkoutExercises} refuses to drop recorded {@link SetLog} data needs real JPA
 * association behavior (lazy-loaded {@code exerciseSet.workoutExercise.workout} chains,
 * cascading deletes), which a {@code @MockitoBean}-replaced {@code WorkoutService} — the pattern
 * every other {@code *ControllerTest} in this codebase uses — can't exercise. This test runs the
 * real service/repository stack against Postgres instead, seeding data directly via repositories
 * (per spec §7's "or a direct repository seed" allowance for R12/R13) and cleaning it up in
 * {@code @AfterEach} since there is no transactional rollback here.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19103", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class ReplaceWorkoutExercisesIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainingPlanRepository trainingPlanRepository;
    @Autowired
    private WorkoutRepository workoutRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Autowired
    private ExerciseSetRepository exerciseSetRepository;
    @Autowired
    private SetLogRepository setLogRepository;
    @Autowired
    private WorkoutLogRepository workoutLogRepository;

    private ManagedChannel channel;
    private WorkoutServiceGrpc.WorkoutServiceBlockingStub workoutStub;
    private WorkoutSessionServiceGrpc.WorkoutSessionServiceBlockingStub sessionStub;

    private User trainer;
    private User client;
    private TrainingPlan trainingPlan;
    private Exercise exercise;
    private Workout workout;
    private WorkoutExercise workoutExercise;
    private ExerciseSet exerciseSet;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19103").usePlaintext().build();
        workoutStub = WorkoutServiceGrpc.newBlockingStub(channel);
        sessionStub = WorkoutSessionServiceGrpc.newBlockingStub(channel);

        trainer = userRepository.save(newUser("Trainer", Role.TRAINER));
        client = userRepository.save(newUser("Client", Role.CLIENT));

        trainingPlan = new TrainingPlan();
        trainingPlan.setName("Plan");
        trainingPlan.setTrainer(trainer);
        trainingPlan.setClient(client);
        trainingPlan.setStartDate(LocalDate.of(2026, 1, 1));
        trainingPlan.setEndDate(LocalDate.of(2026, 12, 31));
        trainingPlan.setLevel(com.vertice.api.plan.PlanLevel.INTERMEDIATE);
        trainingPlan = trainingPlanRepository.save(trainingPlan);

        exercise = new Exercise();
        exercise.setName("Bench Press");
        exercise.setMuscleGroup(MuscleGroup.CHEST);
        exercise = exerciseRepository.save(exercise);

        workout = new Workout();
        workout.setName("Push Day");
        workout.setDayOfWeek(DayOfWeek.MONDAY);
        workout.setTrainingPlan(trainingPlan);
        workout = workoutRepository.save(workout);

        workoutExercise = new WorkoutExercise();
        workoutExercise.setWorkout(workout);
        workoutExercise.setExercise(exercise);
        workoutExercise.setOrder(1);
        workoutExercise = workoutExerciseRepository.save(workoutExercise);

        exerciseSet = new ExerciseSet();
        exerciseSet.setWorkoutExercise(workoutExercise);
        exerciseSet.setSetNumber(2);
        exerciseSet.setReps(10);
        exerciseSet.setStrategy(SetStrategy.STRAIGHT);
        exerciseSet = exerciseSetRepository.save(exerciseSet);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        setLogRepository.deleteAll(setLogRepository.findByWorkoutId(workout.getId()));
        workoutLogRepository.findByWorkoutIdAndClientIdAndWeekStartDate(workout.getId(), client.getId(), LocalDate.of(2026, 3, 2))
                .ifPresent(workoutLogRepository::delete);
        workoutRepository.deleteById(workout.getId());
        trainingPlanRepository.deleteById(trainingPlan.getId());
        exerciseRepository.deleteById(exercise.getId());
        userRepository.deleteById(client.getId());
        userRepository.deleteById(trainer.getId());

        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void replaceWorkoutExercises_whenSetLogRecorded_throwsFailedPreconditionAndWritesNothing() {
        SetLog setLog = new SetLog();
        setLog.setWorkoutLog(seedWorkoutLog());
        setLog.setExerciseSet(exerciseSet);
        setLog.setReps(10);
        setLog.setWeight(BigDecimal.valueOf(60));
        setLog.setRecordedAt(Instant.now());
        setLogRepository.save(setLog);

        ReplaceWorkoutExercisesRequest request = ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(workout.getId())
                .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(exercise.getId()).build())
                .build();

        assertThatThrownBy(() -> workoutStub.replaceWorkoutExercises(request))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(ex.getStatus().getDescription()).contains("Bench Press").contains("set 2");
                });

        // Nothing changed: the original WorkoutExercise/ExerciseSet is still there.
        assertThat(workoutExerciseRepository.findByWorkoutId(workout.getId())).hasSize(1);
        assertThat(exerciseSetRepository.findByWorkoutExerciseId(workoutExercise.getId())).hasSize(1);
    }

    @Test
    void replaceWorkoutExercises_whenWorkoutLogOpenedButEmpty_succeeds() {
        sessionStub.getOrStartWorkoutLog(GetOrStartWorkoutLogRequest.newBuilder()
                .setWorkoutId(workout.getId())
                .setClientId(client.getId())
                .setWeekStartDate("2026-03-02")
                .build());

        ReplaceWorkoutExercisesRequest request = ReplaceWorkoutExercisesRequest.newBuilder()
                .setWorkoutId(workout.getId())
                .addExercises(WorkoutExerciseEntry.newBuilder().setExerciseId(exercise.getId())
                        .addSets(ExerciseSetEntry.newBuilder().setReps(12).build())
                        .build())
                .build();

        WorkoutResponse response = workoutStub.replaceWorkoutExercises(request);

        assertThat(response.getId()).isEqualTo(workout.getId());
        var newTree = workoutExerciseRepository.findByWorkoutId(workout.getId());
        assertThat(newTree).hasSize(1);
        assertThat(newTree.getFirst().getId()).isNotEqualTo(workoutExercise.getId());
        assertThat(exerciseSetRepository.findByWorkoutExerciseId(newTree.getFirst().getId())).hasSize(1);
    }

    private WorkoutLog seedWorkoutLog() {
        WorkoutLog workoutLog = new WorkoutLog();
        workoutLog.setWorkout(workout);
        workoutLog.setClient(client);
        workoutLog.setWeekStartDate(LocalDate.of(2026, 3, 2));
        workoutLog.setStartedAt(Instant.now());
        return workoutLogRepository.save(workoutLog);
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@example.com");
        user.setCpf(String.valueOf(System.nanoTime()).substring(0, 11));
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
