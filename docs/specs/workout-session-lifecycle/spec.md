# Spec: Workout session lifecycle (redo, discard, untick, weekly rollover) and feedback rating

Status: Implemented
Owner: hebertpdl@gmail.com
Related: vertice-mobile `docs/prds/client-workout-experience/prd.md` (R19–R28, R34, R38, R66–R72)
and `docs/specs/client-workout-experience/spec.md` §2.2 items A1–A4;
`docs/specs/workout-session-logging/spec.md` and `docs/specs/workout-feedback/spec.md` (the model
this extends)

## 0. Scope decisions

- **A session has a `status`.** `IN_PROGRESS`, `COMPLETED` or `SUPERSEDED`. The old
  `UNIQUE (workout_id, client_id, week_start_date)` becomes two partial unique indexes (one
  `IN_PROGRESS`, one `COMPLETED` per workout, client and week), so a redo can run next to the
  week's completed session. `V32` backfills existing rows from `completed_at`.
- **A redo replaces the completed session only when it is finished.** `CompleteWorkoutLog` marks
  the week's earlier `COMPLETED` session `SUPERSEDED` in the same transaction, flushed first so the
  index never sees two. A superseded session keeps its set logs and feedback. It drops out of
  `ListWorkoutLogs`, `GetLastSetLogs` and progress, and its feedback is flagged `replaced`.
- **`GetOrStartWorkoutLog` keeps its meaning.** It resumes `IN_PROGRESS`, else returns the week's
  `COMPLETED` session, else starts one. A redo is the explicit `RestartWorkoutLog`, the client's own
  only.
- **Discarding and unticking.** `DiscardWorkoutLog` deletes an `IN_PROGRESS` session with its set
  logs. `DeleteSetLog` deletes one set log, and is a no-op when there is none. Both are
  FAILED_PRECONDITION on a finished session, like `RecordSetLog` already was.
- **Blank weights.** `RecordSetLog` already stored `''` as NULL; the proto now documents it.
- **Weekly rollover.** `WorkoutWeekRollover` runs Monday 00:05 in `America/Sao_Paulo` (the first
  `@Scheduled` job here, hence `SchedulingConfig`):
  - A session left `IN_PROGRESS` in an earlier week becomes `COMPLETED` with
    `completion_kind = AUTO` when it has a set log; that's a finish, so it supersedes like one.
  - One with no set log is deleted.
  - `GetOrStart`, `Restart` and `ListWorkoutLogs` run the same pass for their client first, so a
    late or missed job never shows. "This week" is `BusinessDates.currentWeekStart(Clock)`, with a
    `Clock` bean (`ClockConfig`) that tests can fix.
- **Feedback rating.** `FeedbackRating` (five levels) is required on new feedback (UNSPECIFIED is
  INVALID_ARGUMENT); old rows keep NULL. The text becomes optional. Feedback still needs a
  `COMPLETED` session (a superseded one can't take new feedback), with at most 10 per session.
- **Export.** `ExportUserData` carries each log's `status` and each feedback's `rating`.

## 1. Contract (additive; `buf breaking` clean)

- `WorkoutLogResponse.status` (`WorkoutLogStatus`) and `completion_kind` (`CompletionKind`).
- `RestartWorkoutLog`, `DiscardWorkoutLog`, `DeleteSetLog` and `ListSetLogs`; the last one lets a
  client resume a session on another device.
- `SubmitWorkoutFeedbackRequest.rating`; `WorkoutFeedbackResponse.rating` and `replaced`.

The authorization rows are in CLAUDE.md's matrix.

## 2. Tests

- `WorkoutSessionLifecycleIntegrationTest` runs over gRPC and the database. It covers:
  - a redo superseding the original only when finished;
  - "last time" values and feedback `replaced`;
  - discarding a redo keeps the original, and a finished session can't be discarded;
  - unticking, a blank weight, and the finished-session refusal;
  - the job and the lazy rollover;
  - the rating being required.
- The existing service, controller, migration and export tests were updated for the status, the
  clock and the rating.
