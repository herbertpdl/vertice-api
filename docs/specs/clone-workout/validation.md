# Validation checklist: Clone a Workout

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `CloneWorkout` RPC added to `WorkoutService`, `CloneWorkoutRequest` has all 4 fields
- [x] Reuses `WorkoutResponse` — code read

## Validation rules (section 3)

- [x] Blank `name` → `INVALID_ARGUMENT` —
  `WorkoutControllerTest#cloneWorkout_withBlankName_throwsInvalidArgument`
- [x] Unset `day_of_week` → `INVALID_ARGUMENT` —
  `#cloneWorkout_withUnsetDayOfWeek_throwsInvalidArgument`
- [x] Missing `source_workout_id` → `NOT_FOUND` —
  `WorkoutServiceTest#cloneWorkout_throwsWhenSourceMissing`
- [x] Missing `target_training_plan_id` → `NOT_FOUND` —
  `#cloneWorkout_throwsWhenTargetPlanMissing`

## Behavior (section 4)

- [x] Full `WorkoutExercise`/`ExerciseSet` tree deep-copied (new instances, not the same
  reference), scalar fields preserved, `Exercise` catalog reference preserved (same instance) —
  `WorkoutServiceTest#cloneWorkout_deepCopiesWorkoutExercisesAndSets` (asserts
  `isNotSameAs`/`isSameAs` on the captured saved entity graph, not just the response DTO)
- [x] Cloning a workout with zero exercises produces an empty copy, no error —
  `WorkoutServiceTest#cloneWorkout_allowsCloningWorkoutWithNoExercises`
- [x] Cloning into the same plan works (no special-casing) — same test uses source plan id as
  target

## Verification

- [x] `./gradlew test` passes — 176/176 (167 before this PR + 9 new: 5 service + 4 controller)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
