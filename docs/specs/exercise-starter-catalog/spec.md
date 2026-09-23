# Spec: Starter exercise catalog

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/prds/exercise-starter-catalog/prd.md`,
`docs/assessments/exercise-starter-catalog/assessment.md`,
`docs/specs/grpc-exercise-catalog/spec.md` (the CRUD this reshapes),
`docs/specs/exercise-video-url/spec.md` (the `video_url` rule R42 reuses unchanged),
`docs/specs/training-plan-fields/spec.md` (touched: its "no authorization enforcement" gap is
closed here for the exercise RPCs and the four workout RPCs of Increment 2, inherited everywhere
else), `docs/specs/user-unification/spec.md` (touched: its §3.5 "`role` does not gate any
endpoint" gap is closed for the same RPCs), `docs/specs/workout-session-logging/spec.md` (touched:
same inherited gap; the session RPCs stay caller-supplied `client_id`, and the R20 client path
through `GetExercise` is what this spec adds), `docs/specs/clone-workout/spec.md` (touched:
`CloneWorkout` gains the R19 ownership check; same-plan cloning and deep copy unchanged),
`docs/specs/workout-exercise-crud/spec.md` (touched: `CreateWorkoutExercise` gains R17/R18; its
"`exercise_id` immutable after creation" decision is why `UpdateWorkoutExercise` needs no guard),
`docs/specs/create-workout-with-exercises/spec.md` (touched: `CreateWorkoutWithExercises` and
`ReplaceWorkoutExercises` gain R17/R18; its §0 "no ownership check" decision is superseded for
these two RPCs), `docs/domain-model.md` (the `Exercise` catalog is no longer "shared across every
trainer" — PR3 updates that line).
Contract brief: the cross-repo decisions `D1`–`D17` cited below come from the orchestrator's
contract brief for this feature; the sibling specs are
`https://github.com/herbertpdl/vertice-bff/blob/main/docs/specs/exercise-starter-catalog/spec.md`
and
`https://github.com/herbertpdl/vertice-web-react/blob/main/docs/specs/exercise-starter-catalog/spec.md`
(both being written in the same run — not on `main` yet).

## 0. Scope decisions

**No owner was available when the contract brief was fixed.** Every `D*` decision below is
*assumed in the absence of the owner; the assessment's recommendation* where the assessment made
one, otherwise the orchestrator's best judgment. The owner overturns any of them before
implementation starts; a decision marked "(mine)" was made by this spec's author and is called out
as such.

- **Caller identity is the BFF's own JWT, verified with a shared HS256 secret and resolved once
  under `grpc/` (F12, F26, F29, Q3; D1 — assumed).** `vertice-bff` attaches `authorization:
  Bearer <its JWT>` as call metadata on every RPC; vertice-api verifies it with `JWT_SECRET`
  (same value both sides; default `dev-secret-change-me` for local) instead of the never-wired
  `issuer-uri` decoder, and resolves `CallerIdentity { userId = claim "id", role = claim "role" }`
  through one `CallerIdentityResolver` under `grpc/` that every controller reuses. Non-local: every
  RPC still requires authentication (as today), now against the shared secret. `local`: a token is
  optional — decoded when present, anonymous when absent; only RPCs marked "requires identity"
  fail `UNAUTHENTICATED` on an anonymous call, so every existing controller test keeps working
  without a token. Cost accepted: the BFF-minted JWT is reused as the service-to-service
  credential (a real identity provider is out of scope), and the secret must be provisioned
  identically on both sides.
  - *Sub-decision (mine): the decoder is a length-agnostic HS256 `JwtDecoder`, not
    `NimbusJwtDecoder.withSecretKey`.* Verified this session: Nimbus 10.9's `MACProvider` throws
    `KeyLengthException("The secret length must be at least 256 bits")` for any secret under 32
    bytes, and both local defaults are shorter (`dev-secret-change-me` = 20 bytes; the BFF's
    checked-in `.env` value `change-me-in-production` = 24 bytes). `jsonwebtoken` 9.0.3 on the BFF
    signs with any length. So `config/HmacJwtDecoder` implements
    `org.springframework.security.oauth2.jwt.JwtDecoder` directly with `javax.crypto.Mac`
    (HmacSHA256) and a constant-time compare (§6). If the owner prefers to require a ≥32-byte
    secret on both sides instead, this class collapses to one `NimbusJwtDecoder` line — reported
    to the orchestrator as the point where the brief's default value and the standard decoder
    conflict.
  - *Sub-decision (mine): under `!local` the application refuses to boot with a secret shorter
    than 32 bytes* (`config/JwtSecretGuard`), so the length-agnostic decoder never silently runs
    production on a weak key.
  - *Sub-decision (mine): controllers resolve the identity and pass `CallerIdentity` into the
    service.* Services stay plain Mockito-testable (the assessment's §6 "mock the resolved caller
    identity two ways"), and `*ControllerTest` is what proves the wiring over the wire.
  - *Consequence (mine): under `local`, a token that is present but invalid fails
    `UNAUTHENTICATED` even for RPCs that do not need identity* — that is what "decoded when
    present" means for the resource-server interceptor; only an absent token is anonymous.
- **Cross-trainer and wrong-role refusals are `PERMISSION_DENIED`; a missing id stays `NOT_FOUND`
  (F13; D2 — assumed).** The PRD says "refused, not merely absent" (R16, R21–R24, R32, R36), and
  the BFF already maps this pair cleanly (403 `FORBIDDEN` / 404 `NOT_FOUND`). Cost accepted: a
  refused caller learns the id exists. This sets the pattern for every future ownership check in
  this codebase. New `PermissionDeniedException` in `common/exception/`, in both handlers (F20).
- **Changing or deleting a starter-set exercise is `PERMISSION_DENIED` with a fixed message (R26,
  R27, E1, E2, F20; D3 — assumed).** `Exercise <id> belongs to the shared starter set and cannot
  be changed` / `... cannot be deleted`. Not `FAILED_PRECONDITION`: no state change can ever make
  the answer different, so it is a permission, not a precondition.
- **Deleting an exercise a workout uses is `FAILED_PRECONDITION` via `ExerciseInUseException`
  (R34, E5, F15, F20; D4 — assumed).** Message `Exercise <id> is used by a workout and cannot be
  deleted`, by analogy to `WorkoutExerciseHasRecordedDataException`; checked with an
  `existsByExerciseId` query before the delete, so the raw FK violation that today falls through
  as `UNKNOWN` never happens. Registered in `GrpcExceptionAdvice` (→ `FAILED_PRECONDITION`) and
  `GlobalExceptionHandler` (→ `412 PRECONDITION_FAILED`, same as the existing precedent).
- **Role rules per exercise RPC (R13–R25; D5 — assumed).** `ListMuscleGroups`: anyone, no
  identity. `ListExercises`: TRAINER → starter ∪ own; ADMIN → starter only (R25); CLIENT →
  `PERMISSION_DENIED` (R21). `GetExercise`: TRAINER → starter or own; ADMIN → starter only;
  CLIENT → only when a `workout_exercises` row references it inside a workout of a training plan
  whose `client_id` is the caller (R20, R22). `CreateExercise`/`UpdateExercise`/`DeleteExercise`:
  TRAINER only — CLIENT *and* ADMIN get `PERMISSION_DENIED` (R23, R24; the platform team has no
  exercise-management surface, PRD §6/R37). Cost accepted: an ADMIN opening a full workout that
  contains a trainer's private exercise gets a 403 from the embedded `GetExercise` (documented in
  the BFF spec, not mitigated).
- **Muscle groups are a table with database ids; exercises reference groups by id; the proto
  `MuscleGroup` enum is deleted and its fields `reserved` (F1, F6, F7, F9, F25; D6 — assumed).**
  `muscle_groups` is seeded with the 14 launch groups in R8 order (ids 1..14), new RPC
  `ListMuscleGroups` returns them, and `ExerciseResponse` embeds `muscle_groups` as `{id, name}`
  objects so composed BFF endpoints stay self-contained. Field 5 of `ExerciseResponse` and field 4
  of `ExerciseRequest` are `reserved` (number and name) — the first `reserved` use in this
  codebase, set deliberately rather than by retyping in place. Matches the owner's own constraint
  (PRD §9: a groups table, exercises refer to group ids).
- **The join row carries `is_primary` and `catalog_order` (R48–R50, F6, F7, F17, F23; D7 —
  assumed).** `is_primary` marks the PRD §10 section the exercise is listed under; `catalog_order`
  is its 1-based row number there. Ordering is produced by the query (§5.2), never by sorting in
  Java. Column named `is_primary` because `primary` is a reserved word (mine).
- **Pre-starter cleanup is one forward-only Flyway SQL migration in its own PR, a one-way door
  (R51–R59, E12, E23, F4, F5, F8, F16, F21, F27, F28, PRD open question 2; D8 — assumed).** V23
  deletes in the order `set_logs` → `exercise_sets` → `workout_exercises` → `exercises` and stops
  there (`workout_logs`/`workout_feedback` are keyed only by `workout_log_id`, so R54 holds by
  construction). The R56 keep-list is a literal inside the migration and is **empty** (assessment
  Q2: all pre-starter rows are test data); the R57–R59 copy/repoint/refile routine is still written,
  driven by that literal, and proven by `PreStarterCleanupMigrationTest` against a fixture, so the
  capability exists if the platform team supplies ids before merge. No undo exists (F28); the
  runbook says so. No optimistic locking is added for this or any other path (F16 — baseline
  unchanged); no logging is introduced (F21 — the migration is plain SQL, Flyway's history row is
  the record).
- **`ListExercises` changing from unfiltered to scoped, and the `Exercise` shape change, ship
  without versioning (F10; D9 — assumed).** The only consumer is the BFF (`GET /exercises`, and
  `GetExercise` inside `getFullWorkout`), whose only consumer is the web app. Increment 1 is one
  coordinated deploy window api → bff → web, after Increment 0 is live on api and bff.
