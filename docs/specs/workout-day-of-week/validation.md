# Validation checklist: Workout day-of-week

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Data model (section 2)

- [x] `V16` migration is a plain `NOT NULL` add (confirmed zero pre-existing `workouts` rows in
  the local `vertice-postgres` container, no backfill needed) — verified by `./gradlew test`
  running the real migration against that DB
- [x] `Workout` entity gains `dayOfWeek` (`DayOfWeek` enum, `@Enumerated(EnumType.STRING)`) —
  code read

## Contract (section 3)

- [x] `DayOfWeek` proto enum added (`DAY_OF_WEEK_UNSPECIFIED` + 7 days)
- [x] `WorkoutResponse`/`WorkoutRequest`/`WorkoutCreateRequest` all gain `day_of_week`

## Validation rules (section 4)

- [x] `DAY_OF_WEEK_UNSPECIFIED` on create → `INVALID_ARGUMENT` —
  `WorkoutControllerTest#createWorkout_withUnsetDayOfWeek_throwsInvalidArgument`
- [x] `DAY_OF_WEEK_UNSPECIFIED` on update → `INVALID_ARGUMENT` —
  `#updateWorkout_withUnsetDayOfWeek_throwsInvalidArgument`
- [x] Valid day round-trips through create/update/list —
  `WorkoutServiceTest#createWorkout_setsTrainingPlanAndSaves`,
  `#updateWorkout_updatesNameAndDayOfWeek`, `#listWorkouts_returnsWorkoutsForTrainingPlan`

## Mapping (section 5)

- [x] `WorkoutMapper#mapDayOfWeek` uses `@ValueMapping` for the unmapped
  `DAY_OF_WEEK_UNSPECIFIED`/`UNRECOGNIZED` source constants, mirrors `TrainingPlanMapper#mapLevel`
  — code read

## Verification

- [x] `./gradlew test` passes — 118/118 (116 before this PR + 2 new: 1 controller unset-day case
  on create, 1 on update; existing tests updated in place to carry `day_of_week` rather than added
  as separate new cases, since it's now a required field on every existing scenario)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
