# Validation checklist: Workout feedback

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Data model (section 2)

- [x] `V19` migration creates `workout_feedback`, new table, no backfill — verified live by
  `./gradlew test` running the real migration
- [x] `WorkoutFeedbackRepository.findByWorkoutLog_Workout_TrainingPlan_TrainerId` — nested
  derived query resolves through 3 associations, exercised by
  `WorkoutFeedbackServiceTest#listWorkoutFeedback_returnsFeedbackForTrainer` (real repository
  method name compiles and Spring Data parses it — confirmed by the app context loading
  successfully in the controller test, which would fail fast on an invalid derived query)

## Contract (section 3)

- [x] `workout_feedback.proto` declares both RPCs, `package vertice.session.v1`
- [x] `WorkoutFeedbackResponse` embeds `workout_id`/`training_plan_id`/`client_id`, resolved via
  the mapper's nested source paths, not stored columns — code read +
  `WorkoutFeedbackServiceTest#submitWorkoutFeedback_savesAndResolvesLinkedIds` and
  `#listWorkoutFeedback_returnsFeedbackForTrainer` both assert all three resolve correctly

## Validation rules (section 4)

- [x] Blank `text` → `INVALID_ARGUMENT` —
  `WorkoutFeedbackControllerTest#submitWorkoutFeedback_withBlankText_throwsInvalidArgument`
- [x] `workout_log_id` unset (0) → `INVALID_ARGUMENT` —
  `#submitWorkoutFeedback_withZeroWorkoutLogId_throwsInvalidArgument`
- [x] Missing `workout_log_id` → `NOT_FOUND` —
  `WorkoutFeedbackServiceTest#submitWorkoutFeedback_throwsWhenWorkoutLogMissing`
- [x] Submitting against a non-completed `WorkoutLog` → rejected —
  `WorkoutFeedbackServiceTest#submitWorkoutFeedback_throwsWhenWorkoutLogNotCompleted`,
  `WorkoutFeedbackControllerTest#submitWorkoutFeedback_whenSessionNotCompleted_throwsInvalidArgument`
- [x] Unknown `trainer_id` on list → empty list, not an error —
  `WorkoutFeedbackServiceTest#listWorkoutFeedback_returnsEmptyForUnknownTrainer`
- [x] `trainer_id` unset (0) → `INVALID_ARGUMENT` —
  `WorkoutFeedbackControllerTest#listWorkoutFeedback_withZeroTrainerId_throwsInvalidArgument`

## Verification

- [x] `./gradlew test` passes — 217/217 (205 before this PR + 12 new: 5 service + 7 controller)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