- **R17–R19 are enforced here only, on four RPCs (E18–E20; D10 — assumed).**
  `CreateWorkoutExercise`, `CreateWorkoutWithExercises`, `ReplaceWorkoutExercises` (R17 exercise
  visibility + R18 plan ownership) and `CloneWorkout` (R19, both sides), using the D1 identity.
  The BFF writes no `assertOwnsExercise`; upstream is the single enforcer. Cost accepted: an ADMIN
  can no longer add to, replace on, or clone a trainer's workout (never the plan's trainer) — no
  product surface needs it. Every other plan/workout/session/user RPC keeps the inherited
  "caller-supplied ids, no identity" gap from `training-plan-fields`, `user-unification` §3.5 and
  `workout-session-logging` §0 — closed here only where the PRD builds on it.
- **Trainer-created exercises have no primary muscle group (owner, 2026-09-23).** Every group a
  trainer picks is stored with `is_primary = false`; `is_primary` only files starter rows (and
  R59-refiled kept rows) under their PRD §10 section. Pills therefore render in id order for a
  trainer's own exercise. Settles the BFF spec's open question.
- **`UpdateExercise` replaces name, description, video link and the whole group list (R29–R31,
  E22, F19, PRD open question 1; D11 — assumed).** At least one group required after
  de-duplication, same check as create.
- **This feature adds the first repository-level test (F23; D12 — assumed).**
  `ExerciseCatalogRepositoryTest` runs the real query against Postgres (`@SpringBootTest` on its
  own gRPC port, direct repository seeding, cleanup in `@AfterEach` — the shape
  `ReplaceWorkoutExercisesIntegrationTest` already set) to prove R47–R50 ordering and the seed
  counts; `PreStarterCleanupMigrationTest` proves the D8 routine.
- **`NOT_FOUND` descriptions keep this codebase's `ResourceNotFoundException` format (mine).**
  The brief writes them as `Exercise <id> not found`; the existing exception, which every RPC and
  the BFF already see, produces `Exercise with id <id> not found` (and `MuscleGroup with id <id>
  not found`). The status code is the contract; the wording is not changed for one feature.
  Reported to the orchestrator.
- **`GrpcExceptionAdvice#handleValidation` falls back to the exception message when there are no
  violations (mine; baseline gap found this session).** Every hand-thrown
  `ConstraintViolationException("...", Set.of())` in this codebase (e.g. `muscleGroup: must be
  set`, `exercises: must not exceed 20 entries`) currently reaches the wire as `INVALID_ARGUMENT`
  with an **empty** description, because the advice only formats `getConstraintViolations()`.
  The brief pins `muscleGroupIds: must contain at least one muscle group` and `muscleGroupIds:
  unknown muscle group <id>` as wire messages, so the fallback is added in PR3 with a
  `GrpcExceptionMappingTest` scenario. Existing messages start reaching the BFF too — an
  improvement, not a break (the BFF passes descriptions through).
- **`owner_id IS NULL` means starter set; `is_starter` is derived, not stored (mine).** One
  nullable FK to `users` covers R14/R57; no second flag can drift from it. A trainer-owned exercise
  is never deleted with its owner (no `ON DELETE` — `training_plans.trainer_id` has none either).
- **`exercise_muscle_groups` has a surrogate `id` plus `UNIQUE (exercise_id, muscle_group_id)`
  (mine).** Every table in this codebase uses a surrogate identity key (`trainer_clients` is the
  latest); a composite `@EmbeddedId` would be the first and buys nothing here.
- **Search escapes `%`, `_` and `\` before binding (mine).** `search` is an `ILIKE '%…%'`
  substring per the brief; without escaping a user-typed `%` widens the match. Accent-insensitive
  matching stays out of scope (brief).
- **Deferred Mediums:** F17 pagination (≈200 starter rows + a trainer's own — not needed; the
  query is built as a real filtered/ordered query from the start so pagination is additive later),
  F22 metrics (no `MeterRegistry` baseline), F16 locking, F21 logging — all unchanged baselines.
  Resolved Mediums: F6/F7 (`is_primary`/`catalog_order`), F13 (`PERMISSION_DENIED`), F19 (empty
  group list check shared by create/update), F20 (every new exception in both handlers), F25
  (`MuscleGroup` becomes an entity in `plan/exercise`; nothing outside the package referenced the
  enum — verified: `WorkoutExercise` only references `Exercise`).

## 1. Goal

- **Increment 0** — vertice-api verifies the BFF's HS256 JWT on both transports and can resolve
  `CallerIdentity` for any RPC; no RPC requires it yet; nothing user-visible changes.
- **Increment 1** — `ExerciseService` gains `ListMuscleGroups`; `ListExercises` becomes
  identity-scoped, group-filtered, name-searched and deterministically ordered (R13–R15, R45–R50);
  `GetExercise`/`CreateExercise`/`UpdateExercise`/`DeleteExercise` enforce role, ownership,
  starter-set immutability and the in-use guard (R16, R20–R27, R29–R36, R39–R44); exercises carry
  one or more of 14 muscle groups (R3, R8, R11, R43); the 199-exercise starter set (R1–R12) is
  seeded and the pre-starter rows are removed (R51–R59).
- **Increment 2** — `CreateWorkoutExercise`, `CreateWorkoutWithExercises`,
  `ReplaceWorkoutExercises`, `CloneWorkout` refuse cross-trainer exercises, workouts and plans
  (R17–R19).

Stays untouched: `R33`/`E6` (session responses carry ids only — a rename shows everywhere, F2);
`R44`/`E3`/`E4` (no name uniqueness, F3); `R41`/`R42` (description optional, `video_url`
pattern, F14); `R20`/`E7` on the session RPCs (`WorkoutSessionService` never filters exercises;
the client's path to an exercise is `GetExercise` under D5); `R28` (app-side omission, web);
`R37`/`R38` hold by omission (no promote-to-starter or muscle-group CRUD RPC is ever exposed);
`R25` has no surface to build. `UpdateWorkoutExercise`, `DeleteWorkoutExercise`,
`ListWorkoutExercises`, `GetWorkoutExercise`, all `ExerciseSetService`, `TrainingPlanService`,
`WorkoutSessionService`, `WorkoutFeedbackService`, `UserService`, `TrainerClientService` RPCs, and
`GetExerciseProgress` are unchanged.

## 2. Contract (`src/main/proto/vertice/exercise/v1/exercise.proto`)

Verbatim from the contract brief (package, options, RPC names, message and field names, numbers
and `reserved` lines are binding; the BFF copy is `protos/vertice/exercise/v1/exercise.proto`):

```proto
syntax = "proto3";

package vertice.exercise.v1;

import "google/protobuf/empty.proto";

option java_package = "com.vertice.api.generated.grpc.exercise.v1";
option java_multiple_files = true;

service ExerciseService {
  rpc ListMuscleGroups(ListMuscleGroupsRequest) returns (ListMuscleGroupsResponse);
  rpc ListExercises(ListExercisesRequest) returns (ListExercisesResponse);
  rpc GetExercise(GetExerciseRequest) returns (ExerciseResponse);
  rpc CreateExercise(ExerciseRequest) returns (ExerciseResponse);
  rpc UpdateExercise(UpdateExerciseRequest) returns (ExerciseResponse);
  rpc DeleteExercise(DeleteExerciseRequest) returns (google.protobuf.Empty);
}

message MuscleGroupResponse {
  int64 id = 1;
  string name = 2;
}

message ListMuscleGroupsRequest {
}

message ListMuscleGroupsResponse {
  repeated MuscleGroupResponse muscle_groups = 1;
}

message ExerciseResponse {
  reserved 5;
  reserved "muscle_group";
  int64 id = 1;
  string name = 2;
  string description = 3;
  string video_url = 4;
  repeated MuscleGroupResponse muscle_groups = 6;
  bool is_starter = 7;
}

message ExerciseRequest {
  reserved 4;
  reserved "muscle_group";
  string name = 1;
  string description = 2;
  string video_url = 3;
  repeated int64 muscle_group_ids = 5;
}

message ListExercisesRequest {
  int64 muscle_group_id = 1;
  string search = 2;
}

message ListExercisesResponse {
  repeated ExerciseResponse exercises = 1;
}

message GetExerciseRequest {
  int64 id = 1;
}

message UpdateExerciseRequest {
  int64 id = 1;
  ExerciseRequest exercise = 2;
}

