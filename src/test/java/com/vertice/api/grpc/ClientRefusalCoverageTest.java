package com.vertice.api.grpc;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.vertice.api.plan.PlanLevel;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
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
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.vertice.api.plan.PlanCallers.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client can never perform a trainer action, whichever app sends it (web-trainer-only-access
 * PRD, R12–R17). The counterpart of {@link RpcAuthorizationCoverageTest} for the CLIENT role: every
 * bound RPC outside {@link #CLIENT_ALLOWED} (the CLAUDE.md matrix rows that name a CLIENT) is
 * called with a real CLIENT token and must answer {@code PERMISSION_DENIED}. The methods are read
 * from the bound services, so a new trainer-only RPC is covered the moment it exists.
 *
 * <p>The caller is the most permissive client there is: the client of a real plan, with a
 * workout, a workout exercise and a set under it. Every id field in the request (top level or
 * nested) is filled with the matching id of that tree, so a rule that let "the plan's trainer or
 * client" through where only the trainer belongs would accept the call and fail here, instead of
 * being hidden behind a missing-id refusal. The other fields get a valid value
 * ({@link #validValue}), because several controllers validate their input before asking who is
 * calling, and an {@code INVALID_ARGUMENT} would prove nothing. Afterwards nothing may have
 * changed (R16).
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19127", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientRefusalCoverageTest {

    private static final Set<String> INFRASTRUCTURE_SERVICES = Set.of(
            "grpc.health.v1.Health", "grpc.reflection.v1.ServerReflection", "grpc.reflection.v1alpha.ServerReflection");

    /** "Service/Method" (unqualified service name) a CLIENT may call, per the CLAUDE.md matrix. */
    private static final Set<String> CLIENT_ALLOWED = Set.of(
            "UserService/GetUser", "UserService/UpdateUser", "UserService/SetUserPassword", "UserService/DeleteUser",
            "UserService/DeleteAccount", "UserService/ExportUserData", "UserService/RecordConsent",
            "UserService/VerifyCredentials",
            "TrainingPlanService/ListTrainingPlans", "TrainingPlanService/GetTrainingPlan",
            "WorkoutService/ListWorkouts", "WorkoutService/GetWorkout",
            "WorkoutExerciseService/ListWorkoutExercises", "WorkoutExerciseService/GetWorkoutExercise",
            "ExerciseSetService/ListExerciseSets", "ExerciseSetService/GetExerciseSet",
            "WorkoutSessionService/GetOrStartWorkoutLog", "WorkoutSessionService/RestartWorkoutLog",
            "WorkoutSessionService/DiscardWorkoutLog", "WorkoutSessionService/DeleteSetLog",
            "WorkoutSessionService/ListSetLogs", "WorkoutSessionService/GetWorkoutLog",
            "WorkoutSessionService/RecordSetLog", "WorkoutSessionService/CompleteWorkoutLog",
            "WorkoutSessionService/ListWorkoutLogs", "WorkoutSessionService/GetLastSetLogs",
            "WorkoutSessionService/GetExerciseProgress",
            "WorkoutFeedbackService/SubmitWorkoutFeedback",
            "TrainerClientService/GetClientTrainer",
            "ExerciseService/GetExercise", "ExerciseService/ListMuscleGroups");

    /** Tables a refused call could have added rows to or removed rows from (R16). */
    private static final List<String> TABLES = List.of("users", "trainer_clients", "training_plans", "workouts",
            "workout_exercises", "exercise_sets", "exercises", "exercise_muscle_groups", "workout_logs", "set_logs",
            "workout_feedback", "password_setup_tokens");

    @Autowired
    private List<BindableService> services;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
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

    private ManagedChannel channel;
    private User trainer;
    private User client;
    private TrainerClient link;
    private Exercise exercise;
    private TrainingPlan plan;
    private Workout workout;
    private Map<String, Object> snapshot;

    @BeforeAll
    void seed() {
        channel = NettyChannelBuilder.forTarget("localhost:19127").usePlaintext().build();

        trainer = userRepository.save(newUser("Trainer", Role.TRAINER, validCpf()));
        client = userRepository.save(newUser("Client", Role.CLIENT, null));
        link = new TrainerClient();
        link.setTrainer(trainer);
        link.setClient(client);
        link.setStartedAt(Instant.now());
        link = trainerClientRepository.save(link);

        exercise = new Exercise();
        exercise.setName("Trainer's own squat");
        exercise.setOwner(trainer);
        exercise = exerciseRepository.save(exercise);

        plan = new TrainingPlan();
        plan.setName("Plan");
        plan.setTrainer(trainer);
        plan.setClient(client);
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setLevel(PlanLevel.INTERMEDIATE);
        plan = trainingPlanRepository.save(plan);
        workout = seedWorkout(plan);

        snapshot = snapshot();
    }

    @AfterAll
    void cleanUp() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        trainingPlanRepository.deleteById(plan.getId());
        exerciseRepository.deleteById(exercise.getId());
        trainerClientRepository.delete(link);
        userRepository.deleteById(client.getId());
        userRepository.deleteById(trainer.getId());
    }

    @TestFactory
    Stream<DynamicTest> everyTrainerRpc_refusesThePlansOwnClient_andChangesNothing() {
        List<MethodDescriptor<?, ?>> methods = boundMethods();
        assertThat(methods).extracting(ClientRefusalCoverageTest::key).containsAll(CLIENT_ALLOWED);

        List<DynamicTest> tests = new ArrayList<>();
        for (MethodDescriptor<?, ?> method : methods) {
            if (CLIENT_ALLOWED.contains(key(method))) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(key(method) + " as the plan's CLIENT",
                    () -> assertCode(() -> call(method), Status.Code.PERMISSION_DENIED)));
        }
        tests.add(DynamicTest.dynamicTest("no refused call changed anything",
                () -> assertThat(snapshot()).isEqualTo(snapshot)));
        return tests.stream();
    }

    private List<MethodDescriptor<?, ?>> boundMethods() {
        return services.stream()
                .map(BindableService::bindService)
                .filter(definition -> !INFRASTRUCTURE_SERVICES.contains(definition.getServiceDescriptor().getName()))
                .flatMap(definition -> definition.getMethods().stream())
                .<MethodDescriptor<?, ?>>map(method -> method.getMethodDescriptor())
                .toList();
    }

    /** "Service/Method" without the proto package. */
    private static String key(MethodDescriptor<?, ?> method) {
        String service = method.getServiceName();
        return service.substring(service.lastIndexOf('.') + 1) + "/" + method.getBareMethodName();
    }

    @SuppressWarnings("unchecked")
    private void call(MethodDescriptor<?, ?> method) {
        assertThat(method.getType()).as("only unary RPCs are probed").isEqualTo(MethodDescriptor.MethodType.UNARY);
        var descriptor = (MethodDescriptor<Object, Object>) method;
        Message prototype = (Message) ((MethodDescriptor.PrototypeMarshaller<?>) method.getRequestMarshaller()).getMessagePrototype();
        String service = key(method).substring(0, key(method).indexOf('/'));
        ClientCalls.blockingUnaryCall(asClient(), descriptor,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), withTreeIds(prototype.toBuilder(), service).build());
    }

    /**
     * Fills every id field, nested messages included, with the id of the seeded tree it names, and
     * every other field the RPCs validate with a valid value.
     */
    private Message.Builder withTreeIds(Message.Builder builder, String service) {
        for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
            if (field.isRepeated()) {
                if (field.getName().equals("muscle_group_ids")) {
                    builder.addRepeatedField(field, jdbc.queryForObject("SELECT min(id) FROM muscle_groups", Map.of(), Long.class));
                }
                continue;
            }
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                builder.setField(field, withTreeIds(((Message) builder.getField(field)).toBuilder(), service).build());
            } else if (field.getJavaType() == FieldDescriptor.JavaType.LONG) {
                Long id = idFor(field.getName(), service);
                if (id != null) {
                    builder.setField(field, id);
                }
            } else if (field.getJavaType() == FieldDescriptor.JavaType.ENUM) {
                // The first value after *_UNSPECIFIED: a set level, weekday, strategy or role.
                builder.setField(field, field.getEnumType().getValues().get(1));
            } else {
                Object value = validValue(field.getName());
                if (value != null) {
                    builder.setField(field, value);
                }
            }
        }
        return builder;
    }

    private Long idFor(String field, String service) {
        return switch (field) {
            case "training_plan_id", "target_training_plan_id" -> plan.getId();
            case "workout_id", "source_workout_id" -> workout.getId();
            case "workout_exercise_id" -> workout.getWorkoutExercises().getFirst().getId();
            case "exercise_set_id" -> firstSetId();
            case "exercise_id" -> exercise.getId();
            case "trainer_id" -> trainer.getId();
            case "client_id" -> client.getId();
            case "user_id" -> client.getId();
            case "id" -> switch (service) {
                case "TrainingPlanService" -> plan.getId();
                case "WorkoutService" -> workout.getId();
                case "WorkoutExerciseService" -> workout.getWorkoutExercises().getFirst().getId();
                case "ExerciseSetService" -> firstSetId();
                case "ExerciseService" -> exercise.getId();
                default -> client.getId();
            };
            default -> null;
        };
    }

    private static Object validValue(String field) {
        return switch (field) {
            case "name" -> "Probe";
            case "email" -> "probe-" + System.nanoTime() + "@client-refusal.example.com";
            case "password" -> "supersecret1";
            case "token", "accepted_terms_version" -> "probe";
            case "start_date" -> "2026-01-05";
            case "end_date" -> "2026-03-30";
            case "set_number", "order" -> 2;
            case "reps" -> 5;
            default -> null;
        };
    }

    private long firstSetId() {
        return workout.getWorkoutExercises().getFirst().getExerciseSets().getFirst().getId();
    }

    private Channel asClient() {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + TestJwts.token(client.getId(), Role.CLIENT));
        return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    /**
     * Row counts of every table a refused call could write, plus the full content of the seeded
     * rows (an in-place update, such as ending the relationship or renaming the workout, keeps the
     * counts).
     */
    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table : TABLES) {
            result.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Map.of(), Long.class));
        }
        Map<String, Object> ids = Map.of("trainer", trainer.getId(), "client", client.getId(), "link", link.getId(),
                "exercise", exercise.getId(), "plan", plan.getId(), "workout", workout.getId());
        Map<String, String> seeded = Map.of(
                "users", "t.id IN (:trainer, :client)",
                "trainer_clients", "t.id = :link",
                "exercises", "t.id = :exercise",
                "exercise_muscle_groups", "t.exercise_id = :exercise",
                "training_plans", "t.id = :plan",
                "workouts", "t.training_plan_id = :plan",
                "workout_exercises", "t.workout_id = :workout",
                "exercise_sets", "t.workout_exercise_id IN (SELECT id FROM workout_exercises WHERE workout_id = :workout)");
        seeded.forEach((table, where) -> result.put(table + " (seeded rows)", jdbc.queryForObject(
                "SELECT string_agg(t::text, ',' ORDER BY t.id) FROM " + table + " t WHERE " + where, ids, String.class)));
        return result;
    }

    private Workout seedWorkout(TrainingPlan owner) {
        Workout w = new Workout();
        w.setName("Leg Day");
        w.setDayOfWeek(DayOfWeek.MONDAY);
        w.setTrainingPlan(owner);

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

    private static User newUser(String name, Role role, String cpf) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + System.nanoTime() + "@client-refusal.example.com");
        user.setCpf(cpf);
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }

    /** A random, check-digit-valid CPF, so parallel runs don't collide on {@code uq_users_cpf}. */
    private static String validCpf() {
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) {
            digits[i] = ThreadLocalRandom.current().nextInt(10);
        }
        digits[9] = cpfCheckDigit(digits, 9);
        digits[10] = cpfCheckDigit(digits, 10);
        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static int cpfCheckDigit(int[] digits, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += digits[i] * (count + 1 - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
