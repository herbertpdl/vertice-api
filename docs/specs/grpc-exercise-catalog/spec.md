# Spec: Exercise catalog gRPC CRUD

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/grpc-trainer/spec.md` (the pattern this mirrors),
`docs/specs/workout-exercise-sets/spec.md` (the entity this exposes), `docs/domain-model.md`

## 1. Goal

First real RPC surface for `Exercise` (the reusable catalog entity — `id`/`name`/`description`
only, per `workout-exercise-sets`). Flat CRUD, no parent — same shape as Trainer/Student, simplest
of the five entities in the plan, first to validate the `vertice.exercise.v1` proto
package/pattern.

## 2. Contract: `exercise.proto`

`src/main/proto/vertice/exercise/v1/exercise.proto`, `option java_package =
"com.vertice.api.generated.grpc.exercise.v1"`.

| RPC | Request | Response |
|---|---|---|
| `ListExercises` | `ListExercisesRequest` (empty) | `ListExercisesResponse` (`repeated ExerciseResponse`) |
| `GetExercise` | `GetExerciseRequest{id}` | `ExerciseResponse` |
| `CreateExercise` | `ExerciseRequest{name,description}` | `ExerciseResponse` |
| `UpdateExercise` | `UpdateExerciseRequest{id, ExerciseRequest exercise}` | `ExerciseResponse` |
| `DeleteExercise` | `DeleteExerciseRequest{id}` | `google.protobuf.Empty` |

`ExerciseRequest{string name; string description;}` doubles as both the create body and the
nested update body — unlike Trainer/Student, `Exercise` has no create-only field (no password
equivalent), so there's no need for a separate `ExerciseCreateRequest`.
`ExerciseResponse{int64 id; string name; string description;}`.

## 3. Business logic

New `com.vertice.api.plan.exercise.ExerciseService` (mirrors `TrainerService` shape):
`listExercises()`, `getExercise(id)`, `createExercise(request)`, `updateExercise(id, request)`,
`deleteExercise(id)`. No uniqueness/duplicate checks (`Exercise.name` has no unique constraint —
nothing in `workout-exercise-sets` asked for one, and it's plausible for a catalog to have
near-duplicate entries, e.g. "Barbell Squat" vs. "Back Squat"). `ExerciseMapper` (MapStruct):
`toEntity`, `toResponse`, `updateEntityFromRequest` — same trivial shape as `TrainerMapper`, no
custom field handling needed (no nullable-numeric or `BigDecimal` fields on `Exercise`).

## 4. Controller

`ExerciseController` (`@GrpcService`, `extends ExerciseServiceGrpc.ExerciseServiceImplBase`) —
new file; there is no REST version to replace (`Exercise` never had one).

Validation (`GrpcRequestValidator`, mirrors `TrainerController`'s private-record pattern):
`name` `@NotBlank`; `description` unconstrained (nullable, matches the entity column).

## 5. Dead code removed

`ExerciseRequest`/`ExerciseResponse` (`com.vertice.api.plan.exercise.dto`) — the REST-shaped DTOs
left over from before `Exercise` was refactored, confirmed unused anywhere (no controller/service
ever referenced them; `TrainingPlanController` is still an empty stub). Deleted, same as the
Trainer/Student REST DTOs were deleted when those moved to gRPC.

## 6. Out of scope

- `TrainingPlan`/`Workout`/`WorkoutExercise`/`ExerciseSet` RPCs — later PRs in the same plan.
- Any uniqueness constraint on `Exercise.name`.
