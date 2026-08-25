# Validation checklist: WorkoutExercise gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `workout_exercise.proto` declares all 5 RPCs, `package vertice.plan.v1`
- [x] `ListWorkoutExercises` scoped by `workout_id`
- [x] `WorkoutExerciseRequest` (update body) excludes `workout_id`/`exercise_id` — code read

## Validation rules (section 3)

- [x] `order < 1` → `INVALID_ARGUMENT` —
  `WorkoutExerciseControllerTest#createWorkoutExercise_withZeroOrder_throwsInvalidArgument`,
  `#updateWorkoutExercise_withZeroOrder_throwsInvalidArgument`
- [x] `rest_seconds_between_sets < 0` → `INVALID_ARGUMENT` —
  `#createWorkoutExercise_withNegativeRestSeconds_throwsInvalidArgument`
- [x] Missing `workout_id` on create → `NOT_FOUND` —
  `WorkoutExerciseServiceTest#createWorkoutExercise_throwsWhenWorkoutMissing`
- [x] Missing `exercise_id` on create → `NOT_FOUND` —
  `#createWorkoutExercise_throwsWhenExerciseMissing`
- [x] Update only changes `order`/`rest_seconds_between_sets`/`notes`, `workout`/`exercise` stay
  fixed — `WorkoutExerciseServiceTest#updateWorkoutExercise_updatesOrderRestAndNotesOnly`
- [x] Nullable `notes` reuses `ProtoStrings#nullToEmpty` —
  `WorkoutExerciseServiceTest#listWorkoutExercises_returnsExercisesForWorkout` asserts empty, not
  crashing

## Mapping (section 4)

- [x] `WorkoutExerciseMapper` maps `workout_id`/`exercise_id` via `workout.id`/`exercise.id`
  source paths, mirrors `TrainingPlanMapper`/`WorkoutMapper` — code read

## Out of scope (section 5)

- [x] `docs/domain-model.md`'s "Current status" note updated to reflect this PR — code read

## Verification

- [x] `./gradlew test` passes — 139/139 (118 before this PR + 21 new: 8 service + 13 controller)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
