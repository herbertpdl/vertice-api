# Validation checklist: Workout / WorkoutExercise / ExerciseSet entities

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, code read) — not assumed.

## Conventions (section 1)

- [ ] All new entities use `IDENTITY` id generation
- [ ] No auditing fields added (matches the rest of the codebase)
- [ ] No Bean Validation annotations on entities (matches `Exercise`/`TrainingPlan`)
- [ ] Every new bidirectional relationship has `@ToString.Exclude` +
  `@EqualsAndHashCode.Exclude` on the child/"many" side

## Exercise refactor (section 3) / TrainingPlan (section 4)

- [ ] `Exercise` no longer has `sets`, `reps`, `trainingPlan`
- [ ] `ExerciseRequest`/`ExerciseResponse` updated to match
- [ ] `TrainingPlanResponse` no longer references `exercises`
- [ ] `TrainingPlan.exercises` replaced by `TrainingPlan.workouts`

## Entities (sections 5–8)

- [ ] `Workout`, `WorkoutExercise`, `ExerciseSet`, `SetStrategy` match the field tables exactly
- [ ] `WorkoutExercise.order` maps to DB column `exercise_order`
- [ ] `weight`/`loadPercentage` are `BigDecimal`, both nullable
- [ ] `SetStrategy` has exactly the 9 listed values, no `SUPERSET`

## Migrations (section 9)

- [ ] `V9`–`V12` exist, app boots cleanly against a fresh DB with `ddl-auto=validate`
- [ ] `V12` drops `sets`, `reps`, `training_plan_id`, and `fk_exercises_training_plan` from
  `exercises`

## Verification

- [ ] `./gradlew build` succeeds (compiles, `ddl-auto=validate` passes against a real Postgres)
- [ ] `./gradlew test` passes, existing suite unaffected
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
