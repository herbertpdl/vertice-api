# Spec: WorkoutExercise gRPC CRUD

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement — assigning exercises into a workout is a
prerequisite for "set count / strategy / rest time" management, finished by the follow-up
`exercise-set-crud` spec), `docs/specs/workout-exercise-sets/spec.md` (the entity/migration layer
this is the first gRPC surface for), `docs/specs/grpc-workout/spec.md` (the CRUD pattern this
mirrors)

## 0. Scope decisions

`WorkoutExercise`/`ExerciseSet` have existed as entities + repositories only since
`workout-exercise-sets` — no gRPC surface. This spec is the first of two PRs closing that gap
(this one: placing an `Exercise` into a `Workout`; the next, `exercise-set-crud`: the sets
themselves). Following this repo's "document the default, cheap to revisit" approach:

- **`workout_id`/`exercise_id` immutable after creation.** Same choice already made for
  `Workout.trainingPlan` (not in `WorkoutRequest`) — moving a `WorkoutExercise` to a different
  workout, or swapping which catalog `Exercise` it points to, isn't a described use case;
  delete-and-recreate covers it. Update only touches `order`/`rest_seconds_between_sets`/`notes`.
- **No uniqueness on `order` within a workout.** requirements.md doesn't ask for it; two
  `WorkoutExercise` rows with the same `order` is accepted as a gap (matches
  `workout-day-of-week/spec.md` §0's identical call on weekday uniqueness).
- **`rest_seconds_between_sets` has no unset/explicit-zero distinction.** It's proto3 `int32`,
  whose zero value is indistinguishable from "the caller explicitly set 0 seconds rest" — same
  ambiguity `trainer-cref/spec.md` §5 already accepted for a different type (proto3 `string`).
  There's no existing "optional scalar" precedent in this codebase to reach for instead
  (`ProtoStrings#nullToEmpty` solves the string case only), so it's treated as a plain required
  int, defaulting to `0` when omitted — reasonable since `0` ("no configured rest gap between
  sets") and "not set" mean effectively the same thing operationally.

## 1. Goal

First gRPC surface for `WorkoutExercise`: place a catalog `Exercise` into a `Workout` with an
order, an optional rest time between its sets, and optional notes.

## 2. Contract

New file `src/main/proto/vertice/plan/v1/workout_exercise.proto`, same package/`java_package` as
`workout.proto`/`training_plan.proto`.

| RPC | Request | Response |
|---|---|---|
| `ListWorkoutExercises` | `{workout_id}` | `repeated WorkoutExerciseResponse` |
| `GetWorkoutExercise` | `{id}` | `WorkoutExerciseResponse` |
| `CreateWorkoutExercise` | `WorkoutExerciseCreateRequest` | `WorkoutExerciseResponse` |
| `UpdateWorkoutExercise` | `{id, WorkoutExerciseRequest}` | `WorkoutExerciseResponse` |
| `DeleteWorkoutExercise` | `{id}` | `Empty` |

`WorkoutExerciseResponse`: `id`, `workout_id`, `exercise_id`, `order`, `rest_seconds_between_sets`,
`notes`. `WorkoutExerciseCreateRequest`: `workout_id`, `exercise_id`, `order`,
`rest_seconds_between_sets`, `notes`. `WorkoutExerciseRequest` (update body): `order`,
`rest_seconds_between_sets`, `notes` — no `workout_id`/`exercise_id` (see §0).

## 3. Validation rules

- `order`: `@Min(1)` (a 1-based position, matches the `exercise_order` column's own implicit
  meaning).
- `rest_seconds_between_sets`: `@Min(0)` (no negative rest time).
- `workout_id`/`exercise_id` on create: must each resolve or `NOT_FOUND` (`ResourceNotFoundException`
  with the respective entity name), same pattern `WorkoutService#createWorkout` already uses for
  `training_plan_id`.
- `notes`: no constraint (optional, matches `Workout.notes`... actually `WorkoutExercise.notes`
  nullable `String`, `ProtoStrings#nullToEmpty` on the response side).

## 4. Mapping

`WorkoutExerciseMapper`, same shape as `WorkoutMapper`: `toEntity`/`updateEntityFromRequest`
ignore `id`/`workout`/`exercise`/`exerciseSets`; `toResponse` maps `workoutId`/`exerciseId` via
`workout.id`/`exercise.id` source paths (mirrors `trainingPlanId source = "trainer.id"`-style
mappings already used everywhere else) and `notes` via `nullToEmpty`.

## 5. Out of scope

- `ExerciseSet` CRUD (`exercise-set-crud`, the direct follow-up).
- Uniqueness/reordering helpers for `order` (see §0).
- `docs/domain-model.md`'s "Current status" note (still says "entity/migration layer only, no
  gRPC") gets updated as part of this PR since it stops being fully true here.