message DeleteExerciseRequest {
  int64 id = 1;
}
```

The `enum MuscleGroup` is deleted from the file (only the two reserved fields referenced it).
`workout.proto` and `workout_exercise.proto` are **unchanged** in Increment 2.

### Identity metadata (all services) — Increment 0

Metadata key `authorization`, value `Bearer <BFF JWT>`; claims read: `id` (numeric user id),
`role` (`ADMIN` | `TRAINER` | `CLIENT`). The token's `exp`, when present, is honored; `iat`,
`name`, `email` are ignored.

### Status codes, per RPC, in check order

As built, request-shape validation (`INVALID_ARGUMENT` from the controller) runs **before** the
service's role check, so for `ListExercises`, `CreateExercise` and `UpdateExercise` a wrong-role
caller sending a malformed request gets `INVALID_ARGUMENT`, not `PERMISSION_DENIED`. Identity
(`UNAUTHENTICATED`) is still checked first, and a well-formed request from a wrong role still gets
`PERMISSION_DENIED`. Accepted by the owner (2026-09-23); the rows below read in that order.

| RPC | Status | Fires when | Exception |
|---|---|---|---|
| any (non-local profile) | `UNAUTHENTICATED` | no/invalid bearer token, as today | Spring gRPC security interceptor |
| any (local profile) | `UNAUTHENTICATED` | a bearer token is present but not verifiable with the shared secret | Spring gRPC security interceptor |
| `ListMuscleGroups` | — | never fails beyond transport; identity not required | — |
| `ListExercises` | `UNAUTHENTICATED` | no identity on the call | `UnauthenticatedException` |
| | `PERMISSION_DENIED` | caller role `CLIENT` (R21) | `PermissionDeniedException` — `Role CLIENT is not allowed to list exercises` |
| | `INVALID_ARGUMENT` | `search` longer than 100 chars | `ConstraintViolationException` (`search: size must be between 0 and 100`) |
| | `NOT_FOUND` | `muscle_group_id` ≠ 0 and no such group | `ResourceNotFoundException("MuscleGroup", id)` — `MuscleGroup with id <id> not found` |
| `GetExercise` | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `NOT_FOUND` | no such exercise | `ResourceNotFoundException("Exercise", id)` |
| | `PERMISSION_DENIED` | TRAINER and `owner_id` is another user; ADMIN and `owner_id` not null; CLIENT and no `workout_exercises` row in a plan with `client_id` = caller references it (R16, R22, R25) | `PermissionDeniedException` — `You do not have access to exercise <id>` |
| `CreateExercise` | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `PERMISSION_DENIED` | role ≠ TRAINER (R23; ADMIN too, D5) | `PermissionDeniedException` — `Role <ROLE> is not allowed to create exercises` |
| | `INVALID_ARGUMENT` | §4 rules: `name: must not be blank`, `videoUrl: must be a valid http(s) URL`, `muscleGroupIds: must contain at least one muscle group`, `muscleGroupIds: unknown muscle group <id>`, size limits | `ConstraintViolationException` |
| `UpdateExercise` | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `PERMISSION_DENIED` | role ≠ TRAINER (R24) | `PermissionDeniedException` — `Role <ROLE> is not allowed to change exercises` |
| | `INVALID_ARGUMENT` | same rules as create, including an empty `muscle_group_ids` (R31, E22) | `ConstraintViolationException` |
| | `NOT_FOUND` | no such exercise | `ResourceNotFoundException("Exercise", id)` |
| | `PERMISSION_DENIED` | `owner_id IS NULL` (starter row, R26/E1) | `PermissionDeniedException` — `Exercise <id> belongs to the shared starter set and cannot be changed` |
| | `PERMISSION_DENIED` | `owner_id` is another trainer (R32/E21) | `PermissionDeniedException` — `You do not have access to exercise <id>` |
| `DeleteExercise` | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `PERMISSION_DENIED` | role ≠ TRAINER (R24) | `PermissionDeniedException` — `Role <ROLE> is not allowed to delete exercises` |
| | `NOT_FOUND` | no such exercise | `ResourceNotFoundException("Exercise", id)` |
| | `PERMISSION_DENIED` | starter row (R27/E2) | `PermissionDeniedException` — `Exercise <id> belongs to the shared starter set and cannot be deleted` |
| | `PERMISSION_DENIED` | another trainer's (R36/E21) | `PermissionDeniedException` — `You do not have access to exercise <id>` |
| | `FAILED_PRECONDITION` | any `workout_exercises` row references it (R34/E5) | `ExerciseInUseException` — `Exercise <id> is used by a workout and cannot be deleted` |
| `CreateWorkoutExercise` (Inc 2) | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `NOT_FOUND` | workout missing (existing) | `ResourceNotFoundException("Workout", id)` |
| | `PERMISSION_DENIED` | caller is not `workout.trainingPlan.trainer` (R18/E19; CLIENT and ADMIN always fall here) | `PermissionDeniedException` — `You do not have access to workout <workout_id>` |
| | `NOT_FOUND` | exercise missing (existing) | `ResourceNotFoundException("Exercise", id)` |
| | `PERMISSION_DENIED` | exercise `owner_id` not null and ≠ caller (R17/E18) | `PermissionDeniedException` — `You do not have access to exercise <exercise_id>` |
| `CreateWorkoutWithExercises` (Inc 2) | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `NOT_FOUND` | plan missing (existing) | `ResourceNotFoundException("TrainingPlan", id)` |
| | `PERMISSION_DENIED` | caller is not the plan's trainer (R18) | `PermissionDeniedException` — `You do not have access to training plan <training_plan_id>` |
| | `INVALID_ARGUMENT` | any `exercise_id` does not exist (existing generic message, unchanged) | `ConstraintViolationException` |
| | `PERMISSION_DENIED` | any referenced exercise is private to another trainer (R17) | `PermissionDeniedException` — `You do not have access to exercise <exercise_id>` (first offender, ascending id) |
| `ReplaceWorkoutExercises` (Inc 2) | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `NOT_FOUND` | workout missing (existing) | `ResourceNotFoundException("Workout", id)` |
| | `PERMISSION_DENIED` | caller is not the workout's plan trainer (R18) | `PermissionDeniedException` — `You do not have access to workout <workout_id>` |
| | `INVALID_ARGUMENT` / `PERMISSION_DENIED` | as `CreateWorkoutWithExercises` for the exercise list | as above |
| | `FAILED_PRECONDITION` | recorded data (existing, unchanged) | `WorkoutExerciseHasRecordedDataException` |
| `CloneWorkout` (Inc 2) | `UNAUTHENTICATED` | no identity | `UnauthenticatedException` |
| | `NOT_FOUND` | source workout / target plan missing (existing) | `ResourceNotFoundException` |
| | `PERMISSION_DENIED` | source workout's plan trainer ≠ caller (R19/E20) | `PermissionDeniedException` — `You do not have access to workout <source_workout_id>` |
| | `PERMISSION_DENIED` | target plan's trainer ≠ caller (R19/E20) | `PermissionDeniedException` — `You do not have access to training plan <target_training_plan_id>` |

Note on the brief: for `CreateWorkoutWithExercises`/`ReplaceWorkoutExercises` the brief says
"existing `NOT_FOUND` for a non-existent exercise stays"; the existing behavior for those two RPCs
is the generic `INVALID_ARGUMENT` `exercise_id: one or more referenced exercises do not exist`
(`create-workout-with-exercises/spec.md` §0/F10), and that is what stays. Only
`CreateWorkoutExercise` returns `NOT_FOUND` for a missing exercise, as it does today.

## 3. Data model and migrations

Highest existing migration: `V21`. This feature adds `V22`, `V23`, `V24`, in that order; each ships
in the same PR as the entity change it requires (`ddl-auto=validate`), and PR merge order must
equal version order (Flyway `outOfOrder` is off).

### V22 — `V22__create_muscle_groups_and_exercise_ownership.sql` (PR3, schema + reference data)

```sql
CREATE TABLE muscle_groups (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(50) NOT NULL,
    CONSTRAINT uq_muscle_groups_name UNIQUE (name)
);

-- R8 launch groups, in R8 order; a fresh identity column yields ids 1..14 in this order.
INSERT INTO muscle_groups (name) VALUES
    ('Peito'), ('Costas'), ('Ombros'), ('Bíceps'), ('Tríceps'), ('Antebraço'), ('Quadríceps'),
    ('Posteriores de coxa'), ('Glúteos'), ('Panturrilhas'), ('Abdômen'), ('Lombar'),
    ('Trapézio'), ('Cardio');

