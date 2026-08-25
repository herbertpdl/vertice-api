# Spec: ExerciseSet gRPC CRUD

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement — "Set how many sets for that execercise",
"Manage how the set strategy will be", "Set resting time between sets"),
`docs/specs/workout-exercise-crud/spec.md` (the direct predecessor — places the exercise into the
workout; this spec adds the sets to it), `docs/specs/workout-exercise-sets/spec.md` (the entity
layer this is the second and final gRPC surface for)

## 0. Scope decisions

Second and final PR closing the "entity/repository only" gap `workout-exercise-sets` left open.
Together with `workout-exercise-crud`, this fully satisfies requirements.md's trainer-side set
management. Following the same "document the default" approach:

- **`workout_exercise_id` immutable after creation** — same reasoning `workout-exercise-crud/spec.md`
  §0 already gave for `workout_id`/`exercise_id` there.
- **`weight`/`load_percentage` cross the wire as strings, not `int32`.** They're `NUMERIC(6,2)`/
  `NUMERIC(5,2)` columns — an `int32` would lose the fractional part, and a proto `double` risks
  float rounding on a value that's persisted as an exact decimal. Same "canonical form over the
  wire" choice `ProtoDates` already made for `LocalDate`; a new `ProtoDates`-shaped `ProtoDecimals`
  util handles the `BigDecimal` boundary. Unlike `rest_seconds_between_sets` in
  `workout-exercise-crud` (which had no way to distinguish "unset" from "explicitly 0"), a string
  *can* — empty string means unset, same as `ProtoStrings#nullToEmpty`'s reverse direction — so
  these two fields don't inherit that ambiguity.
- **`reps`/`duration_seconds`/`rest_seconds` stay plain `int32`, `@Min(0)` not `@Min(1)`.**
  Requirements.md and `workout-exercise-sets/spec.md` both allow a set to have only one of
  `reps`/`duration_seconds` set (an isometric hold has no rep count) — proto3 `int32`'s zero
  value has to double as "omitted" here (no string-based escape hatch makes sense for a count),
  so validation only blocks negative values, not zero. Documented as the same accepted ambiguity
  `workout-exercise-crud/spec.md` §0 already accepted for `rest_seconds_between_sets`.
- **`strategy` is a required proto enum** — same unset-rejected-explicitly pattern as
  `Role`/`PlanLevel`/`DayOfWeek`. Every set has to have a strategy (`STRAIGHT` covers "just a
  normal set" — there's no meaningful "no strategy" state, unlike the other optional fields here).

## 1. Goal

First (and closing) gRPC surface for `ExerciseSet`: define the actual sets — reps, duration,
weight, load %, strategy, rest time, notes — for a `WorkoutExercise`.

## 2. Contract

New file `src/main/proto/vertice/plan/v1/exercise_set.proto`, same package/`java_package` as the
other `plan/v1` proto files.

| RPC | Request | Response |
|---|---|---|
| `ListExerciseSets` | `{workout_exercise_id}` | `repeated ExerciseSetResponse` |
| `GetExerciseSet` | `{id}` | `ExerciseSetResponse` |
| `CreateExerciseSet` | `ExerciseSetCreateRequest` | `ExerciseSetResponse` |
| `UpdateExerciseSet` | `{id, ExerciseSetRequest}` | `ExerciseSetResponse` |
| `DeleteExerciseSet` | `{id}` | `Empty` |

`SetStrategy` proto enum mirrors the existing Java `com.vertice.api.plan.workout.SetStrategy`
exactly (9 values: `STRAIGHT`, `WARM_UP`, `BACKOFF`, `DROPSET`, `REST_PAUSE`, `CLUSTER`, `AMRAP`,
`ISOMETRIC_HOLD`, `FAILURE`), zero value `SET_STRATEGY_UNSPECIFIED`.

`ExerciseSetResponse`: `id`, `workout_exercise_id`, `set_number`, `reps`, `duration_seconds`,
`weight` (string), `load_percentage` (string), `strategy`, `rest_seconds`, `notes`.
`ExerciseSetCreateRequest`: same fields plus `workout_exercise_id` in place of `id`.
`ExerciseSetRequest` (update body): everything except `workout_exercise_id`.

## 3. Validation rules

- `set_number`: `@Min(1)` — a 1-based position, same reasoning `order` got in
  `workout-exercise-crud`.
- `reps`, `duration_seconds`, `rest_seconds`: `@Min(0)` (see §0 — zero doubles as "omitted").
- `weight`, `load_percentage`: parsed via `ProtoDecimals#stringToDecimal`, which itself rejects a
  malformed or negative value (`INVALID_ARGUMENT`); blank string → `null` (unset).
- `strategy`: `SET_STRATEGY_UNSPECIFIED` rejected the same `requireX`-style direct check
  `WorkoutController#requireDayOfWeek`/`TrainingPlanController#requireLevel` already use.
- `workout_exercise_id` on create: must resolve or `NOT_FOUND`.

## 4. Mapping

`ExerciseSetMapper`, same shape as `WorkoutExerciseMapper`: `toEntity`/`updateEntityFromRequest`
ignore `id`/`workoutExercise`; `toResponse` maps `workoutExerciseId` via `workoutExercise.id`;
`weight`/`loadPercentage` via `ProtoDecimals`' `@Named` methods; `notes` via
`ProtoStrings#nullToEmpty`; `strategy` via a `@ValueMapping`-annotated method mirroring
`WorkoutMapper#mapDayOfWeek` (proto's extra `SET_STRATEGY_UNSPECIFIED`/`UNRECOGNIZED` → `null`,
unreachable since the controller rejects unset first).

## 5. Out of scope

- Reordering/renumbering helpers for `set_number` (no uniqueness enforced, same as `order` in
  `workout-exercise-crud`).
- Actual performance tracking (what weight a client actually used on a given day) — that's
  `SetLog` in `workout-session-logging`, a distinct concept from this trainer-authored plan data.
