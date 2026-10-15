# Spec: Data-subject rights: end a relationship, refuse deletes over history, delete and export an account

Status: In progress. PR (a) (relationship end, delete refusals, integrity fallback) implemented;
PR (b) `DeleteAccount` and PR (c) `ExportUserData` to follow.
Owner: hebertpdl@gmail.com
Related: Vertice health audit 2026-10-09, plan item P1-05 (findings COMP-API-001, PROD-API-001,
REL-API-003, and the API parts of COMP-BFF-001 and PRIV-BFF-003); `docs/data-inventory.md` (D5);
`docs/specs/password-lifecycle/spec.md`

## 0. Scope decisions

- **Three sequential PRs.** (a) `EndTrainerClientRelationship`, the granular-delete refusals and
  the `DataIntegrityViolationException` fallback; (b) `DeleteAccount`, which anonymises per D5;
  (c) `ExportUserData`. (a) changes no data on its own and is what the BFF's `DELETE /clients/:id`
  needs (PRIV-BFF-003); (b) is irreversible, so it ships on top of refusals that already hold.
- **Ending a relationship sets `ended_at` and nothing else.** A trainer's "remove client" must not
  touch the client's account (PRIV-BFF-003). Plans and logs are history and stay with both users.
  The ended row is kept: it is the record of who trained whom, and `uq_trainer_clients_active_client`
  only covers active rows, so the client is free for another trainer straight away.
- **Who may end it:** the trainer, for their own `trainer_id` and a client on their active roster,
  or ADMIN. Not the client: whether a client may leave a trainer on their own is a product decision
  that comes with linking (below). To the trainer, a client who isn't on their roster and one who
  doesn't exist are the same `PERMISSION_DENIED`, decided before the service runs; ADMIN gets
  `NOT_FOUND` when no active relationship exists, including one that already ended.
- **After it ends, the trainer keeps the plan-tree access of the plans they wrote.** The plan-tree
  rules follow the plan's `trainer_id`, not the link, so the trainer still reads those plans and
  the logs under them, and the client keeps reading them too. What needed the active link goes:
  roster, detail `GetUser` (the summary view remains, as for any trainer with a plan for the
  target), `GetExerciseProgress`, setup links, and `CreateTrainingPlan`/`UpdateTrainingPlan` for
  that client. Plan-tree writes other than `UpdateTrainingPlan` (workouts, sets, deleting a plan
  nobody logged) still work: locking a former client's plans read-only is a product decision, left
  as a follow-up.
- **`LinkClient` (re-link an existing user, or invite) is not built.** Invite-and-accept is a
  product decision (PROD-API-001); recorded as a follow-up.
- **Deletes over recorded data are refused, not cascaded.** A workout log, a set log or feedback
  is the client's history, so a delete that would remove it or break its foreign key answers
  `FAILED_PRECONDITION` (`HasRecordedDataException`, "<Resource> <id> has <what> and cannot be
  deleted"), checked with one existence query before anything is deleted:

  | RPC | Refused when |
  | --- | --- |
  | `DeleteTrainingPlan` | any workout of the plan has a workout log |
  | `DeleteWorkout` | the workout has a workout log |
  | `DeleteWorkoutExercise` | any of its sets has a set log |
  | `DeleteExerciseSet` | the set has a set log |
  | `DeleteExercise` | a workout uses it (existing `ExerciseInUseException`) |
  | `DeleteUser` | the user has a plan (either side), a workout log, a relationship as the trainer (ended included) or a private exercise |

  Set logs always belong to a log of the same workout (`RecordSetLog` enforces it), so the plan and
  workout checks only look at workout logs. A started log with nothing recorded still counts: the
  client opened the session. `DeleteUser` keeps working for a user without data; removing an
  account that has data is (b).
- **`DataIntegrityViolationException` is a fallback, never `INTERNAL`/`UNKNOWN`.** Mapped in
  `GrpcExceptionAdvice` by SQLSTATE: `23505` unique → `ALREADY_EXISTS`, `23503` foreign key →
  `FAILED_PRECONDITION`, anything else (NOT NULL, CHECK) → `INVALID_ARGUMENT`. Descriptions are
  fixed text: the driver's message names the values (an email, a CPF). The unique indexes a request
  can race into keep the service's own message (`uq_users_email_lower` → "Email already in use",
  `uq_users_cpf` → "CPF already in use", `uq_trainer_clients_active_client` → "Client already has
  an active trainer"), which covers the concurrent duplicate-CPF race. One line logs the SQLSTATE
  and constraint name only: WARN for the unique and foreign-key races, ERROR for anything else
  (a validation gap worth seeing). Postgres error text can carry the values, so pgjdbc's
  `logServerErrorDetail=false` drops the `Detail: Key (...)=(...)` part from driver messages and
  Hibernate's own SQL-error logger (`org.hibernate.orm.jdbc.error`) is off.
- **Indexes for the checks (V28).** The refusals and (b)'s account deletion filter on
  `set_logs.exercise_set_id`, `workout_logs.client_id`, `training_plans.trainer_id`/`client_id`,
  `workouts.training_plan_id` and `trainer_clients.trainer_id` (all rows, not just active ones),
  none of which had a leading-column index. Part of PERF-API-001.

## 1. `EndTrainerClientRelationship`

`TrainerClientService.EndTrainerClientRelationship(EndTrainerClientRelationshipRequest{trainer_id,
client_id}) returns (google.protobuf.Empty)`. Controller: `requireUser()`, then
`TrainerClientAuthorization#checkEndTrainerClientRelationship` (role, own `trainer_id`, active
link), then `TrainerClientService#endRelationship`, which loads the active row and sets `ended_at`
to the database clock (`DbInstants.now()`).

## 2. Tests

- `plan/RecordedDataIntegrationTest`: a populated plan (workout, two sets, a completed log with one
  set logged, feedback). Every refused delete answers `FAILED_PRECONDITION` with its message and
  removes nothing; what nobody logged still deletes; `DeleteUser` refuses the client and the
  trainer; ending the relationship keeps plans, logs and feedback readable and frees the client.
- `trainerclient/TrainerClientAuthorizationIntegrationTest`: who may end a relationship, and that
  every active-link privilege goes once it ends.
- `grpc/GrpcExceptionMappingTest`: each SQLSTATE branch (and none at all); real JPA unique
  violations on `uq_users_cpf` and `uq_users_email_lower`, whose values appear in neither the
  status nor the captured log output (`OutputCaptureExtension`).
- `RpcAuthorizationCoverageTest` picks the new RPC up on its own.

## 3. Follow-ups

- (b) `DeleteAccount` and (c) `ExportUserData` (this spec, next PRs).
- vertice-bff: `DELETE /clients/:id` calls `EndTrainerClientRelationship` instead of `DeleteUser`
  (P1-05 step 2).
- `LinkClient` / invite-and-accept, and whether a client may end a relationship themself (product).
- Whether a former client's plans become read-only for the trainer (product).
- The rest of PERF-API-001 (P2-06): `workout_exercises.workout_id`/`exercise_id`,
  `exercise_sets.workout_exercise_id`, `workout_feedback.workout_log_id`.
- `UpdateExerciseSet` still rewrites a set that has logged results (REL-API-003); decide whether
  that is refused too.
