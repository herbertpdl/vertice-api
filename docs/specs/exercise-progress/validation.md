# Validation checklist: Exercise progress graph data

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Data model (section 2)

- [x] `SetLogRepository#findCompletedSetLogsForClientAndExercise` — the JPQL `@Query` parses and
  the Spring context loads successfully (`WorkoutSessionControllerTest` boots the full app
  context including this repository bean — a malformed JPQL string fails fast at context startup,
  not silently)

## Contract (section 3)

- [x] `GetExerciseProgress` RPC added to `WorkoutSessionService`/`workout_session.proto`,
  `GetExerciseProgressResponse` carries `repeated ProgressPoint`

## Validation rules (section 4)

- [x] `client_id`/`exercise_id` unset (0) → `INVALID_ARGUMENT` —
  `WorkoutSessionControllerTest#getExerciseProgress_withZeroExerciseId_throwsInvalidArgument`
- [x] Unknown ids → empty `points`, not an error —
  `WorkoutSessionServiceTest#getExerciseProgress_returnsEmptyWhenNoHistory`

## Behavior (section 5)

- [x] One point per week, `MAX(weight)` that week when multiple `SetLog`s land in the same week
  — `WorkoutSessionServiceTest#getExerciseProgress_returnsOnePointPerWeekWithMaxWeight` (asserts
  the higher of two same-week weights wins, and points come back oldest-to-newest)
- [x] `SetLog`s with no `weight` are skipped, not treated as zero —
  `WorkoutSessionServiceTest#getExerciseProgress_skipsSetLogsWithNoWeight`
- [x] Only completed sessions contribute — enforced by the repository query itself
  (`completedAt IS NOT NULL`), same mechanism already relied on for `GetLastSetLogs`

## Verification

- [x] `./gradlew test` passes — 222/222 (217 before this PR + 5 new: 3 service + 2 controller)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
