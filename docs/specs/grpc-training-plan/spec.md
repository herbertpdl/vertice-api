# Spec: TrainingPlan gRPC CRUD

Status: Implemented — current; TrainingPlan is unrelated to the Trainer/Student → User rework.
Owner: hebertpdl@gmail.com
Related: `docs/specs/grpc-trainer/spec.md` (pattern), `docs/specs/grpc-exercise-catalog/spec.md`
(PR 1 of this same plan), `docs/domain-model.md`

## 1. Goal

Second PR in the plan/workout CRUD series. `TrainingPlan` CRUD, scoped to its parent `Trainer`.
Replaces the empty `TrainingPlanController` REST stub entirely — it never had any real endpoint.

## 2. Contract: `training_plan.proto`

`src/main/proto/vertice/plan/v1/training_plan.proto`, `package vertice.plan.v1`, `option
java_package = "com.vertice.api.generated.grpc.plan.v1"` — the shared package for all four
plan/workout proto files in this series (per the approved plan).

| RPC | Request | Response |
|---|---|---|
| `ListTrainingPlans` | `ListTrainingPlansRequest{trainer_id}` | `ListTrainingPlansResponse` (`repeated TrainingPlanResponse`) |
| `GetTrainingPlan` | `GetTrainingPlanRequest{id}` | `TrainingPlanResponse` |
| `CreateTrainingPlan` | `TrainingPlanCreateRequest{name,description,trainer_id}` | `TrainingPlanResponse` |
| `UpdateTrainingPlan` | `UpdateTrainingPlanRequest{id, TrainingPlanRequest training_plan}` | `TrainingPlanResponse` |
| `DeleteTrainingPlan` | `DeleteTrainingPlanRequest{id}` | `google.protobuf.Empty` |

`TrainingPlanResponse{int64 id; string name; string description; int64 trainer_id;}`.
`TrainingPlanRequest{string name; string description;}` (update body — no `trainer_id`, plans
aren't re-parented; see §3). `ListTrainingPlans` is scoped by `trainer_id`, matching the plan's
"list RPCs are parent-scoped" decision.

## 3. Business logic

`TrainingPlanService` (currently an empty stub — filled in here) gains `TrainerRepository` as a
dependency purely to validate the referenced trainer exists on create
(`trainerRepository.findById(trainerId).orElseThrow(() -> new ResourceNotFoundException("Trainer",
trainerId))`) — same pre-check-before-save discipline `TrainerService` already uses for email/CPF
uniqueness. `TrainingPlanMapper.toEntity` ignores `trainer` (can't map an id straight onto a
relation); the service sets `trainingPlan.setTrainer(trainer)` explicitly using the entity it just
fetched for the existence check — one query, not two. `toResponse` maps `trainer_id` via
`@Mapping(target = "trainerId", source = "trainer.id")`.

`Update` never changes `trainer` — same "no re-parenting via update" rule as the rest of the plan.
`Delete` cascades to `Workout`/`WorkoutExercise`/`ExerciseSet` for free (JPA `cascade = ALL,
orphanRemoval = true` already configured on `TrainingPlan.workouts`).

`TrainingPlan.description` is nullable, same as `Exercise.description` — reuses
`grpc-exercise-catalog`'s `ProtoStrings#nullToEmpty` fix (`@Mapper(uses = ProtoStrings.class)` +
`@Mapping(target = "description", qualifiedByName = "nullToEmpty")`) rather than rediscovering the
same protobuf-rejects-null-strings problem.

New repository method: `TrainingPlanRepository.findByTrainerId(Long trainerId)`.

## 4. Controller

`TrainingPlanController` (`@GrpcService`, `extends
TrainingPlanServiceGrpc.TrainingPlanServiceImplBase`) replaces the current empty
`@RestController @RequestMapping("/api/plans")` stub file entirely.

Validation: `name` `@NotBlank` (create + update); `trainer_id` unvalidated by Bean Validation —
`0`/nonexistent ids are naturally rejected as `NOT_FOUND` by the existence check in §3, same
"let the FK check do the work" choice already made implicitly for every other id field in this
codebase (`GetTrainerRequest.id` etc. never got `@Positive` either).

## 5. Dead code removed

`TrainingPlanRequest`/`TrainingPlanResponse` (`com.vertice.api.plan.dto`) — unused REST DTOs,
confirmed no controller/service ever referenced them.

## 6. Out of scope

- `Workout`/`WorkoutExercise`/`ExerciseSet` RPCs — later PRs.
