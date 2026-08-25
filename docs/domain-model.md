# Domain model: training plans, workouts, and exercises

Five entities are easy to mix up because the names are similar and they're deeply nested. This
doc is the map. Full field-level detail lives in `docs/specs/workout-exercise-sets/spec.md`; this
is the "what is this thing and why does it exist" reference.

## The hierarchy

```
TrainingPlan          "12-Week Strength Program" (belongs to a Trainer)
  └── Workout          "Week 1 – Day 1: Push"        (one training day)
        └── WorkoutExercise   "Bench Press, 1st, 90s rest"   (this exercise, in this workout)
              ├── ExerciseSet   Set 1: 15 reps @ 40kg, WARM_UP
              ├── ExerciseSet   Set 2: 12 reps @ 60kg, STRAIGHT
              └── ExerciseSet   Set 3: 10 reps @ 65kg, STRAIGHT
        └── WorkoutExercise   "Overhead Press, 2nd, 60s rest"
              └── ExerciseSet   ...
  └── Workout          "Week 1 – Day 2: Pull"
        └── WorkoutExercise   "Barbell Row, 1st, 90s rest"
              └── ...
```

And separately, **not nested under any of the above**:

```
Exercise catalog (shared across every trainer, every plan, every workout):
  "Bench Press", "Overhead Press", "Barbell Row", "Squat", ...
```

## Two axes: "when" vs. "what"

- **`TrainingPlan` → `Workout`** answers *when*: which program, which day.
- **`Exercise` → `WorkoutExercise` → `ExerciseSet`** answers *what*: which movement, how it's
  placed into a specific day, how it's actually performed.

`WorkoutExercise` is the one entity that touches both sides — it's the bridge between a `Workout`
(a day) and an `Exercise` (a catalog movement).

## What each entity is, and why it's a separate thing

| Entity | Package | What it represents | Why it's not merged into another entity |
|---|---|---|---|
| `TrainingPlan` | `com.vertice.api.plan` | A trainer's overall program for a client (e.g. a 12-week plan). | Owns the trainer relationship and the plan-level name/description; a plan has many workouts. |
| `Workout` | `com.vertice.api.plan.workout` | One specific training day inside a plan (e.g. "Day 1: Push"). | A plan isn't one flat list of exercises — it's organized into days, each with its own set of exercises. |
| `Exercise` | `com.vertice.api.plan.exercise` | The *definition* of a movement (e.g. "Bench Press") — name, description. | Reusable catalog entry, independent of any plan/workout. Without this split, every workout would duplicate the exercise's name/description as plain strings instead of referencing one canonical row — no easy rename/fix-a-typo, no "pick from a list" catalog UI, no querying "which workouts use Squat" via a real foreign key. |
| `WorkoutExercise` | `com.vertice.api.plan.workout` | "In *this* workout, do *this* catalog exercise, in *this* order, with *this* rest between sets." | The join between `Workout` and `Exercise` — carries workout-specific placement data (`order`, `restSecondsBetweenSets`, `notes`) that doesn't belong on either side alone. |
| `ExerciseSet` | `com.vertice.api.plan.workout` | One set actually performed for a `WorkoutExercise`: reps, weight, duration, strategy. | Sets vary independently per exercise-in-workout (1×15, 1×12, 2×10 — different reps *and* weight per set) — a single fixed `sets`/`reps` pair on `Exercise` (the old design) couldn't represent that; a `WorkoutExercise` needs a *list* of these. |

## Worked example, end to end

A trainer builds `TrainingPlan` "12-Week Strength Program" for a client. Its first `Workout` is
"Week 1 – Day 1: Push". In that workout, the trainer adds the catalog `Exercise` "Bench Press" as
a `WorkoutExercise` (1st in order, 90s rest between sets), then records three `ExerciseSet` rows
for it: a 15-rep warm-up at 40kg, then two straight sets at 12 and 10 reps with increasing weight.
The same "Bench Press" `Exercise` row is reused, unchanged, if this or any other trainer adds it
to a different workout in a different plan — only the `WorkoutExercise`/`ExerciseSet` rows differ
per placement.

## Current status

All five entities (`TrainingPlan`, `Workout`, `Exercise`, `WorkoutExercise`, `ExerciseSet`) have
full gRPC CRUD (`docs/specs/grpc-training-plan/spec.md`, `grpc-workout/spec.md`,
`grpc-exercise-catalog/spec.md`, `workout-exercise-crud/spec.md`, `exercise-set-crud/spec.md`).
