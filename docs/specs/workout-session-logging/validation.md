# Validation checklist: Workout session logging (WorkoutLog / SetLog)

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Data model (section 2)

- [x] `V18` migration creates both tables with their unique constraints — zero pre-existing rows
  anywhere, verified live by `./gradlew test` running the real migration
- [x] `WorkoutLog`/`SetLog` entities and repositories match the spec exactly — code read

## Contract (section 3)

- [x] `workout_session.proto` declares all 5 RPCs, `package vertice.session.v1`
- [x] `completed_at`/`weight` are empty string when unset — `WorkoutSessionServiceTest`'s
  `getOrStartWorkoutLog_createsNewLogWhenNoneExists` asserts `completed_at` empty

## Validation rules (section 4)

- [x] Non-Monday `week_start_date` rejected on both `GetOrStartWorkoutLog` and `ListWorkoutLogs`
  — `WorkoutSessionServiceTest#getOrStartWorkoutLog_throwsWhenNotMonday`,
  `#listWorkoutLogs_throwsWhenWeekStartDateNotMonday`
- [x] Blank `week_start_date` → `INVALID_ARGUMENT` —
  `WorkoutSessionControllerTest#getOrStartWorkoutLog_withBlankWeekStartDate_throwsInvalidArgument`
- [x] `client_id` not resolving to `CLIENT` role → `NOT_FOUND` —
  `WorkoutSessionServiceTest#getOrStartWorkoutLog_throwsWhenClientIsNotClientRole`
- [x] Negative `reps` → `INVALID_ARGUMENT` —
  `WorkoutSessionControllerTest#recordSetLog_withNegativeReps_throwsInvalidArgument`
- [x] `RecordSetLog` against a completed `WorkoutLog` → rejected —
  `WorkoutSessionServiceTest#recordSetLog_throwsWhenWorkoutLogAlreadyCompleted`,
  `WorkoutSessionControllerTest#recordSetLog_whenSessionAlreadyCompleted_throwsInvalidArgument`
- [x] `@Min(1)` id fields rejected at zero —
  `WorkoutSessionControllerTest#getOrStartWorkoutLog_withZeroWorkoutId_throwsInvalidArgument`,
  `#listWorkoutLogs_withZeroClientId_throwsInvalidArgument`

## Behavior (section 0/3)

- [x] `GetOrStartWorkoutLog` idempotent get-or-create —
  `WorkoutSessionServiceTest#getOrStartWorkoutLog_returnsExistingLogIdempotently` (verifies no
  `Workout`/save lookups happen on the existing-log path)
- [x] `CompleteWorkoutLog` idempotent, preserves original `completedAt` —
  `WorkoutSessionServiceTest#completeWorkoutLog_isIdempotentWhenAlreadyCompleted` (asserts the
  *original* timestamp survives and `save` isn't called again)
- [x] `RecordSetLog` upserts in place (same row id) rather than creating a duplicate —
  `WorkoutSessionServiceTest#recordSetLog_upsertsExistingSetLogInPlace`
- [x] `GetLastSetLogs` returns empty (not an error) with no completed history —
  `WorkoutSessionServiceTest#getLastSetLogs_returnsEmptyWhenNoCompletedSession`,
  `WorkoutSessionControllerTest#getLastSetLogs_returnsEmptyWhenNoHistory`

## Test infra fix (unplanned, discovered during this PR)

- [x] 11 `@SpringBootTest` classes now exist, each opening its own Spring context with a default
  10-connection HikariCP pool — locally exceeded Postgres's `max_connections=100`, causing
  intermittent `FATAL: sorry, too many clients already` failures unrelated to any code change.
  Capped each test context's pool to 3 connections via each `@SpringBootTest(properties = ...)`
  (a `src/test/resources/application.properties` was tried first but silently shadowed
  `src/main/resources/application.properties` entirely — same classpath resource path, first
  match wins, not merged — breaking the datasource URL/driver; reverted in favor of per-class
  properties). Confirmed stable across 3 consecutive full `./gradlew test` runs.

## Verification

- [x] `./gradlew test` passes — 205/205, stable across repeated runs (176 before this PR + 29
  new: 16 service + 13 controller)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
