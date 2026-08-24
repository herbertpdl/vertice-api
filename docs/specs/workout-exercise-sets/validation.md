# Validation checklist: Workout / WorkoutExercise / ExerciseSet entities

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, code read) — not assumed.

## Conventions (section 1)

- [x] All new entities use `IDENTITY` id generation
- [x] No auditing fields added (matches the rest of the codebase)
- [x] No Bean Validation annotations on entities (matches `Exercise`/`TrainingPlan`)
- [x] Every new bidirectional relationship has `@ToString.Exclude` +
  `@EqualsAndHashCode.Exclude` on the child/"many" side — `Workout.trainingPlan`,
  `Workout.workoutExercises`, `WorkoutExercise.workout`, `WorkoutExercise.exercise`,
  `WorkoutExercise.exerciseSets`, `ExerciseSet.workoutExercise`, `TrainingPlan.workouts`

## Exercise refactor (section 3) / TrainingPlan (section 4)

- [x] `Exercise` no longer has `sets`, `reps`, `trainingPlan` — down to `id`/`name`/`description`
- [x] `ExerciseRequest`/`ExerciseResponse` updated to match
- [x] `TrainingPlanResponse` no longer references `exercises`
- [x] `TrainingPlan.exercises` replaced by `TrainingPlan.workouts`

## Entities (sections 5–8)

- [x] `Workout`, `WorkoutExercise`, `ExerciseSet`, `SetStrategy` match the field tables exactly
- [x] `WorkoutExercise.order` maps to DB column `exercise_order` — confirmed via `\d
  workout_exercises`
- [x] `weight`/`loadPercentage` are `BigDecimal`, both nullable — confirmed
  `numeric(6,2)`/`numeric(5,2)`, nullable, via `\d exercise_sets`
- [x] `SetStrategy` has exactly the 9 listed values, no `SUPERSET`

## Migrations (section 9)

- [x] `V9`–`V12` exist, app boots cleanly against the real local DB with `ddl-auto=validate`
- [x] `V12` drops `sets`, `reps`, `training_plan_id`, and `fk_exercises_training_plan` from
  `exercises` — confirmed via `\d exercises` (only `id`/`name`/`description` remain)

## Verification

- [x] `./gradlew build` succeeds
- [x] `./gradlew test` passes — 78/78, unaffected (no test previously touched `plan`/`exercise`)
- [x] Manual: inspected the real schema post-migration via `psql \d` for all four tables
  (`exercises`, `workouts`, `workout_exercises`, `exercise_sets`) — matches the spec's field
  tables and FK graph exactly
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
