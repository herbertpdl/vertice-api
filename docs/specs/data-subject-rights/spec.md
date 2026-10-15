# Spec: Data-subject rights: end a relationship, refuse deletes over history, delete and export an account

Status: Implemented (vertice-api slice of P1-05): PR (a) relationship end, delete refusals and the
integrity fallback; PR (b) `DeleteAccount`; PR (c) `ExportUserData`. The BFF routes are step 2.
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

- **`DeleteAccount` anonymises the user row and deletes what is about the person (D5).** The row
  can't go: plans the user wrote for other clients (as a trainer), relationship history and the
  exercises in those plans point at it, and they belong to the other party's history. So the row
  keeps its id and role, and everything identifying goes: name "Usuário removido"; email, CPF,
  CREF, password hash and all four consent fields NULL. V29 relaxes `ck_users_trainer_cpf` and V30
  `email NOT NULL` for deleted rows only.
- **The email is NULL, not a placeholder** (review of #68). A predictable placeholder such as
  `deleted+<id>@invalid` could be claimed by a live account first (self UpdateUser, a trainer
  updating a client, public CreateUser), and the victim's deletion would then fail on
  `uq_users_email_lower` and roll back for good. NULLs never collide on that index. As defence in
  depth, `CreateUser`, `UpdateUser` and `CreateClientForTrainer` refuse any address on the reserved
  `.invalid` TLD (INVALID_ARGUMENT).
- **A deleted account is missing everywhere**, ADMIN included: `UserService#findByIdOrThrow`
  (GetUser, UpdateUser, RecordConsent, SetUserPassword, DeleteUser → NOT_FOUND), the email lookup
  (VerifyCredentials), and `ListUsers`, which leaves deleted rows out rather than marking them:
  an anonymised row carries nothing ops or the dev login could use. Nothing can be written back
  into it, or attached to it: the user lookups of `CreateClientForTrainer` (trainer),
  `CreateTrainingPlan` (trainer and client) and `GetOrStartWorkoutLog` (client) also treat a
  deleted account as `NOT_FOUND`, ADMIN included (review round 2 of #68).
- **Health-related data is deleted, not aggregated.** Workout feedback, set logs and workout logs
  are held on the client's consent (D4); with the account gone, so is the basis. Anonymised
  aggregates were the alternative: nothing in the product reads them yet, so keeping them would be
  data with no purpose. Feedback is deleted rather than anonymised for the same reason (free text
  can't be anonymised reliably).
- **The plans written for the deleted user go too** (the plan item's order: feedback → set logs →
  logs → plans → relationships → user). A plan prescribed to one client is about them. The plans a
  deleting *trainer* wrote stay with their clients, under the anonymised trainer.
- **A TRAINER with active clients is refused** (`FAILED_PRECONDITION` "End your client
  relationships first…"): deleting would leave active clients on plans with no trainer. Once they
  end the relationships (`EndTrainerClientRelationship`), their unused private exercises are
  deleted and the used ones stay, owned by the anonymised row.
- **Self only, no id in the request.** The token says whose account it is, as for `RecordConsent`.
  An ADMIN variant (support requests) is a follow-up; the service already takes the actor
  separately so the audit line doesn't change.
- **Revocation is `deleted_at`.** `findRoleById` filters `deleted_at IS NULL`, so the account's
  unexpired tokens fail the per-call role check (`UNAUTHENTICATED`) on their next use. A deleted
  account is also `NOT_FOUND` for `CreatePasswordSetupToken` and absent from
  `ListUsersWithoutPassword`: it has no password, but must never be offered one again.
- **Serialised per account:** the row is read `FOR UPDATE`, so two concurrent deletions run one
  after the other and the second gets `UNAUTHENTICATED`/`NOT_FOUND`.
- **Audit:** one INFO line, `Account deleted: actorId=… userId=… role=…`, with no PII. The
  `audit_events` table is P2-08.
- **`ExportUserData` returns one JSON document in a proto string** (LGPD art. 18 II and V). JSON
  is the portable format a person can open or hand to another service; a structured proto would
  only be readable through our own clients, and the BFF can pass the string through as a file.
  `formatVersion` changes whenever a key is renamed or removed.
- **What the export holds** is everything stored *about the caller*, per the data inventory:
  profile (email, CPF, CREF included: it is their own), consent, every relationship on either side
  with the other party's id and name, every plan they are the client or the trainer of (whole tree:
  workouts, exercises, sets, notes), their own workout logs with set logs and feedback, and their
  private exercises. Left out: the password hash and setup tokens (credentials, which no RPC
  returns), and, for a trainer, their clients' sessions and feedback (those clients' data; the
  trainer reads them in the product, but they are not the trainer's personal data).
- **Bounded, never truncated.** Each section is read with `LIMIT max-rows + 1`
  (`vertice.export.max-rows`, default 20 000) and the document is measured after serialization
  (`vertice.export.max-size`, default 3 MB, under gRPC's 4 MiB default receive limit that the
  BFF's client keeps). Past either limit the RPC is `RESOURCE_EXHAUSTED`, naming the section and
  limit only: a silently partial export would misstate what we hold. Today's largest accounts are
  orders of magnitude below; paging or streaming is a follow-up if one ever reaches the bound.
- **One consistent snapshot:** a read-only `REPEATABLE READ` transaction, so a set logged mid-export
  can't appear without its log. Plain SQL, one set-based query per section (children first, grouped
  by parent), so the cost doesn't grow with the number of plans or logs.
- **Timestamps** are read as UTC wall-clock time, the way Hibernate stores `Instant` in these
  `TIMESTAMP` columns, so they match what every other RPC returns (an integration test compares one
  with the entity). Self only, like `DeleteAccount`; a deleted account's token is already
  `UNAUTHENTICATED`, and the profile query filters `deleted_at` as well.
- **Audit:** one INFO line, `User data exported: userId=… bytes=…`.

## 1. `EndTrainerClientRelationship`

`TrainerClientService.EndTrainerClientRelationship(EndTrainerClientRelationshipRequest{trainer_id,
client_id}) returns (google.protobuf.Empty)`. Controller: `requireUser()`, then
`TrainerClientAuthorization#checkEndTrainerClientRelationship` (role, own `trainer_id`, active
link), then `TrainerClientService#endRelationship`, which loads the active row and sets `ended_at`
to the database clock (`DbInstants.now()`).

## 2. `DeleteAccount`

`UserService.DeleteAccount(DeleteAccountRequest{}) returns (google.protobuf.Empty)`. Controller:
`UserAuthorization#checkDeleteAccount` (any user; a service is `PERMISSION_DENIED`) returns the
caller's id; `AccountDeletionService#deleteAccount(actorId, userId)` locks the row, checks the
trainer rule, runs the `AccountDeletionRepository` statements (feedback, set logs, workout logs,
then the client's plan tree bottom-up, unused exercises, relationships, tokens) and calls
`User#anonymise` with the database clock.

## 3. `ExportUserData`

`UserService.ExportUserData(ExportUserDataRequest{}) returns (ExportUserDataResponse{json})`.
Controller: `UserAuthorization#checkExportUserData` (any user; a service is `PERMISSION_DENIED`)
returns the caller's id; `UserDataExportService#exportUserData` reads every section through
`UserDataExportQueries`, builds `UserDataExport` (records whose fields are the JSON keys),
serializes it with Boot's `JsonMapper` and checks the size. `ExportTooLargeException` maps to
`RESOURCE_EXHAUSTED`.

## 4. Tests

- `plan/RecordedDataIntegrationTest`: a populated plan (workout, two sets, a completed log with one
  set logged, feedback). Every refused delete answers `FAILED_PRECONDITION` with its message and
  removes nothing; what nobody logged still deletes; `DeleteUser` refuses the client and the
  trainer; ending the relationship keeps plans, logs and feedback readable and frees the client.
- `trainerclient/TrainerClientAuthorizationIntegrationTest`: who may end a relationship, and that
  every active-link privilege goes once it ends.
- `grpc/GrpcExceptionMappingTest`: each SQLSTATE branch (and none at all); real JPA unique
  violations on `uq_users_cpf` and `uq_users_email_lower`, whose values appear in neither the
  status nor the captured log output (`OutputCaptureExtension`).
- `RpcAuthorizationCoverageTest` picks the new RPCs up on its own.
- `user/AccountDeletionIntegrationTest` (populated: plan, sets, a completed log with a set log,
  feedback, consent, a setup link): a client's deletion removes the health data, the plan for them
  and the link, anonymises the row, kills the token, hides the account from setup links, and logs
  ids only, and leaves the row unreachable for ADMIN (GetUser, UpdateUser, SetUserPassword are
  NOT_FOUND; ListUsers leaves it out); a second client of the same trainer keeps their plan tree,
  log, set log, feedback, setup link and relationship; the deletion succeeds while a live row
  holds the old placeholder address, and every write path refuses a `.invalid` address; a trainer
  with an active client is refused and unchanged; a trainer without one is anonymised, loses the
  unused exercise, and their client keeps the plan, logs and feedback, while ADMIN can neither
  create a client for that trainer nor a plan under them (`NOT_FOUND`).
- `UserControllerTest`: self only (client, trainer, ADMIN each delete their own), services denied.
- `config/DatasourceErrorDetailTest` pins `logServerErrorDetail=false`: the effective Hikari
  property, and a real unique violation whose message names the constraint but not the value.
- `user/UserDataExportIntegrationTest` (a trainer with two clients, each with a plan, a completed
  log with a set log and feedback): the client's export holds their profile, consent,
  relationship, plan tree, log, set log and feedback, with timestamps equal to the entities', and
  nothing of the other client nor any credential; the trainer's holds both plans, both
  relationships and their exercise but no client session; services are refused and a deleted
  account's token can't export. `UserDataExportLimitsTest`: a section past `max-rows` is
  `RESOURCE_EXHAUSTED`. `UserDataExportServiceTest`: the size bound, and a missing account.

## 5. Follow-ups

- vertice-bff (P1-05 step 2): `DELETE /auth/me` → `DeleteAccount`, `GET /auth/me/export` →
  `ExportUserData` (as a JSON file download), `GET/PATCH /auth/me`.
- Paging or streaming the export, if an account ever reaches the bounds.
- ADMIN-initiated `DeleteAccount` for a support request.
- vertice-bff: `DELETE /clients/:id` calls `EndTrainerClientRelationship` instead of `DeleteUser`
  (P1-05 step 2).
- `LinkClient` / invite-and-accept, and whether a client may end a relationship themself (product).
- Whether a former client's plans become read-only for the trainer (product).
- The rest of PERF-API-001 (P2-06): `workout_exercises.workout_id`/`exercise_id`,
  `exercise_sets.workout_exercise_id`, `workout_feedback.workout_log_id`.
- `UpdateExerciseSet` still rewrites a set that has logged results (REL-API-003); decide whether
  that is refused too.
