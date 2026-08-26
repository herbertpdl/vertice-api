# Spec: Workout / WorkoutExercise / ExerciseSet entities

Status: Implemented — current; Workout/WorkoutExercise/ExerciseSet are unrelated to the
Trainer/Student → User rework.
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md` (entity/repository conventions this follows)

## 0. Scope decision

Entity/migration layer only — no DTOs, services, controllers, or `.proto` for this spec.
`TrainingPlanController`/`TrainingPlanService` are still empty stubs with zero endpoints; nothing
in the app calls into `Exercise`/`TrainingPlan` yet, so there's no REST/gRPC surface to design
here. This is purely fixing the data model so a real feature can be built on top of it later.

The original ask was "make `Exercise` support variable sets per exercise (1x15, 1x12, 2x10)
instead of one fixed `sets`/`reps` pair" — the follow-up spec resolved that into a concrete shape:
`Exercise` becomes a reusable catalog entry (just `name`/`description`), and the per-workout,
per-set data (reps, weight, strategy, ...) moves to a new `ExerciseSet`, attached via a new
`WorkoutExercise` join entity. That join entity needs something to join *to* besides `Exercise` —
a specific training day — which didn't exist as an entity yet, so this spec adds `Workout` too
(`TrainingPlan` → `Workout` → `WorkoutExercise` → `ExerciseSet`, `WorkoutExercise` → `Exercise`).

## 1. Conventions confirmed before writing code (not assumed)

Checked `Trainer`, `Student`, `TrainingPlan`, `Exercise`:

- ID strategy: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` on every entity, no
  exceptions. Used here too.
- Auditing fields: **none exist anywhere in this codebase** — no `createdAt`/`updatedAt` on any
  entity. Not added here either, for consistency.
- Validation: entities carry only JPA-level `@Column(nullable = ...)` constraints; Jakarta Bean
  Validation annotations (`@NotBlank`, `@Positive`, etc.) only ever appear on request DTOs/records,
  never on `@Entity` classes. Since this spec adds no DTOs (see §0), no Bean Validation annotations
  are added to the new entities — DB-level `nullable` constraints only, matching how `Exercise`/
  `TrainingPlan` already work.
- Lombok: `@Data` on every entity. `Trainer`/`Student` already use `@ToString.Exclude` (for
  `passwordHash`, a secrets concern). `TrainingPlan`/`Exercise` don't use it at all, despite being
  a bidirectional `@OneToMany`/`@ManyToOne` pair — meaning `@Data`'s generated `toString()`/
  `equals()`/`hashCode()` on either side recurses into the other, an infinite loop
  (`StackOverflowError`) the first time either gets logged, compared, or asserted on. This spec
  doesn't touch `TrainingPlan`/`Exercise`'s existing pair (out of scope, not asked for), but every
  *new* bidirectional relationship added here gets `@ToString.Exclude` +
  `@EqualsAndHashCode.Exclude` on the "many"/child side to avoid replicating that bug four more
  times. Documented here since it's a deviation from the letter of "existing convention" in
  service of not shipping a known-broken pattern into new code.

## 2. Package layout

New package `com.vertice.api.plan.workout`, alongside the existing `com.vertice.api.plan`
(`TrainingPlan`) and `com.vertice.api.plan.exercise` (`Exercise`, now a pure catalog entity):

- `Workout` — a specific training day within a `TrainingPlan`.
- `WorkoutExercise` — join entity: which `Exercise`, in which `Workout`, in what order.
- `ExerciseSet` — one set within a `WorkoutExercise`.
- `SetStrategy` — enum classifying a set.
- One plain `JpaRepository<X, Long>` per new entity, matching `TrainingPlanRepository`/
  `ExerciseRepository`'s existing minimalism (no custom query methods added — none needed yet).

## 3. `Exercise` — refactored to a catalog entity

Drops `sets`, `reps`, and the direct `trainingPlan` `@ManyToOne` — those were the "fixed
sets/reps" problem from the original ask. `Exercise` keeps `id`, `name`, `description`; it's now
referenced *from* `WorkoutExercise` rather than owning a training-plan link itself, so the same
exercise definition (e.g. "Barbell Bench Press") can be reused across many workouts/plans instead
of being duplicated per plan.

