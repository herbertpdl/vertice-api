# Spec: Exercise progress graph data

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "On the workout the costumer should be able
also to see an option that if they click, show graph with the progress of the weight during the
weeks"), `docs/specs/workout-session-logging/spec.md` (the `SetLog` history this aggregates)

## 0. Scope decisions

Per the plan-mode decision this closes out
(`/Users/herbertlago/.claude/plans/cached-hatching-dove.md` §Key scope decisions #4):

- **Data endpoint, not a rendered image.** This is a headless gRPC API with no UI anywhere in
  this repo — "show graph" means returning the time-series data a client app charts itself, same
  as every other read RPC in this codebase returns data for the caller to render.
- **One point per ISO week, `MAX(weight)` that week.** A client might do the same catalog
  `Exercise` in more than one `Workout` within a week (no constraint prevents that — see
  `workout-day-of-week/spec.md` §0), and log several sets at different weights in one session.
  The natural "progress" signal for strength work is the heaviest weight moved that week, not an
  average (which would understate real progress by blending in warm-up-weight sets) or "the last
  one logged" (arbitrary, order-dependent). Documented as the chosen default, cheap to revisit if
  a different aggregation is wanted.
- **Only `SetLog`s from *completed* sessions count.** An in-progress session's numbers aren't
  final yet — same reasoning `workout-feedback/spec.md` already applies to feedback submission.
- **`SetLog`s with no `weight` recorded are skipped**, not treated as zero — a duration-only
  (isometric) set has nothing to contribute to a weight-progress graph.

## 1. Goal

New `GetExerciseProgress(client_id, exercise_id)` RPC returning an ordered (oldest → newest)
series of `{week_start_date, weight}` points, one per week that had at least one qualifying
`SetLog`.

## 2. Data model

No schema change. New `SetLogRepository` query joining `SetLog` → `ExerciseSet` →
`WorkoutExercise` → `Exercise` (filtered by `exercise_id`) and `SetLog` → `WorkoutLog` (filtered
by `client_id`, `completed_at IS NOT NULL`), ordered by `WorkoutLog.weekStartDate`:

```java
@Query("SELECT sl FROM SetLog sl "
     + "WHERE sl.exerciseSet.workoutExercise.exercise.id = :exerciseId "
     + "AND sl.workoutLog.client.id = :clientId "
     + "AND sl.workoutLog.completedAt IS NOT NULL "
     + "ORDER BY sl.workoutLog.weekStartDate ASC")
List<SetLog> findCompletedSetLogsForClientAndExercise(Long clientId, Long exerciseId);
```

A hand-written JPQL `@Query`, not a derived method name — four chained association predicates is
past the point a Spring Data method name stays readable (every other repository method in this
codebase is a 1–3-association derived query; this is the first `@Query`).

## 3. Contract

Extends `workout_session.proto` (`vertice.session.v1`):

```proto
rpc GetExerciseProgress(GetExerciseProgressRequest) returns (GetExerciseProgressResponse);

message GetExerciseProgressRequest {
  int64 client_id = 1;
  int64 exercise_id = 2;
}

message ProgressPoint {
  string week_start_date = 1;
  string weight = 2;
}

message GetExerciseProgressResponse {
  repeated ProgressPoint points = 1;
}
```

## 4. Validation rules

`client_id`, `exercise_id`: `@Min(1)`. Neither is required to resolve to a real row — an unknown
id (like every other list-style query in this plan, e.g. `workout-feedback/spec.md` §4's
`trainer_id`) just yields an empty `points` list, not `NOT_FOUND`.

## 5. Behavior

`WorkoutSessionService#getExerciseProgress`: runs the query above, groups by
`WorkoutLog.weekStartDate` into a `TreeMap<LocalDate, BigDecimal>` merging with `BigDecimal::max`
(sorted-by-key iteration gives the oldest-to-newest ordering directly, independent of the query's
own row order), skipping any `SetLog` with a `null` weight, then maps each entry to a
`ProgressPoint`.

## 6. Out of scope

- Any other metric (volume, estimated 1RM, reps-over-time) — requirements.md asks specifically
  for "the progress of the weight during the weeks."
- Caching/pre-aggregation — this is a read computed on demand, consistent with every other list
  RPC in this codebase.
