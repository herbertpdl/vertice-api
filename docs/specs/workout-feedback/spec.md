# Spec: Workout feedback

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "They should be able to provide feedback
about a workout every time they finish one. The feedback need to be delivered to the personal
trainer linking the workout, training plan and client that gave the feedback"),
`docs/specs/workout-session-logging/spec.md` (the `WorkoutLog` this attaches to)

## 0. Scope decisions

- **Feedback attaches to a `WorkoutLog`, not directly to a `Workout`.** requirements.md says
  feedback happens "every time they finish one [workout]" — that's per-session, and `WorkoutLog`
  is already this codebase's per-session concept (`workout-session-logging/spec.md`). One
  `WorkoutLog` can have at most... actually, no stated limit, so no uniqueness constraint (a
  client could in principle submit more than one feedback for the same session — not
  restricted, matches this plan's general "don't add constraints requirements.md didn't ask
  for" pattern, e.g. `workout-day-of-week/spec.md` §0 on weekday uniqueness).
- **`workout_id`/`training_plan_id`/`client_id` are resolved via joins, not stored.** They're all
  reachable through `WorkoutLog` → `Workout` → `TrainingPlan` → `trainer`/`client`; storing them
  again on `WorkoutFeedback` would just be denormalization with no independent source of truth.
  `ListWorkoutFeedback`'s response embeds them anyway (computed in the service) so the trainer
  view has everything in one round-trip, per the requirement's own phrasing ("linking the
  workout, training plan and client").
- **Submission requires a *completed* `WorkoutLog`.** "Every time they finish one" ties feedback
  to completion; submitting against an in-progress session is rejected.
- **No ownership check** on `SubmitWorkoutFeedback` (any caller-supplied `workout_log_id` is
  accepted) — same accepted gap as every prior spec in this plan
  (`training-plan-fields/spec.md` §0).

## 1. Goal

New `WorkoutFeedback` entity + `WorkoutFeedbackService`/`WorkoutFeedbackController` gRPC surface:
a client submits free-text feedback for a completed workout session; a trainer lists all feedback
across their clients/plans, each entry carrying the workout/plan/client it belongs to.

## 2. Data model

```sql
CREATE TABLE workout_feedback (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_log_id  BIGINT NOT NULL,
    text            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_workout_feedback_workout_log FOREIGN KEY (workout_log_id) REFERENCES workout_logs (id)
);
```

Migration `V19` (new table, no backfill). `WorkoutFeedback` entity: `workoutLog` (`@ManyToOne`),
`text` (`String`, required), `createdAt` (`Instant`, server-set).
`WorkoutFeedbackRepository.findByWorkoutLog_Workout_TrainingPlan_TrainerId(Long trainerId)` —
nested-association derived query, same mechanism `WorkoutLogRepository` already relies on for
`findByClientIdAndWorkout_TrainingPlan_IdAndWeekStartDate`.

## 3. Contract

New file `src/main/proto/vertice/session/v1/workout_feedback.proto`, same package as
`workout_session.proto`.

| RPC | Request | Response |
|---|---|---|
| `SubmitWorkoutFeedback` | `{workout_log_id, text}` | `WorkoutFeedbackResponse` |
| `ListWorkoutFeedback` | `{trainer_id}` | `repeated WorkoutFeedbackResponse` |

`WorkoutFeedbackResponse`: `id`, `workout_log_id`, `workout_id`, `training_plan_id`, `client_id`,
`text`, `created_at` — the last four resolved via the `WorkoutLog` association chain in the
mapper/service, not stored columns.

## 4. Validation rules

- `text`: `@NotBlank`.
- `workout_log_id`: `@Min(1)`, must resolve or `NOT_FOUND` ("WorkoutLog").
- Submitting against a `WorkoutLog` whose `completedAt` is `null` → rejected
  (`ConstraintViolationException`, "workout session is not completed yet").
- `trainer_id` on `ListWorkoutFeedback`: `@Min(1)`. Not required to resolve to an actual `User` —
  an unknown/mistyped id just yields an empty list, same as `TrainingPlanService#listTrainingPlans`
  never validates its `trainerId`/`clientId` filters exist either.

## 5. Out of scope

- Any ownership/ACL check on submission (see §0).
- Feedback editing/deletion, structured ratings (numeric score) — requirements.md only asks for
  free-text feedback.