`ExerciseRequest`/`ExerciseResponse` (`com.vertice.api.plan.exercise.dto`, unused anywhere in the
app — confirmed via repo-wide search) updated to match: drop `sets`/`reps`. `TrainingPlanResponse`
drops its `exercises` field (that relationship no longer exists — `TrainingPlan` now relates to
`Workout`, not `Exercise`, directly); no `WorkoutResponse` DTO added since nothing consumes it yet
(§0).

## 4. `TrainingPlan` — `exercises` replaced with `workouts`

`List<Exercise> exercises` (`mappedBy = "trainingPlan"`, cascade `ALL`, `orphanRemoval = true`)
becomes `List<Workout> workouts` with the same ownership semantics, now pointing at `Workout`
instead of `Exercise` directly (which no longer has a `trainingPlan` field to map by).

## 5. `Workout`

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `Long` | — | identity |
| `name` | `String` | no | e.g. "Day 1 — Push" |
| `trainingPlan` | `TrainingPlan` (`@ManyToOne`) | no | |
| `workoutExercises` | `List<WorkoutExercise>` | — | `mappedBy = "workout"`, cascade `ALL`, orphan removal |

## 6. `WorkoutExercise`

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `Long` | — | identity |
| `workout` | `Workout` (`@ManyToOne`) | no | |
| `exercise` | `Exercise` (`@ManyToOne`) | no | catalog reference, not owned/cascaded |
| `order` | `Integer` | no | position within the workout; DB column `exercise_order` — `order` is a reserved SQL keyword, `@Column(name = "exercise_order")` sidesteps quoting it everywhere |
| `restSecondsBetweenSets` | `Integer` | yes | |
| `notes` | `String` | yes | |
| `exerciseSets` | `List<ExerciseSet>` | — | `mappedBy = "workoutExercise"`, cascade `ALL`, orphan removal |

## 7. `ExerciseSet`

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `Long` | — | identity |
| `workoutExercise` | `WorkoutExercise` (`@ManyToOne`) | no | |
| `setNumber` | `Integer` | no | order within the `WorkoutExercise` |
| `reps` | `Integer` | yes | absent for AMRAP/ISOMETRIC_HOLD-style sets with no fixed target |
| `durationSeconds` | `Integer` | yes | for holds |
| `weight` | `BigDecimal(6,2)` | yes | absolute load; "and/or `loadPercentage`" per the ask — both nullable, no XOR enforced at the entity/DB level (that's a validation-layer decision for whenever a request DTO exists) |
| `loadPercentage` | `BigDecimal(5,2)` | yes | e.g. % of 1RM |
| `strategy` | `SetStrategy` (`@Enumerated(STRING)`) | no | every set gets an explicit classification |
| `restSeconds` | `Integer` | yes | override of `WorkoutExercise.restSecondsBetweenSets` |
| `notes` | `String` | yes | |

`BigDecimal` over `Double`/`Float` for `weight`/`loadPercentage` — standard practice for any
numeric value that gets displayed/compared precisely (same reasoning as using `BigDecimal` for
money); `Double` risks binary floating-point rounding surprises (e.g. `62.5` not round-tripping
exactly).

## 8. `SetStrategy`

```java
public enum SetStrategy {
    STRAIGHT, WARM_UP, BACKOFF, DROPSET, REST_PAUSE, CLUSTER, AMRAP, ISOMETRIC_HOLD, FAILURE
}
```

No `SUPERSET` — per the ask, that's a relationship between two `WorkoutExercise` rows (e.g. a
shared group/order marker), not a set-level attribute. Not modeled at all yet, deliberately.

## 9. Migrations

`V9__create_workouts_table.sql`, `V10__create_workout_exercises_table.sql`,
`V11__create_exercise_sets_table.sql`, `V12__alter_exercises_drop_sets_reps_and_plan_link.sql` —
the last one drops `exercises.sets`, `exercises.reps`, the `fk_exercises_training_plan` FK, and
`exercises.training_plan_id`. No data to migrate/backfill: `exercises`/`training_plans` have never
had a live consumer (no endpoints ever wrote to them), so there's nothing in either table in any
real environment.

## 10. Out of scope

- DTOs, services, controllers, `.proto` contracts for any of this — first real consumer designs
  those against actual requirements (e.g. how a trainer builds a workout in the BFF/web UI).
- `SUPERSET` / exercise-grouping relationships.
- Enforcing "at least one of `weight`/`loadPercentage`" or similar cross-field rules — no
  validation layer exists yet to put it in.
- Any change to `Trainer`/`Student`/existing `TrainingPlan`↔`Exercise` `@Data` cycle risk (see §1).