CREATE TABLE exercise_muscle_groups (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exercise_id      BIGINT NOT NULL,
    muscle_group_id  BIGINT NOT NULL,
    is_primary       BOOLEAN NOT NULL DEFAULT FALSE,
    catalog_order    INTEGER,
    CONSTRAINT fk_exercise_muscle_groups_exercise FOREIGN KEY (exercise_id) REFERENCES exercises (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_muscle_groups_muscle_group FOREIGN KEY (muscle_group_id) REFERENCES muscle_groups (id),
    CONSTRAINT uq_exercise_muscle_groups_exercise_group UNIQUE (exercise_id, muscle_group_id),
    CONSTRAINT ck_exercise_muscle_groups_order_only_primary CHECK (catalog_order IS NULL OR is_primary)
);
CREATE UNIQUE INDEX uq_exercise_muscle_groups_one_primary ON exercise_muscle_groups (exercise_id) WHERE is_primary;
CREATE INDEX idx_exercise_muscle_groups_group_order ON exercise_muscle_groups (muscle_group_id, is_primary, catalog_order);

-- NULL = shared starter set (R13); a user id = private to that trainer (R14).
ALTER TABLE exercises ADD COLUMN owner_id BIGINT REFERENCES users (id);
CREATE INDEX idx_exercises_owner ON exercises (owner_id);

-- V20's single enum column is superseded by the join table (D6). Pre-starter rows lose their
-- throwaway heuristic group here; V23 removes or refiles them (R51, R59).
ALTER TABLE exercises DROP COLUMN muscle_group;
```

Data-changing: seeds 14 reference rows, drops one column (its content was V20's throwaway
backfill). Reversible only by hand. Entity mapping:

- `plan/exercise/MuscleGroup` — the **enum is deleted** and the name reused for the JPA entity
  (`@Entity @Table("muscle_groups")`: `id`, `name`), plus `MuscleGroupRepository`
  (`findAllByOrderByIdAsc()`).
- `plan/exercise/ExerciseMuscleGroup` — `@Entity @Table("exercise_muscle_groups")`: `id`,
  `@ManyToOne(LAZY) exercise`, `@ManyToOne(LAZY) muscleGroup`, `isPrimary`, `catalogOrder`
  (nullable `Integer`).
- `plan/exercise/Exercise` — remove `muscleGroup`; add `@ManyToOne(fetch = LAZY)
  @JoinColumn(name = "owner_id") User owner` (nullable) and `@OneToMany(mappedBy = "exercise",
  cascade = ALL, orphanRemoval = true) List<ExerciseMuscleGroup> muscleGroups` (`@ToString.Exclude`
  / `@EqualsAndHashCode.Exclude`, same as `Workout#workoutExercises`). `isStarter()` =
  `owner == null`.

### V23 — `V23__remove_pre_starter_exercises.sql` (PR4, one-way door, no entity change)

Runs after V22 (groups exist for R59) and before V24 (so at V23 time *every* row in `exercises`
is pre-starter — that is how the migration identifies them; no timestamp column exists or is
added). Structure, top to bottom:

1. `CREATE TEMP TABLE keep_list (exercise_id BIGINT NOT NULL, muscle_group_name VARCHAR(50) NOT
   NULL, is_primary BOOLEAN NOT NULL) ON COMMIT DROP;` followed by the marker comment line
   `-- @keep-list` and the R56/R59 literal: one `INSERT INTO keep_list VALUES (<id>, '<group>',
   <is_primary>)` per (kept exercise, launch group), exactly one `is_primary = true` row per
   kept id. **Shipped empty** (D8; only the commented example remains). Group names must match
   `muscle_groups.name` — the migration `RAISE`s if any name in `keep_list` does not resolve, so a
   typo fails the migration instead of silently dropping a group.
2. Ownership of kept rows (R57): for each distinct kept `exercise_id`, the set of trainers whose
   workouts use it = `SELECT DISTINCT we.exercise_id, tp.trainer_id FROM workout_exercises we JOIN
   workouts w ON w.id = we.workout_id JOIN training_plans tp ON tp.id = w.training_plan_id WHERE
   we.exercise_id IN (SELECT exercise_id FROM keep_list)`. The lowest `trainer_id` keeps the
   original row: `UPDATE exercises SET owner_id = <that trainer>`. A kept exercise no workout uses
   has no trainer to belong to and is treated as not kept (R56's premise — genuine history — is
   workout usage); it is removed in step 5.
3. Per-trainer copies (R58, E23), in a `DO $$ ... $$` PL/pgSQL block looping over the remaining
   (exercise, trainer) pairs: `INSERT INTO exercises (name, description, video_url, owner_id)
   SELECT name, description, video_url, <trainer> FROM exercises WHERE id = <source> RETURNING id
   INTO new_id;` then `UPDATE workout_exercises SET exercise_id = new_id WHERE exercise_id =
   <source> AND workout_id IN (SELECT w.id FROM workouts w JOIN training_plans tp ON tp.id =
   w.training_plan_id WHERE tp.trainer_id = <trainer>)`. `exercise_sets` and `set_logs` hang off
   `workout_exercises.id`, which does not change, so every logged weight and rep follows the copy
   with its entry (E23) and `workout_logs`/`workout_feedback` are never touched (R54).
4. Groups for kept rows and their copies (R59): `INSERT INTO exercise_muscle_groups (exercise_id,
   muscle_group_id, is_primary, catalog_order) SELECT e.id, mg.id, k.is_primary, NULL FROM
   keep_list k JOIN muscle_groups mg ON mg.name = k.muscle_group_name JOIN exercises e ON e.id =
   k.exercise_id OR e.id IN (<copies of k.exercise_id>)` — copies are tracked in a temp table
   `copies (source_exercise_id, new_exercise_id)` filled by step 3. `catalog_order` is `NULL` for
   private rows (only starter rows are ordered, R49).
5. Removal (R51–R53, E12), strictly in this order, keyed on "not kept" = `owner_id IS NULL` at
   this point (every kept row and copy has an owner after steps 2–3):
   `DELETE FROM set_logs WHERE exercise_set_id IN (SELECT es.id FROM exercise_sets es JOIN
   workout_exercises we ON we.id = es.workout_exercise_id JOIN exercises e ON e.id = we.exercise_id
   WHERE e.owner_id IS NULL);` → `DELETE FROM exercise_sets WHERE workout_exercise_id IN (SELECT
   we.id FROM workout_exercises we JOIN exercises e ON e.id = we.exercise_id WHERE e.owner_id IS
   NULL);` → `DELETE FROM workout_exercises WHERE exercise_id IN (SELECT id FROM exercises WHERE
   owner_id IS NULL);` → `DELETE FROM exercises WHERE owner_id IS NULL;`. Nothing else is
   deleted; `workout_logs`, `workout_feedback`, `workouts`, `training_plans` are not named in the
   file.

Data-changing and **irreversible** (F28: no undo migrations exist; deleted `set_logs` are gone).
The DB has real-history rows only if the platform team says so before merge (assessment Q2 says
none). `PreStarterCleanupMigrationTest` (§7) executes the file's SQL, with a test-supplied
`keep_list`, inside a rolled-back transaction against the migrated schema — so the routine is
proven even though the shipped literal is empty. Deploy constraint: none beyond ordering (no
entity change), but it must never be applied to a database that already has V24's starter rows
(Flyway's version order guarantees that on a fresh or sequentially-migrated database).

### V24 — `V24__seed_starter_exercises.sql` (PR5, data only, no entity change)

Mapping from PRD §10 to rows, mechanically:

- A temp staging table `starter_seed (primary_group VARCHAR(50), catalog_order INTEGER, name
  VARCHAR(255), secondary_groups VARCHAR(50)[]) ON COMMIT DROP` receives one row per PRD §10 table
  row, section by section in R8 order: `primary_group` = the `###` section heading the row sits
  under, `catalog_order` = the row's `#` (1-based position within that section), `name` = the
  `Exercise` column verbatim (diacritics included), `secondary_groups` = the `Groups` column with
  the section's own group removed (verified: the first entry of every `Groups` cell is the
  section's group, and all 199 names are distinct, so `name` is a safe join key below).
- `INSERT INTO exercises (name, description, video_url, owner_id) SELECT name, NULL, NULL, NULL
  FROM starter_seed ORDER BY primary_group_id, catalog_order` — R2 (name), R4 (no description),
  R5 (no video), R13 (`owner_id IS NULL`).
- Primary link: `INSERT INTO exercise_muscle_groups (exercise_id, muscle_group_id, is_primary,
  catalog_order) SELECT e.id, mg.id, TRUE, s.catalog_order FROM starter_seed s JOIN exercises e ON
  e.name = s.name AND e.owner_id IS NULL JOIN muscle_groups mg ON mg.name = s.primary_group` —
  the `owner_id IS NULL` join guard is what keeps a V23-kept private exercise that happens to
  share a starter name (R44) from receiving starter links.
- Secondary links: same, `FROM starter_seed s CROSS JOIN LATERAL unnest(s.secondary_groups) g
  JOIN muscle_groups mg ON mg.name = g`, with `is_primary = FALSE, catalog_order = NULL` (R11,
  R50).
- Sanity assertions at the end (`DO $$ ... RAISE EXCEPTION ... $$`): `SELECT count(*) FROM
  exercises WHERE owner_id IS NULL` = **199**; `SELECT count(*) FROM muscle_groups` = **14**;
  `SELECT count(*) FROM exercise_muscle_groups emg JOIN exercises e ON e.id = emg.exercise_id
  WHERE e.owner_id IS NULL` = **283** (199 primary + 84 secondary — computed from PRD §10: 122
  exercises carry one group, 70 carry two, 7 carry three); each starter exercise has exactly one
  primary link with a non-null `catalog_order`.

Expected primary-row count per group (= rows in each PRD §10 section): Peito 20, Costas 22, Ombros
20, Bíceps 16, Tríceps 16, Antebraço 9, Quadríceps 20, Posteriores de coxa 13, Glúteos 14,
Panturrilhas 8, Abdômen 17, Lombar 7, Trapézio 7, Cardio 10. Expected total links per group
(primary + secondary): Peito 24, Costas 23, Ombros 27, Bíceps 21, Tríceps 30, Antebraço 13,
Quadríceps 23, Posteriores de coxa 18, Glúteos 37, Panturrilhas 8, Abdômen 19, Lombar 19,
Trapézio 11, Cardio 10.

Data-changing; reversible by deleting `exercises WHERE owner_id IS NULL` (nothing references
starter rows until a trainer uses one). Deploy constraint: V22–V24 and PR3's code ship in the same
deploy (F27); V23/V24 need no code of their own but must be applied before PR6–PR8's behavior is
meaningful.

### Properties (PR1)

`application.properties`: remove `spring.security.oauth2.resourceserver.jwt.issuer-uri`; add
`vertice.jwt.secret=${JWT_SECRET:dev-secret-change-me}`. No profile-specific override.

## 4. Validation rules

Controller step = the private `record` + `GrpcRequestValidator`, plus a direct check where a
Bean Validation message cannot carry the pinned wording (same as `requireMuscleGroup` today).
Service step = needs repository access or the caller identity.

| Field / rule | Constraint | On violation (status, description) | PRD rule | Where |
|---|---|---|---|---|
| `ExerciseRequest.name` | `@NotBlank @Size(max = 255)` | `INVALID_ARGUMENT` `name: must not be blank` / `name: size must be between 0 and 255` | R2, R39 | controller |
| `ExerciseRequest.description` | `@Size(max = 255)` (blank → `null` column, as today) | `INVALID_ARGUMENT` `description: size must be between 0 and 255` | R41 | controller |
| `ExerciseRequest.video_url` | `@Pattern("^$|^https?://\\S+$") @Size(max = 500)` (existing) | `INVALID_ARGUMENT` `videoUrl: must be a valid http(s) URL` | R42 | controller |
| `ExerciseRequest.muscle_group_ids` | de-duplicate (order preserved), then at least one | `INVALID_ARGUMENT` `muscleGroupIds: must contain at least one muscle group` | R31, R40, E13, E22 | controller (direct check) |
| `ExerciseRequest.muscle_group_ids` | every id exists in `muscle_groups` | `INVALID_ARGUMENT` `muscleGroupIds: unknown muscle group <id>` (lowest offending id) | R3, R8 | service |
| `ExerciseRequest.muscle_group_ids` | duplicates allowed (collapsed) | — | R43 | controller |
| `ListExercisesRequest.search` | `@Size(max = 100)`; trimmed; empty = no filter | `INVALID_ARGUMENT` `search: size must be between 0 and 100` | R46 | controller |
| `ListExercisesRequest.muscle_group_id` | `0` = no filter; otherwise must exist | `NOT_FOUND` `MuscleGroup with id <id> not found` | R45 | service |
| identity required | `ListExercises`, `GetExercise`, `Create/Update/DeleteExercise`, and the four Increment 2 RPCs | `UNAUTHENTICATED` `Caller identity required` | R14–R24 | controller (`CallerIdentityResolver#require`) |
| role | `ListExercises`: TRAINER or ADMIN; `Create/Update/Delete`: TRAINER | `PERMISSION_DENIED` `Role <ROLE> is not allowed to <list/create/change/delete> exercises` | R21, R23, R24, E16, E17 | service (`CallerIdentity#requireRole`) |
| ownership | see §5 | `PERMISSION_DENIED` `You do not have access to exercise <id>` | R16, R32, R36, E18, E21 | service |
| starter immutability | `owner_id IS NULL` on update/delete | `PERMISSION_DENIED` `Exercise <id> belongs to the shared starter set and cannot be changed` / `... deleted` | R26, R27, E1, E2 | service |
| in use | no `workout_exercises` row references it | `FAILED_PRECONDITION` `Exercise <id> is used by a workout and cannot be deleted` | R34, R35, E5 | service |
| name uniqueness | none (no constraint added) | — | R44, E3, E4 | — |
| Increment 2 plan ownership | `plan.trainer.id == caller.userId` | `PERMISSION_DENIED` `You do not have access to workout <id>` / `... training plan <id>` | R18, R19, E19, E20 | service |
| Increment 2 exercise visibility | `owner_id IS NULL OR owner_id == caller.userId` for every referenced exercise | `PERMISSION_DENIED` `You do not have access to exercise <id>` | R17, E18 | service |

Existing validations on the Increment 2 RPCs (`order`, caps, set fields, `day_of_week`, `name`)
run where they run today; the new checks run in the service after the existing controller-level
record validation and before any write (brief: identity → existence → ownership → exercise
existence → visibility → existing validations — the controller-level Bean Validation on the
request shape necessarily still runs first, before the service is entered, as it does today).

## 5. Behavior

Identity is resolved in the controller (`callerIdentityResolver.require()`) and passed to the
service as `CallerIdentity(Long userId, Role role)`. `CallerIdentity#requireRole(Role...)` throws
`PermissionDeniedException`. `Exercise#isVisibleTo(CallerIdentity)` = `owner == null || (role ==
TRAINER && owner.id == userId)` — the one predicate every check below reuses.

### 5.0 Identity resolution (Increment 0)

1. Non-local: `GrpcSecurityConfig#grpcAuthenticationInterceptor` keeps `allRequests()
   .authenticated()` + `oauth2ResourceServer(jwt)`; the `JwtDecoder` it uses is now the
   `HmacJwtDecoder` bean (§6) — Spring Boot's own decoder auto-config backs off because a
   `JwtDecoder` bean exists. Missing or unverifiable token → `UNAUTHENTICATED` from the
   interceptor, as today (`GrpcHealthCheckTest#check_withoutAuth_returnsUnauthenticated` stays
   green).
2. Local: `localGrpcAuthenticationInterceptor` becomes `allRequests().permitAll()` **plus**
   `oauth2ResourceServer(jwt)`: a present token is decoded into a `JwtAuthenticationToken`; an
   absent token leaves the call anonymous and permitted. (If `GrpcSecurity` cannot express
   permit-all-with-optional-bearer, the fallback is a `@Profile("local") @GlobalServerInterceptor`
   that decodes the `authorization` metadata when present and binds the `SecurityContext` itself —
   the tests in §7 are the same either way.)
3. `CallerIdentityResolver#current()` reads `SecurityContextHolder.getContext()
   .getAuthentication()`; when it is a `JwtAuthenticationToken`, returns `CallerIdentity(claim
   "id" as long, Role.valueOf(claim "role"))`; anonymous, missing claim, non-numeric `id` or
   unknown `role` → `Optional.empty()`. `require()` = `current().orElseThrow(
   UnauthenticatedException::new)`.
4. REST (`SecurityConfig`, `LocalSecurityConfig`) is untouched apart from the decoder bean it now
   picks up; Actuator needs no identity.

### 5.1 `ListMuscleGroups`

1. No identity check. 2. `muscleGroupRepository.findAllByOrderByIdAsc()`. 3. Map to
`MuscleGroupResponse` in that order (R8 order = id order). Read-only transaction.

### 5.2 `ListExercises` (requires identity)

1. `require()` identity; `requireRole(TRAINER, ADMIN)` (CLIENT → `PERMISSION_DENIED`, R21/E16).
2. Controller validates `search` (≤ 100); trims it.
3. Service: if `muscle_group_id ≠ 0` and `muscleGroupRepository.existsById` is false →
   `ResourceNotFoundException("MuscleGroup", id)`.
4. Escape `\`, `%`, `_` in `search`; run `ExerciseRepository#findVisible(ownerId, muscleGroupId,
   search)` — one native query (the ordering needs a correlated `catalog_order` in `ORDER BY`,
   which JPQL + `DISTINCT` cannot express on Postgres):
   ```sql
   SELECT e.* FROM exercises e
   WHERE (e.owner_id IS NULL OR e.owner_id = :ownerId)
     AND (:muscleGroupId = 0 OR EXISTS (SELECT 1 FROM exercise_muscle_groups g
                                        WHERE g.exercise_id = e.id AND g.muscle_group_id = :muscleGroupId))
     AND (:search = '' OR e.name ILIKE '%' || :search || '%' ESCAPE '\')
   ORDER BY (e.owner_id IS NULL) ASC,                                   -- own rows first (R48)
            CASE WHEN e.owner_id IS NULL AND :muscleGroupId <> 0
                 THEN (SELECT g.catalog_order FROM exercise_muscle_groups g
                       WHERE g.exercise_id = e.id AND g.muscle_group_id = :muscleGroupId AND g.is_primary)
            END ASC NULLS LAST,                                         -- primary-filed starter rows in PRD order (R49)
            e.name ASC                                                  -- own rows, secondary-group starter rows (R50), unfiltered starter rows
   ```
   `ownerId` is the caller's id for TRAINER **and** ADMIN: an ADMIN never owns an exercise
   (`CreateExercise` is TRAINER-only; V23 assigns owners from `training_plans.trainer_id`, which
   `TrainingPlanService` restricts to TRAINER users), so the predicate collapses to starter-only
   for ADMIN (R25) without a second query. `0`/`''` stand in for "no filter" rather than `NULL`
   because Postgres cannot type a bare `:param IS NULL`.
5. Map each row with `muscle_groups` primary-first-then-by-id and `is_starter`. Read-only
   transaction. Result: own (name asc) → starter primary-in-filter (`catalog_order` asc) →
   starter secondary-in-filter (name asc); without a filter, own (name asc) → starter (name asc).
   E8: Lombar returns "Levantamento terra com barra" among the secondary rows. E14/E15: another
   trainer's rows never match; the search ignores the filter unless both are given.

### 5.3 `GetExercise` (requires identity)

1. `require()` identity. 2. `findById` or `ResourceNotFoundException("Exercise", id)` (existence
before visibility, so a refused caller gets `PERMISSION_DENIED` only for a real row — D2's
accepted disclosure). 3. Visibility: TRAINER/ADMIN → `isVisibleTo(caller)`; CLIENT →
`workoutExerciseRepository.existsByExerciseIdAndWorkout_TrainingPlan_Client_Id(id, caller.userId)`
(R20, R22, E7, E16). Not visible → `PermissionDeniedException.noAccess("exercise", id)`. 4. Map.

### 5.4 `CreateExercise` (requires identity)

1. `require()`; `requireRole(TRAINER)` (R23/E17; ADMIN refused too, D5).
2. Controller: validate the record; de-duplicate `muscle_group_ids` preserving order; empty →
   `INVALID_ARGUMENT` (R40/E13).
3. Service: `muscleGroupRepository.findAllById(ids)`; any missing → `INVALID_ARGUMENT`
   `muscleGroupIds: unknown muscle group <id>`.
4. Build `Exercise` (mapper, scalars), `owner = userRepository.getReferenceById(caller.userId)`,
   one `ExerciseMuscleGroup` per id in request order, **every one `is_primary = false`** and
   `catalog_order = NULL` — a trainer-created exercise has no primary group (owner decision,
   2026-09-23; primary exists only to place starter rows in the PRD §10 catalog order). Save once
   (cascade). Duplicate names are not checked (R44/E3/E4).
5. Respond with `is_starter = false`, groups primary-first-then-by-id — i.e. id order, since none
   is primary.

### 5.5 `UpdateExercise` (requires identity)

1. `require()`; `requireRole(TRAINER)` (R24). 2. Controller validation as create (R31/E22 for an
empty list). 3. `findById` or `NOT_FOUND`. 4. `owner == null` → `PermissionDeniedException
.starterSet(id, "changed")` (R26/E1). 5. `owner.id ≠ caller` → `noAccess("exercise", id)`
(R32/E21). 6. Resolve group ids (unknown → `INVALID_ARGUMENT`). 7. Replace name, description,
video_url (mapper `updateEntityFromRequest`) and the whole `muscleGroups` collection (clear +
re-add, none primary; `orphanRemoval` deletes the old rows) — D11/R29/R30. 8. Save; respond.
R33/E6 hold because every `workout_exercises` row points at this same id.

### 5.6 `DeleteExercise` (requires identity)

1. `require()`; `requireRole(TRAINER)` (R24). 2. `findById` or `NOT_FOUND`. 3. `owner == null` →
`starterSet(id, "deleted")` (R27/E2). 4. `owner.id ≠ caller` → `noAccess` (R36/E21). 5.
`workoutExerciseRepository.existsByExerciseId(id)` → `ExerciseInUseException(id)` (R34/E5). 6.
`delete` — the join rows go with it (JPA cascade; the DB `ON DELETE CASCADE` is the backstop).
R35.

### 5.7 Increment 2 — workout-side guards

All four run inside the existing `@Transactional` service methods; every new check precedes the
first write. Controllers gain only the `require()` call and the extra `CallerIdentity` argument.

**`WorkoutExerciseService#createWorkoutExercise(caller, request)`**
1. Workout `findById` or `NOT_FOUND` (existing). 2. `workout.trainingPlan.trainer.id ≠
caller.userId` → `noAccess("workout", workoutId)` (R18/E19; a CLIENT or ADMIN caller always fails
here). 3. Exercise `findById` or `NOT_FOUND` (existing). 4. `!exercise.isVisibleTo(caller)` →
`noAccess("exercise", exerciseId)` (R17/E18). 5. Existing build/save.

**`WorkoutService#createWorkoutWithExercises(caller, request)`**
1. Plan `findById` or `NOT_FOUND` (existing). 2. `plan.trainer.id ≠ caller.userId` →
`noAccess("training plan", planId)`. 3. `buildWorkoutExerciseTree` (existing batch existence
check → generic `INVALID_ARGUMENT`), then — new, inside the same helper after the existence check —
the first exercise (ascending id) with `!isVisibleTo(caller)` → `noAccess("exercise", id)`. 4.
Existing save.

**`WorkoutService#replaceWorkoutExercises(caller, request)`**
1. Workout `findById` or `NOT_FOUND`. 2. Plan-trainer check → `noAccess("workout", workoutId)`.
3. Existing R12 recorded-data check, then the tree build with the visibility check as above. Order
between the R12 check and the exercise checks: existence/visibility first (they are about the
request), R12 second (it is about current state) — a refused request never reaches the delete.

**`WorkoutService#cloneWorkout(caller, request)`**
1. Source `findById` or `NOT_FOUND`; target plan `findById` or `NOT_FOUND` (existing). 2.
`source.trainingPlan.trainer.id ≠ caller` → `noAccess("workout", sourceWorkoutId)`. 3.
`targetPlan.trainer.id ≠ caller` → `noAccess("training plan", targetTrainingPlanId)` (R19/E20).
4. Existing deep copy. No per-exercise check: every exercise on the source is either starter or
the source trainer's own (R17 held when it was added; the V23 routine repoints entries to the
plan trainer's copy), and step 2 proved the caller is that trainer.

`UpdateWorkoutExercise` needs no guard: `exercise_id` is immutable after creation
(`workout-exercise-crud/spec.md` §0), so it cannot introduce a foreign exercise.

## 6. Mapping and exceptions

**`config/HmacJwtDecoder`** (PR1) — `implements JwtDecoder`. `decode(token)`: split on `.` into
exactly three parts; header JSON must have `"alg": "HS256"` (anything else, including `none`,
→ `BadJwtException`); `Mac.getInstance("HmacSHA256")` keyed with the secret's UTF-8 bytes over
`header.payload`; compare with `MessageDigest.isEqual` against the base64url-decoded signature;
parse the payload with Jackson; convert `exp`/`iat`/`nbf` to `Instant` the way `NimbusJwtDecoder`
does (`MappedJwtClaimSetConverter.withDefaults(Map.of())`); reject when `exp` is present and in
the past (`JwtValidators.createDefault()` — `JwtTimestampValidator`); return
`Jwt.withTokenValue(token).headers(h -> h.putAll(header)).claims(c -> c.putAll(converted))
.build()`. A token without `exp` (the verification doc's one-liner mints one) is accepted.
Bean `jwtDecoder(@Value("${vertice.jwt.secret}") String secret)` in `config/JwtDecoderConfig`.
**`config/JwtSecretGuard`** — `@Component @Profile("!local")`, `@PostConstruct` throws
`IllegalStateException("JWT_SECRET must be at least 32 bytes")` when shorter.

**`grpc/CallerIdentity`** (PR2) — `record CallerIdentity(Long userId, Role role)` with
`requireRole(Role... allowed)` → `PermissionDeniedException.role(role, action)`; the `action`
string is supplied by the caller (`"list exercises"`, `"create exercises"`, …).
**`grpc/CallerIdentityResolver`** — `@Component`, `Optional<CallerIdentity> current()`,
`CallerIdentity require()`.

**`common/exception/`** (new; each in both handlers per CLAUDE.md, F20):

| Exception | gRPC (`GrpcExceptionAdvice`) | REST (`GlobalExceptionHandler`) | PR |
|---|---|---|---|
| `UnauthenticatedException()` — `Caller identity required` | `UNAUTHENTICATED` | `401 UNAUTHORIZED` | PR2 |
| `PermissionDeniedException(String)` with factories `noAccess(resource, id)` → `You do not have access to <resource> <id>`; `starterSet(id, verb)` → `Exercise <id> belongs to the shared starter set and cannot be <verb>`; `role(role, action)` → `Role <ROLE> is not allowed to <action>` | `PERMISSION_DENIED` | `403 FORBIDDEN` | PR2 |
| `ExerciseInUseException(id)` — `Exercise <id> is used by a workout and cannot be deleted` | `FAILED_PRECONDITION` | `412 PRECONDITION_FAILED` | PR7 |

`GrpcExceptionAdvice#handleValidation` (PR3): if `getConstraintViolations()` is empty, use
`ex.getMessage()` as the description.

**`ExerciseMapper`** (PR3) — stays MapStruct, `uses = ProtoStrings.class`:
- `toResponse(Exercise)`: scalars as today (`description`/`videoUrl` via `nullToEmpty`);
  `isStarter` via `expression = "java(exercise.getOwner() == null)"`; the repeated
  `muscle_groups` is filled in an `@AfterMapping` on `ExerciseResponse.Builder`
  (`addAllMuscleGroups`) from `exercise.getMuscleGroups()` sorted primary-first then by
  `muscleGroup.id`, each via `toMuscleGroupResponse(MuscleGroup)`.
- `toMuscleGroupResponse(MuscleGroup)` → `MuscleGroupResponse`.
- `toEntity(ExerciseRequest)` / `updateEntityFromRequest`: `@Mapping(target = "id", ignore =
  true)`, `owner` and `muscleGroups` ignored (the service sets them).
- The `mapMuscleGroup` `@ValueMapping` method is deleted with the enum.

Repositories (PR3 unless noted): `MuscleGroupRepository#findAllByOrderByIdAsc`;
`ExerciseRepository#findVisible(Long ownerId, long muscleGroupId, String search)` (PR8, native
query above); `WorkoutExerciseRepository#existsByExerciseId(Long)` (PR7) and
`#existsByExerciseIdAndWorkout_TrainingPlan_Client_Id(Long, Long)` (PR6).

## 7. Testing strategy

Ports: `19104` `GrpcCallerIdentityTest`, `19105` `ExerciseCatalogRepositoryTest`, `19106`
`PreStarterCleanupMigrationTest` (19090–19103 in use except an unused gap at 19093, left alone).
`src/test/java/com/vertice/api/grpc/TestJwts` (PR1) signs HS256 tokens with
`dev-secret-change-me` for tests: `TestJwts.token(long userId, Role role)`.

**PR1 — `HmacJwtDecoderTest`** (unit): `decode_validHs256Token_returnsClaims` (D1),
`decode_wrongSecret_throwsBadJwt`, `decode_algNone_throwsBadJwt`, `decode_expiredToken_throwsBadJwt`,
`decode_tokenWithoutExp_isAccepted`, `decode_shortSecret_stillVerifies` (the Nimbus floor is the
reason this class exists). **`JwtSecretGuardTest`**: `shortSecret_fails`, `longSecret_passes`.
**`GrpcHealthCheckTest`** (non-local, existing): `check_withoutAuth_returnsUnauthenticated`
(unchanged) + new `check_withSharedSecretToken_returnsServing` (proves the non-local interceptor
verifies with the shared secret). **`GrpcHealthCheckLocalProfileTest`**: unchanged, must still
pass without a token.

**PR2 — `CallerIdentityResolverTest`** (unit): `require_withJwtAuthentication_returnsUserIdAndRole`,
`require_withoutAuthentication_throwsUnauthenticated`, `require_withMissingIdClaim_throwsUnauthenticated`,
`require_withUnknownRole_throwsUnauthenticated`, `current_withoutAuthentication_isEmpty`.
**`GrpcCallerIdentityTest`** (`@SpringBootTest`, `local`, port 19104, a test-only service built
like `GrpcExceptionMappingTest`'s with two methods — one calling `require()`, one not):
`call_withBearerToken_resolvesIdentity`, `call_withoutToken_requireFailsUnauthenticated`,
`call_withoutToken_rpcNotRequiringIdentitySucceeds`, `call_withInvalidToken_failsUnauthenticated`
(D1 local semantics). **`GrpcExceptionMappingTest`**: `unauthenticatedException_mapsToUnauthenticated`,
`permissionDeniedException_mapsToPermissionDenied` (D2).

**PR3 — `ExerciseServiceTest`** (rewritten around the new mapper/entities):
`createExercise_savesGroupsWithNoPrimary` (R39, R43), `createExercise_deduplicatesGroupIds`
(R43), `createExercise_unknownGroup_throwsInvalidArgumentNamingId` (R3), `updateExercise_replacesGroups`
(D11), `getExercise_starterRow_isStarterTrueGroupsPrimaryFirst` (D6), `listMuscleGroups_returnsIdOrder`
(R8), plus the existing `getExercise_withNullVideoUrl_returnsEmptyStringNotNull` /
`getExercise_withNullDescription_returnsEmptyStringNotNull` kept. **`ExerciseControllerTest`**:
existing scenarios adapted to `muscle_group_ids` (`createExercise_withValidRequest_returnsCreated`,
`createExercise_withBlankName_throwsInvalidArgument`, the four `video_url` ones,
`updateExercise_whenExists_returnsUpdated`, `*_whenMissing_throwsNotFound`,
`deleteExercise_whenExists_succeeds`); replaced: `createExercise_withoutMuscleGroupIds_throwsInvalidArgumentWithMessage`
(R40/E13 — asserts the description `muscleGroupIds: must contain at least one muscle group`),
`updateExercise_withoutMuscleGroupIds_throwsInvalidArgumentWithMessage` (R31/E22),
`createExercise_withDuplicateGroupIds_isAllowed`, `listMuscleGroups_returnsGroups`,
`createExercise_nameOver255_throwsInvalidArgument`. **`GrpcExceptionMappingTest`**:
`constraintViolationWithoutViolations_keepsMessageAsDescription` (the §0 baseline fix).
**`ExerciseCatalogRepositoryTest`** (`@SpringBootTest`, `local`, port 19105, real Postgres):
`muscleGroups_seededInLaunchOrder` (R8: 14 rows, ids 1..14, names in R8 order).

**PR4 — `PreStarterCleanupMigrationTest`** (`@SpringBootTest`, `local`, port 19106, real
Postgres; reads `db/migration/V23__remove_pre_starter_exercises.sql` from the classpath, inserts
the fixture's `INSERT INTO keep_list …` lines after the `-- @keep-list` marker, executes the whole
script through `JdbcTemplate` inside a `TransactionTemplate` that is always rolled back; the
fixture is two trainers T1/T2 with one plan and one workout each, exercises X-kept (used by both
workouts, with sets and set logs from a client session each, plus one feedback per session) and
X-drop (used by T1 only, with sets and logs), plus one kept-but-unused X-idle):
`unkeptExercise_removedWithEntriesSetsAndLogs` (R51–R53, E12), `sessionsAndFeedback_survive`
(R54, E23), `keptExerciseUsedByOneTrainer_becomesPrivateToThatTrainer` (R57),
`keptExerciseUsedByTwoTrainers_oneCopyPerTrainer_entriesAndLogsFollow` (R58, E23),
`keptExerciseGroups_filedFromKeepList_primaryFlagged_orderNull` (R59),
`keptButUnusedExercise_isRemoved` (§3 step 2), `unknownGroupNameInKeepList_failsMigration`,
`shippedKeepList_isEmpty` (D8: the file has no uncommented `INSERT INTO keep_list`), and
`starterRowsWouldNotSurviveIfPresent` is **not** written — the test never runs the script on top
of V24 rows; it asserts the script's `-- runs before V24` precondition by version number only.

**PR5 — `ExerciseCatalogRepositoryTest`** additions: `starterSet_has199ExercisesWithNullOwner`
(R1, R12, R13), `starterSet_has283LinksAcross14Groups` (R3, R11), `starterSet_everyExerciseHasExactlyOnePrimaryWithCatalogOrder`
(R49), `starterSet_noDescriptionOrVideo` (R4, R5), `starterSet_perGroupPrimaryCountsMatchPrd`
(the 14 numbers in §3), `starterSet_namesUnique`.

**PR6 — `ExerciseServiceTest`**: `listExercises_clientRefused` (R21/E16),
`listExercises_trainerPassesOwnId_adminPassesOwnId` (D5), `getExercise_missing_notFoundBeforeVisibility`
(D2), `getExercise_trainerOtherTrainers_permissionDenied` (R16/E18),
`getExercise_adminPrivate_permissionDenied` (R25), `getExercise_adminStarter_allowed`,
`getExercise_clientInOwnWorkout_allowed` (R20/E7), `getExercise_clientOutsideOwnWorkouts_permissionDenied`
(R22/E16), `createExercise_clientRefused` / `createExercise_adminRefused` (R23/E17, D5),
`createExercise_setsOwnerToCaller` (R14), `updateExercise_starter_permissionDeniedStarterMessage`
(R26/E1), `updateExercise_otherTrainers_permissionDenied` (R32/E21), `updateExercise_own_replacesAllFields`
(R29, R30), `updateExercise_clientRefused` (R24), `deleteExercise_starter_permissionDeniedStarterMessage`
(R27/E2), `deleteExercise_otherTrainers_permissionDenied` (R36/E21), `deleteExercise_clientRefused`
(R24/E17). **`ExerciseControllerTest`** (tokens via `TestJwts`): `listExercises_withoutToken_unauthenticated`,
`getExercise_withoutToken_unauthenticated`, `createExercise_withoutToken_unauthenticated`,
`listMuscleGroups_withoutToken_succeeds` (D5), `listExercises_clientToken_permissionDenied`,
`updateExercise_starter_permissionDeniedWithMessage`, `deleteExercise_otherTrainers_permissionDeniedWithMessage`,
`createExercise_passesCallerIdentityToService` (captures the `CallerIdentity` argument).

**PR7 — `ExerciseServiceTest`**: `deleteExercise_inUse_throwsExerciseInUse` (R34/E5),
`deleteExercise_unused_deletes` (R35), `deleteExercise_checksStarterAndOwnershipBeforeInUse`.
**`ExerciseControllerTest`**: `deleteExercise_inUse_failedPreconditionWithMessage`.
**`GrpcExceptionMappingTest`**: `exerciseInUseException_mapsToFailedPrecondition` (D4).

**PR8 — `ExerciseCatalogRepositoryTest`** additions (seeds two trainers' private rows on top of
the V24 data, cleans up in `@AfterEach`): `findVisible_ownFirstThenStarterByName` (R48),
`findVisible_groupFilter_primaryInCatalogOrderThenSecondaryByName` (R49, R50 — Peito: "Supino
reto com barra" first, "Supino inclinado com halteres" second; "Mergulho nas paralelas" and
"Supino fechado com barra" after every primary row), `findVisible_groupFilter_includesSecondaryGroupExercises`
(E8: Lombar contains "Levantamento terra com barra"; Lombar total 19), `findVisible_search_caseInsensitiveSubstring`
(R46, E15: `"SUPINO"` matches), `findVisible_search_wildcardsAreLiteral` (`"%"` matches nothing),
`findVisible_search_ignoresOtherTrainersRows` (E14), `findVisible_adminId_returnsStarterOnly`
(R25), `findVisible_noFilter_starterByName`, `findVisible_groupAndSearch_combined` (R45 + R46).
**`ExerciseServiceTest`**: `listExercises_unknownGroup_notFound`, `listExercises_escapesLikeWildcards`,
`listExercises_trimsSearch`. **`ExerciseControllerTest`**: `listExercises_searchOver100_invalidArgument`,
`listExercises_forwardsFilterAndSearch`.

**PR9 — `WorkoutExerciseServiceTest`**: `createWorkoutExercise_otherTrainersWorkout_permissionDenied`
(R18/E19), `createWorkoutExercise_otherTrainersExercise_permissionDenied` (R17/E18),
`createWorkoutExercise_starterExerciseOwnWorkout_succeeds`, `createWorkoutExercise_clientCaller_permissionDenied`,
`createWorkoutExercise_missingWorkout_notFoundBeforeOwnership`. **`WorkoutServiceTest`**:
`createWorkoutWithExercises_otherTrainersPlan_permissionDenied` (R18),
`createWorkoutWithExercises_otherTrainersExercise_permissionDenied` (R17),
`createWorkoutWithExercises_missingExercise_stillInvalidArgument` (must-not-change),
`replaceWorkoutExercises_otherTrainersWorkout_permissionDenied`, `replaceWorkoutExercises_otherTrainersExercise_permissionDeniedBeforeRecordedDataCheck`,
`cloneWorkout_sourceNotOwned_permissionDenied` (R19/E20), `cloneWorkout_targetPlanNotOwned_permissionDenied`
(R19/E20), `cloneWorkout_bothOwned_deepCopiesAsBefore`. **`WorkoutExerciseControllerTest`**:
`createWorkoutExercise_withoutToken_unauthenticated`, `updateWorkoutExercise_withoutToken_stillSucceeds`
(must-not-change). **`WorkoutControllerTest`**: `cloneWorkout_withoutToken_unauthenticated`,
`createWorkoutWithExercises_withoutToken_unauthenticated`, `replaceWorkoutExercises_withoutToken_unauthenticated`,
`createWorkout_withoutToken_stillSucceeds` (must-not-change).
**`ReplaceWorkoutExercisesIntegrationTest`**: now sends `TestJwts.token(trainer.id, TRAINER)` on
its `ReplaceWorkoutExercises` calls; scenarios unchanged.

No test for E10-style concurrency, pagination or metrics — not built (§0).

## 8. Rollout

- **Which profile production runs matters.** Today the non-local profile requires a JWT the BFF
  never sends, so either no non-local deployment exists or it runs `local`. Open question for the
  owner (report). Either way the order below is safe.
- **Increment 0** (PR1, PR2): deploy in any order relative to the BFF's Increment 0; both halves
  are harmless alone. After deploy: `grpcurl` health with and without a token per
  verification.md §1.1; the BFF keeps working unchanged.
- **Increment 1** (PR3–PR8): merge in PR order (V22 → V23 → V24). Deploy vertice-api once with
  all six merged, **then** vertice-bff, **then** vertice-web-react, in one window (D9). Precondition:
  the BFF forwards its JWT on every call (its Increment 0 on `main` and deployed) — otherwise
  PR6 turns every `ListExercises`/`GetExercise` into `UNAUTHENTICATED` → 401 → the web logs the
  user out. `JWT_SECRET` must be the same value in both deployments (and ≥ 32 bytes for
  vertice-api under `!local`, or it refuses to boot). After deploy: `flyway_schema_history` has
  22/23/24 succeeded; counts 199/14/283; `ListMuscleGroups` returns 14; `ListExercises` with a
  trainer token returns 199 + own; the BFF's `GET /exercises` returns the new shape.
- **Rollback**: PR1/PR2 revert cleanly (property + beans). PR3 is a schema change with
  `ddl-auto=validate`: rolling the code back requires rolling the schema back by hand (recreate
  `muscle_group`, drop the two tables and `owner_id`). **PR4 (V23) has no rollback**: deleted
  `exercises`/`workout_exercises`/`exercise_sets`/`set_logs` rows are gone (F28) — a one-way door
  the owner accepts on the basis of assessment Q2 (test data only). PR5 (V24) is reversible by
  deleting `owner_id IS NULL` rows while none is referenced. PR6–PR8 are code-only.
- **Increment 2** (PR9): deploy after Increment 1 is live everywhere; needs no BFF or web change
  (the BFF documents the new 403s). Revert = code revert.

## 9. Out of scope

- Platform-team surfaces (R25/R37/R38 management, browsing private exercises) — PRD §6.
- Videos/descriptions on starter exercises — PRD §6.
- Accent-insensitive search (`unaccent`) — brief; follow-up if trainers report misses.
- Pagination of `ListExercises` (F17), metrics (F22), a logging convention (F21), optimistic
  locking (F16) — baselines unchanged.
- Remapping exercises when the platform team renames/removes a group — PRD §6.
- Identity enforcement on every other RPC (plans, workouts CRUD, sets, sessions, feedback, users,
  trainer-clients) — the inherited gap stays outside the RPCs the PRD builds on (D10).
- ADMIN losing add/replace/clone on trainers' workouts and 403 on a full workout with a private
  exercise (D5/D10 consequence) — documented, not mitigated.
- Replacing the BFF-minted JWT with a real identity provider — D1 reuses it on purpose.
- A `CloneWorkout` UI, client-side web screens — web scope, not this repo.

## 10. Delivery plan

| PR | Title | Increment | Contains (spec §) | Depends on | Verified by |
|---|---|---|---|---|---|
| PR1 | Shared-secret HS256 `JwtDecoder`; local profile accepts an optional bearer token | 0 | §0 D1, §3 properties, §5.0 steps 1–2, 4, §6 decoder/guard | — | verification.md §1.1 |
| PR2 | `CallerIdentity` resolver under `grpc/`; `UnauthenticatedException`/`PermissionDeniedException` in both handlers | 0 | §5.0 step 3, §6 identity + exceptions | PR1 | verification.md §1.2 |
| PR3 | Muscle-group model: V22, entities, proto reshaping, `ListMuscleGroups`, CRUD on group ids | 1 | §2, §3 V22, §4 group rules, §5.1, §5.4–5.5 (shape only), §6 mapper + validation fallback | PR2 (build order only; uses nothing from it yet) | verification.md §1.3 |
| PR4 | Pre-starter cleanup migration V23 (one-way door) | 1 | §3 V23 | PR3 | verification.md §1.4 |
| PR5 | Starter-set seed migration V24 | 1 | §3 V24 | PR4 | verification.md §1.5 |
| PR6 | Exercise RPCs require identity; role, ownership and starter-set rules | 1 | §4 identity/role/ownership rows, §5.2 step 1 + scoping, §5.3–5.6 (minus in-use) | PR2, PR3; **BFF Increment 0 on `main` and deployed before this deploys** | verification.md §1.6 |
| PR7 | `DeleteExercise` refuses an exercise a workout uses | 1 | §5.6 step 5, §6 `ExerciseInUseException` | PR6 | verification.md §1.7 |
| PR8 | `ListExercises` group filter, name search and R47–R50 ordering | 1 | §5.2 steps 2–5, §6 `findVisible` | PR5, PR6 | verification.md §1.8 |
| PR9 | Workout-side guards R17–R19 on four RPCs | 2 | §5.7 | PR6; Increment 1 live on api, bff, web | verification.md §1.9 |

### PR1 — Shared-secret HS256 `JwtDecoder`; local profile accepts an optional bearer token
Branch: `feat/exercise-starter-catalog-jwt-decoder`. Scope: `config/HmacJwtDecoder`,
`config/JwtDecoderConfig`, `config/JwtSecretGuard`; `application.properties` (drop `issuer-uri`,
add `vertice.jwt.secret`); `GrpcSecurityConfig` local bean gains `oauth2ResourceServer(jwt)`;
`TestJwts` test helper; CLAUDE.md's "Authentication" paragraph updated (shared secret, optional
token under `local`). Nothing reads the identity yet. After merge, `main` still deploys: non-local
now verifies against the shared secret (today it verifies against an issuer nobody runs); local
behaves as before for token-less callers. Done when: verification.md §1.1 passes.

### PR2 — `CallerIdentity` resolver under `grpc/`
Branch: `feat/exercise-starter-catalog-caller-identity`. Scope: `grpc/CallerIdentity`,
`grpc/CallerIdentityResolver`, `common/exception/UnauthenticatedException`,
`common/exception/PermissionDeniedException`, entries in `GrpcExceptionAdvice` and
`GlobalExceptionHandler`, tests. No controller calls it yet. After merge, `main` deploys; no
behavior change. Done when: verification.md §1.2 passes.

### PR3 — Muscle-group model
Branch: `feat/exercise-starter-catalog-muscle-groups`. Scope: V22; `MuscleGroup` entity (enum
deleted), `ExerciseMuscleGroup`, `Exercise` changes, `MuscleGroupRepository`; `exercise.proto`
as §2; `ExerciseMapper`; `ExerciseController` validation (`muscle_group_ids`, sizes) and
`listMuscleGroups`; `ExerciseService` group resolution on create/update, `listMuscleGroups`;
`GrpcExceptionAdvice#handleValidation` fallback; `docs/domain-model.md` "shared across every
trainer" line updated; `ReplaceWorkoutExercisesIntegrationTest` fixture adapted (no enum).
Deliberately **not** here: identity/role/ownership (PR6), in-use guard (PR7), filter/search/
ordering (PR8) — `ListExercises` still returns `findAll()` in the new shape. After merge, `main`
deploys with the new proto shape; the live BFF's exercise list changes shape → deploy only within
the Increment 1 window (§8). Done when: verification.md §1.3 passes.

### PR4 — Pre-starter cleanup migration V23
Branch: `feat/exercise-starter-catalog-cleanup-migration`. Scope: the V23 file exactly as §3, with
the empty keep-list literal and the `-- @keep-list` marker; `PreStarterCleanupMigrationTest`. No
Java main code. One-way door — the reviewer's whole attention goes to the delete order and the
keep routine. After merge, `main` deploys; every pre-starter exercise (and its entries/sets/logs)
is gone on the next boot. Done when: verification.md §1.4 passes.

### PR5 — Starter-set seed migration V24
Branch: `feat/exercise-starter-catalog-seed`. Scope: the V24 file (staging rows transcribed from
PRD §10, the three inserts, the count assertions); `ExerciseCatalogRepositoryTest` seed scenarios.
No Java main code. After merge, `main` deploys; `ListExercises` (still unscoped until PR6)
returns 199 rows. Done when: verification.md §1.5 passes.

### PR6 — Exercise RPCs require identity; role, ownership and starter-set rules
Branch: `feat/exercise-starter-catalog-exercise-authorization`. Scope: `ExerciseController`
resolves `CallerIdentity` on five RPCs (`ListMuscleGroups` excluded); `ExerciseService` takes
`CallerIdentity`, enforces D5/D2/D3 as §5.2 step 1 (+ owner scoping of the list via a simple
`findByOwnerIdIsNullOrOwnerId(ownerId)` derived query until PR8 replaces it), §5.3, §5.4 steps 1
and 4 (owner), §5.5 steps 1, 4–5, §5.6 steps 1, 3–4; `WorkoutExerciseRepository
#existsByExerciseIdAndWorkout_TrainingPlan_Client_Id`; tests. After merge, `main` deploys **only
if** the BFF forwards its JWT (Increment 0 on bff live) — the delivery table says so. Done when:
verification.md §1.6 passes.

### PR7 — `DeleteExercise` refuses an exercise a workout uses
Branch: `feat/exercise-starter-catalog-delete-in-use`. Scope: `ExerciseInUseException` + both
handler entries, `WorkoutExerciseRepository#existsByExerciseId`, `ExerciseService#deleteExercise`
step 5, tests. After merge, `main` deploys; the raw FK `UNKNOWN` path is gone. Done when:
verification.md §1.7 passes.

### PR8 — `ListExercises` filter, search and ordering
Branch: `feat/exercise-starter-catalog-list-query`. Scope: `ExerciseRepository#findVisible`
native query; `ExerciseController` `search` validation/trim; `ExerciseService#listExercises`
steps 2–5 (group existence, escaping); `ExerciseCatalogRepositoryTest` ordering scenarios (the
first repository-level proof in the codebase, D12). After merge, `main` deploys; Increment 1 is
complete on the API side. Done when: verification.md §1.8 passes.

### PR9 — Workout-side guards R17–R19
Branch: `feat/exercise-starter-catalog-workout-guards`. Scope: `WorkoutExerciseController`/
`WorkoutController` resolve identity on the four RPCs only; `WorkoutExerciseService
#createWorkoutExercise`, `WorkoutService#createWorkoutWithExercises`/`replaceWorkoutExercises`/
`cloneWorkout` as §5.7; `Exercise#isVisibleTo` reused; tests incl. the
`ReplaceWorkoutExercisesIntegrationTest` token. Deliberately not here: any other plan/workout RPC.
After merge, `main` deploys; the BFF only needs its `docs/api-contract.md` rows. Done when:
verification.md §1.9 passes.
