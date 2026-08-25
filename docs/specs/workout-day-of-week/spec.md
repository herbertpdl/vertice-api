# Spec: Workout day-of-week

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "Create a workut that is a training for a
given weekday"), `docs/specs/grpc-workout/spec.md` (the CRUD this extends)

## 0. Scope decisions

- **Required, not nullable.** requirements.md defines a workout *as* "a training for a given
  weekday" — there's no described notion of a workout without one. Same required-enum treatment
  `PlanLevel` just got in `training-plan-fields/spec.md`.
- **One `Workout` per `(training_plan, day_of_week)` is *not* enforced.** requirements.md doesn't
  say a plan can only have one workout per weekday (a plan could reasonably want two different
  "Monday" workouts across different phases, or a rest-day placeholder) — no uniqueness
  constraint added. Documented here as an accepted gap, cheap to add later if wrong.
- **No date/recurrence semantics here.** `day_of_week` is a template property only ("this workout
  happens on Mondays"), not a specific calendar date — the plan-mode decision in
  `/Users/herbertlago/.claude/plans/cached-hatching-dove.md` §2 covers how specific week
  instances are tracked separately, in the session-logging PR.

## 1. Goal

`Workout` gains a required `day_of_week` (`MONDAY`..`SUNDAY`).

## 2. Data model

`workouts` has zero rows in the local dev DB (confirmed live) — plain `NOT NULL` add, no
backfill needed, unlike `training-plan-fields`'s `V15`.

```sql
ALTER TABLE workouts ADD COLUMN day_of_week VARCHAR(10) NOT NULL;
```

Migration `V16`. New Java enum `com.vertice.api.plan.workout.DayOfWeek { MONDAY, TUESDAY,
WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }`, `@Enumerated(EnumType.STRING)` on `Workout`.

## 3. Contract (`workout.proto`)

```proto
enum DayOfWeek {
  DAY_OF_WEEK_UNSPECIFIED = 0;
  MONDAY = 1;
  TUESDAY = 2;
  WEDNESDAY = 3;
  THURSDAY = 4;
  FRIDAY = 5;
  SATURDAY = 6;
  SUNDAY = 7;
}
```

`WorkoutResponse`/`WorkoutRequest`/`WorkoutCreateRequest` each gain `DayOfWeek day_of_week`
(next unused field number).

## 4. Validation rules

`DAY_OF_WEEK_UNSPECIFIED` rejected in `WorkoutController`, same `requireX`-style direct check
`TrainingPlanController#requireLevel`/`UserController#requireRole` already use — proto3 enums
can't be `@NotNull` on a validation record.

## 5. Mapping

`WorkoutMapper` gains a `@ValueMapping`-annotated `mapDayOfWeek` method mirroring
`TrainingPlanMapper#mapLevel` exactly (unmapped `DAY_OF_WEEK_UNSPECIFIED`/`UNRECOGNIZED` → `null`,
unreachable in practice since the controller rejects unset before the mapper runs).

## 6. Out of scope

- Uniqueness/one-per-weekday enforcement (see §0).
- Any recurrence/calendar-date concept (see §0) — that's `workout-session-logging`.
