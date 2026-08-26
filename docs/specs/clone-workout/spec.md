# Spec: Clone a Workout

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "Whenever a personal trainer need to
change/create a new workout for the client, they should be able to use a previous created one as
base and just edit it changing what they want, because when a training plan changes, it doesn't
mean it changes completely"), `docs/specs/workout-exercise-crud/spec.md`,
`docs/specs/exercise-set-crud/spec.md` (the two entity trees this deep-copies)

## 0. Scope decisions

- **`CloneWorkout` takes the same required fields `CreateWorkout` does** (`name`, `day_of_week`,
  plus `source_workout_id` in place of nothing and `target_training_plan_id` in place of
  `training_plan_id`) rather than inheriting name/day from the source and leaving them to a
  follow-up `UpdateWorkout` call. Two clones of the same source workout would otherwise default to
  identical names, and the caller already has to make a choice for the target plan — making it
  explicit here is one RPC call instead of two (clone, then rename) for the common case.
- **Cloning into the same plan is allowed.** requirements.md's own framing ("a training plan
  changes, it doesn't mean it changes completely") is exactly the same-plan case — e.g. week 2 of
  a plan starts as a copy of week 1 with a few exercises swapped. No special-casing needed either
  way; `target_training_plan_id` is just another training plan id to validate.
- **Deep copy, not a shared reference.** The clone gets entirely new `WorkoutExercise`/
  `ExerciseSet` rows (new ids) pointing at the same catalog `Exercise` rows the source used —
  editing the clone (or the source) afterward never affects the other. This is what makes it
  useful as "a base to edit," per the requirement.

## 1. Goal

New `CloneWorkout` RPC on `WorkoutService`: deep-copies a source `Workout`'s full
`WorkoutExercise`/`ExerciseSet` tree into a brand-new `Workout` under a target `TrainingPlan`.

## 2. Contract (`workout.proto`)

```proto
rpc CloneWorkout(CloneWorkoutRequest) returns (WorkoutResponse);

message CloneWorkoutRequest {
  int64 source_workout_id = 1;
  int64 target_training_plan_id = 2;
  string name = 3;
  DayOfWeek day_of_week = 4;
}
```

Reuses `WorkoutResponse` — a clone is a normal `Workout` from that point on, discoverable via the
existing `ListWorkouts(target_training_plan_id)`.

## 3. Validation rules

- `name`: `@NotBlank`, same as `CreateWorkout`.
- `day_of_week`: `DAY_OF_WEEK_UNSPECIFIED` rejected, same `requireDayOfWeek` check
  `CreateWorkout`/`UpdateWorkout` already use.
- `source_workout_id`: must resolve or `NOT_FOUND` ("Workout").
- `target_training_plan_id`: must resolve or `NOT_FOUND` ("TrainingPlan").

## 4. Behavior

`WorkoutService#cloneWorkout` builds the new `Workout` and its full child tree in memory (copying
every scalar field of each `WorkoutExercise`/`ExerciseSet`, re-pointing each `WorkoutExercise` at
the *same* catalog `Exercise` row the source used), then saves the new `Workout` once — JPA's
existing `cascade = CascadeType.ALL` on both `Workout.workoutExercises` and
`WorkoutExercise.exerciseSets` (already there for `workout-exercise-sets`) persists the whole tree
in one call, same as `TrainingPlanController`'s existing cascade-delete already relies on for the
reverse direction.

Cloning a source workout with zero `WorkoutExercise` rows is allowed — produces an empty copy,
same as `CreateWorkout` already allows an empty workout.

## 5. Out of scope

- Cloning at any other level (a whole `TrainingPlan`, or a single `WorkoutExercise` into another
  workout) — not asked for; `Workout` is the unit requirements.md describes ("use a previous
  created workout as base").
- Any diffing/"what changed from the source" tracking after the clone — once created, the clone
  is a fully independent `Workout` like any other.
