# Spec: Workout gRPC CRUD

Status: Implemented — current; Workout is unrelated to the Trainer/Student → User rework.
Owner: hebertpdl@gmail.com
Related: `docs/specs/grpc-training-plan/spec.md` (pattern + parent), `docs/domain-model.md`

## 1. Goal

Third PR in the plan/workout CRUD series. `Workout` CRUD, scoped to its parent `TrainingPlan`
(from PR 2). Same shape as `TrainingPlan`'s own CRUD, one level down the hierarchy.

## 2. Contract: `workout.proto`

`src/main/proto/vertice/plan/v1/workout.proto` — same proto package (`vertice.plan.v1`) and Java
package as `training_plan.proto`.

| RPC | Request | Response |
|---|---|---|
| `ListWorkouts` | `ListWorkoutsRequest{training_plan_id}` | `ListWorkoutsResponse` (`repeated WorkoutResponse`) |
| `GetWorkout` | `GetWorkoutRequest{id}` | `WorkoutResponse` |
| `CreateWorkout` | `WorkoutCreateRequest{name,training_plan_id}` | `WorkoutResponse` |
| `UpdateWorkout` | `UpdateWorkoutRequest{id, WorkoutRequest workout}` | `WorkoutResponse` |
| `DeleteWorkout` | `DeleteWorkoutRequest{id}` | `google.protobuf.Empty` |

`WorkoutResponse{int64 id; string name; int64 training_plan_id;}`. `WorkoutRequest{string name;}`
(update body — no `training_plan_id`, not re-parentable, same rule as `TrainingPlanRequest`). No
`description` field on `Workout` (per `workout-exercise-sets`'s entity design — just `name`), so
no nullable-string mapping wrinkle here.

## 3. Business logic

`WorkoutService` (new class, mirrors `TrainingPlanService`): injects `TrainingPlanRepository` to
validate the parent plan exists on create (`NOT_FOUND` otherwise). `WorkoutMapper.toEntity`
ignores `trainingPlan`; service sets it explicitly from the already-fetched entity.
`toResponse` maps `training_plan_id` via `@Mapping(target = "trainingPlanId", source =
"trainingPlan.id")`. `Delete` cascades to `WorkoutExercise`/`ExerciseSet` (existing JPA cascade).

New repository method: `WorkoutRepository.findByTrainingPlanId(Long trainingPlanId)`.

## 4. Controller

`WorkoutController` (`@GrpcService`, `extends WorkoutServiceGrpc.WorkoutServiceImplBase`) — new
file, no REST equivalent ever existed. Validation: `name` `@NotBlank`.

## 5. Out of scope

- `WorkoutExercise`/`ExerciseSet` RPCs — later PRs.
