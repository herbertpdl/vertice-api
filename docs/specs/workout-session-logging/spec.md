# Spec: Workout session logging (WorkoutLog / SetLog)

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirements — client marking a workout completed,
recording per-set weight recalled next time, seeing which workouts are done this week),
`docs/specs/exercise-set-crud/spec.md` (the trainer-authored plan `SetLog` records *actual*
performance against)

## 0. Scope decisions

First entirely client-facing surface in this codebase. Key modeling decisions, per the plan-mode
decision this spec implements (`/Users/herbertlago/.claude/plans/cached-hatching-dove.md` §Key
scope decisions #1–#3):

- **`WorkoutLog` = one client's attempt at one `Workout` in one ISO week.** `Workout` stays a
  template (day-of-week only, no date — `workout-day-of-week/spec.md`); `WorkoutLog` is the
  per-week instance, keyed by `(workout_id, client_id, week_start_date)` where `week_start_date`
  is the Monday of that week, `UNIQUE` — one log per workout/client/week. This is what "which
  workout is already done for that week" queries against.
- **`GetOrStartWorkoutLog` is idempotent get-or-create**, not a separate "start" vs. "get"
  operation — a client opening a workout they already started this week should see their
  in-progress state, not a conflict or a duplicate.
- **`CompleteWorkoutLog` is idempotent** — completing an already-completed log is a no-op returning
  the existing (already-completed) log, not an error. Matches the "recommended default, cheap to
  revisit" treatment `training-plan-fields/spec.md` gave an analogous idempotency question.
- **`RecordSetLog` upserts** — one `SetLog` per `(workout_log_id, exercise_set_id)`, `UNIQUE`;
  recording the same set again in the same session overwrites the previous value rather than
  accumulating rows. Rejected once the parent `WorkoutLog` is completed — a closed session's
  numbers are final.
- **No caller-identity/role enforcement** — same accepted gap as every other spec in this plan
  (`training-plan-fields/spec.md` §0): `client_id` is a caller-supplied request field, not derived
  from a JWT.
- **`reps` follows the same "0 doubles as omitted" treatment `ExerciseSet.reps` already has**
  (`exercise-set-crud/spec.md` §0) — no special-casing beyond what `SetStrategy`... n/a here,
  simply: no manual zero-to-null conversion, `reps` is copied through as-is like every other
  `int32` in this codebase.
- **Timestamps (`started_at`/`completed_at`/`recorded_at`) are server-set, response-only.** Never
  accepted as request input — same "server owns time" reasoning `password-storage`/`cpf-field`
  already apply to server-owned state. Cross the wire as ISO-8601 instant strings via a new
  `ProtoInstants` util (write-direction only; nothing parses an instant back from a request).

## 1. Goal

New `com.vertice.api.plan.session` package: `WorkoutLog` and `SetLog` entities, plus a
`WorkoutSessionService`/`WorkoutSessionController` gRPC surface covering: start-or-resume a
week's session, record a set's actual weight/reps, mark the session complete, list a client's
sessions for a plan/week (drives "what's done this week"), and fetch the most recent completed
session's set values for a workout (drives "remembered next time").

## 2. Data model

New tables, migration `V18` (both zero rows anywhere, no backfill concerns):

```sql
CREATE TABLE workout_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_id       BIGINT NOT NULL,
    client_id        BIGINT NOT NULL,
    week_start_date  DATE NOT NULL,
    started_at       TIMESTAMP NOT NULL,
    completed_at     TIMESTAMP,
    CONSTRAINT fk_workout_logs_workout FOREIGN KEY (workout_id) REFERENCES workouts (id),
    CONSTRAINT fk_workout_logs_client FOREIGN KEY (client_id) REFERENCES users (id),
    CONSTRAINT uq_workout_logs_workout_client_week UNIQUE (workout_id, client_id, week_start_date)
);

CREATE TABLE set_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_log_id   BIGINT NOT NULL,
    exercise_set_id  BIGINT NOT NULL,
    weight           NUMERIC(6, 2),
    reps             INTEGER,
    recorded_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_set_logs_workout_log FOREIGN KEY (workout_log_id) REFERENCES workout_logs (id),
    CONSTRAINT fk_set_logs_exercise_set FOREIGN KEY (exercise_set_id) REFERENCES exercise_sets (id),
    CONSTRAINT uq_set_logs_workout_log_exercise_set UNIQUE (workout_log_id, exercise_set_id)
);
```

`WorkoutLog`: `workout` (`@ManyToOne` → `Workout`), `client` (`@ManyToOne` → `User`),
`weekStartDate` (`LocalDate`), `startedAt`/`completedAt` (`Instant`, latter nullable).
`SetLog`: `workoutLog` (`@ManyToOne`), `exerciseSet` (`@ManyToOne` → `ExerciseSet`), `weight`
(`BigDecimal`, nullable), `reps` (`Integer`, nullable), `recordedAt` (`Instant`).

`WorkoutLogRepository`: `findByWorkoutIdAndClientIdAndWeekStartDate`,
`findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDate` (nested-association derived query,
same mechanism already relied on implicitly elsewhere via JPA relationship navigation),
`findFirstByClientIdAndWorkoutIdAndCompletedAtIsNotNullOrderByCompletedAtDesc`.
`SetLogRepository`: `findByWorkoutLogId`, `findByWorkoutLogIdAndExerciseSetId`.

## 3. Contract

New file `src/main/proto/vertice/session/v1/workout_session.proto`, `package vertice.session.v1`,
`option java_package = "com.vertice.api.generated.grpc.session.v1"`.

| RPC | Request | Response |
|---|---|---|
| `GetOrStartWorkoutLog` | `{workout_id, client_id, week_start_date}` | `WorkoutLogResponse` |
| `RecordSetLog` | `{workout_log_id, exercise_set_id, weight, reps}` | `SetLogResponse` |
| `CompleteWorkoutLog` | `{id}` | `WorkoutLogResponse` |
| `ListWorkoutLogs` | `{client_id, training_plan_id, week_start_date}` | `repeated WorkoutLogResponse` |
| `GetLastSetLogs` | `{client_id, workout_id}` | `repeated SetLogResponse` |

`WorkoutLogResponse`: `id`, `workout_id`, `client_id`, `week_start_date`, `started_at`,
`completed_at` (empty string if not yet completed). `SetLogResponse`: `id`, `workout_log_id`,
`exercise_set_id`, `weight` (string, blank if unset), `reps`, `recorded_at`.

## 4. Validation rules

- `workout_id`, `client_id`, `workout_log_id`, `exercise_set_id`, `training_plan_id`: `@Min(1)`
  where present on a request.
- `client_id` on `GetOrStartWorkoutLog`: must resolve to a `CLIENT`-role `User`, same pattern
  `TrainingPlanService` already uses.
- `week_start_date` (on `GetOrStartWorkoutLog` and `ListWorkoutLogs`): `@NotBlank`, parsed via
  `ProtoDates#stringToDate`, and must land on a Monday (`ConstraintViolationException` otherwise)
  — enforced by a shared private helper in `WorkoutSessionService`.
- `reps`: `@Min(0)`. `weight`: parsed via `ProtoDecimals#stringToDecimal` (rejects malformed/
  negative, blank → unset).
- `RecordSetLog` against a `WorkoutLog` whose `completedAt` is already set → rejected
  (`ConstraintViolationException`, "session already completed").

## 5. Out of scope

- `WorkoutFeedback` (the direct follow-up spec, `workout-feedback`).
- The progress-graph aggregation RPC (`exercise-progress`, the follow-up after that).
- Any authorization/ownership enforcement (see §0).
