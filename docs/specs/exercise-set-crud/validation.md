# Validation checklist: ExerciseSet gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `exercise_set.proto` declares all 5 RPCs, `package vertice.plan.v1`
- [x] `SetStrategy` proto enum mirrors the 9 Java `SetStrategy` values exactly, zero value
  `SET_STRATEGY_UNSPECIFIED`
- [x] `ListExerciseSets` scoped by `workout_exercise_id`
- [x] `ExerciseSetRequest` (update body) excludes `workout_exercise_id` — code read

## Validation rules (section 3)

- [x] `set_number < 1` → `INVALID_ARGUMENT` —
  `ExerciseSetControllerTest#createExerciseSet_withZeroSetNumber_throwsInvalidArgument`
- [x] Negative `reps`/`duration_seconds`/`rest_seconds` → `INVALID_ARGUMENT` —
  `#createExerciseSet_withNegativeReps_throwsInvalidArgument`
- [x] Omitted `reps` allowed for a duration-only (isometric) set —
  `ExerciseSetServiceTest#createExerciseSet_allowsOmittedReps_forIsometricSets`
- [x] Negative `weight` → rejected — `ExerciseSetServiceTest#createExerciseSet_throwsWhenWeightIsNegative`
- [x] Malformed `weight` string → rejected —
  `ExerciseSetServiceTest#createExerciseSet_throwsWhenWeightIsMalformed`
- [x] Blank `weight`/`load_percentage` → `null` (unset), not an error —
  `ExerciseSetServiceTest#createExerciseSet_allowsOmittedReps_forIsometricSets` asserts empty
  string round-trip
- [x] `SET_STRATEGY_UNSPECIFIED` on create/update → `INVALID_ARGUMENT` —
  `ExerciseSetControllerTest#createExerciseSet_withUnsetStrategy_throwsInvalidArgument`,
  `#updateExerciseSet_withUnsetStrategy_throwsInvalidArgument`
- [x] Missing `workout_exercise_id` on create → `NOT_FOUND` —
  `ExerciseSetServiceTest#createExerciseSet_throwsWhenWorkoutExerciseMissing`

## Mapping (section 4)

- [x] `ProtoDecimals` (`stringToDecimal`/`decimalToString`) added, mirrors `ProtoDates` — code
  read; exercised live by the negative/malformed-weight service tests above (not mocked — real
  `Mappers.getMapper` instance)
- [x] `strategy` proto↔entity mapping uses `@ValueMapping`, mirrors `WorkoutMapper#mapDayOfWeek`
  — code read

## Out of scope (section 5)

- [x] `docs/domain-model.md`'s "Current status" note updated — all five plan/workout entities now
  have full CRUD

## Verification

- [x] `./gradlew test` passes — 162/162 (139 before this PR + 23 new: 9 service + 14 controller)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
