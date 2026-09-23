# Verification: Starter exercise catalog (vertice-api)

Spec: `docs/specs/exercise-starter-catalog/spec.md`
PRD: `docs/prds/exercise-starter-catalog/prd.md`
Assessment: `docs/assessments/exercise-starter-catalog/assessment.md`
Status: Draft. Update the checklist marks as PRs are verified.

## How to use this document

You are verifying an implementation against the spec above. Run the section for the PR you were
asked about (§1.<n>, one per spec §10 PR), or all of §1 plus §2 for the whole feature. For every
check record PASS, FAIL, or NOT CHECKED with the evidence (command output, query result). A FAIL on
a "must not change" check is as serious as one on a "must change" check. Report in the format of
§3.

Every `grpcurl` below is against `localhost:9090` with `-plaintext`. `$T1`, `$T2`, `$C1`, `$A1`
are the bearer tokens from §0, sent as `-H "authorization: Bearer $T1"`. An expected error is
written as its gRPC code and description, which `grpcurl` prints as `Code: PermissionDenied` /
`Message: ...`; the description must match exactly unless the check says "contains". Every
`psql` below is `docker exec vertice-postgres psql -U vertice -d vertice -Atc "<sql>"`, written
`psql "<sql>"` for short.

## 0. Preconditions

- **Branch under test:** the PR's branch checked out in the vertice-api repo. The PRs are
  stacked (spec §10), so `feat/exercise-starter-catalog-<n>` contains every earlier PR:
  `jwt-decoder` (PR1) → `caller-identity` (PR2) → `muscle-groups` (PR3) → `cleanup-migration`
  (PR4) → `seed` (PR5) → `exercise-authorization` (PR6) → `delete-in-use` (PR7) → `list-query`
  (PR8) → `workout-guards` (PR9, not built yet).
- **Environment (from CLAUDE.md):** `docker compose up -d` (Postgres, container
  `vertice-postgres`), then `./gradlew bootRun --args='--spring.profiles.active=local'` (gRPC on
  `:9090`, reflection on, a bearer token optional). The non-local checks in §1.1 start the app
  without the `local` profile and say how.
- **Fresh database** for §1.3–§1.5: `docker compose down -v && docker compose up -d`, so every
  migration runs from V1. §1.4 seeds pre-starter data on `main` first; follow it in order.
- **Tokens.** vertice-api verifies HS256 tokens with `JWT_SECRET` (default
  `dev-secret-change-me`). Mint one without `exp` (spec §6 accepts it) with this shell function:
  ```sh
  b64url() { base64 | tr -d '=\n' | tr '/+' '_-'; }
  jwt() { # jwt <user id> <ROLE> [secret]
    local h p s
    h=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
    p=$(printf '{"id":%s,"role":"%s"}' "$1" "$2" | b64url)
    s=$(printf '%s.%s' "$h" "$p" | openssl dgst -sha256 -hmac "${3:-dev-secret-change-me}" -binary | b64url)
    printf '%s.%s.%s' "$h" "$p" "$s"
  }
  ```
  The token's `id` must be a real user id for the checks that touch plans and workouts. Create
  the users once (local profile, any branch; CPFs are valid and unique):
  - `grpcurl -plaintext -d '{"name":"Trainer One","email":"t1@vertice.test","password":"secret1","cpf":"52998224725","role":"TRAINER"}' localhost:9090 vertice.user.v1.UserService/CreateUser`
    → note `id` as `T1_ID`; `T1=$(jwt $T1_ID TRAINER)`.
  - Same with `"Trainer Two"`, `t2@vertice.test`, cpf `11144477735` → `T2_ID`, `T2=$(jwt $T2_ID TRAINER)`.
  - Same with `"Client One"`, `c1@vertice.test`, cpf `12345678909`, `"role":"CLIENT"` → `C1_ID`,
    `C1=$(jwt $C1_ID CLIENT)`.
  - Same with `"Admin One"`, `a1@vertice.test`, cpf `98765432100`, `"role":"ADMIN"` → `A1_ID`,
    `A1=$(jwt $A1_ID ADMIN)`.
  - `BAD=$(jwt $T1_ID TRAINER some-other-secret)`: a well-formed token signed with the wrong secret.
- **Data** (needed from §1.6 on; create it once Increment 1's exercise shape is on the branch):
  - `S1`: the starter row "Supino reto com barra":
    `S1=$(psql "SELECT id FROM exercises WHERE name = 'Supino reto com barra' AND owner_id IS NULL")`.
  - `X1`, T1's private exercise:
    `grpcurl -plaintext -H "authorization: Bearer $T1" -d '{"name":"Remada do T1","muscle_group_ids":[12,2]}' localhost:9090 vertice.exercise.v1.ExerciseService/CreateExercise`
    → note `id` as `X1`. (On PR3–PR5, which do not read identity yet, psql
    `UPDATE exercises SET owner_id = $T1_ID WHERE id = $X1` instead.)
  - `X2`, T2's private exercise: same with `$T2` and `"name":"Remada do T2"` → `X2`.
  - `P1`, T1's plan for C1:
    `grpcurl -plaintext -d "{\"name\":\"Plano C1\",\"trainer_id\":$T1_ID,\"client_id\":$C1_ID,\"start_date\":\"2026-09-21\",\"end_date\":\"2026-12-21\"}" localhost:9090 vertice.plan.v1.TrainingPlanService/CreateTrainingPlan`
    → `P1`.
  - `W1`, a workout in P1 using X1:
    `grpcurl -plaintext -d "{\"name\":\"Treino A\",\"training_plan_id\":$P1,\"day_of_week\":\"MONDAY\"}" localhost:9090 vertice.plan.v1.WorkoutService/CreateWorkout`
    → `W1`, then
    `grpcurl -plaintext -d "{\"workout_id\":$W1,\"exercise_id\":$X1,\"order\":1}" localhost:9090 vertice.plan.v1.WorkoutExerciseService/CreateWorkoutExercise`.
- **Tooling:** `grpcurl`, `openssl`, `docker`, `git`, `./gradlew`.

## 1. Per-PR checks

### 1.1 PR1 — Shared-secret HS256 `JwtDecoder`; local profile accepts an optional bearer token

Branch: `feat/exercise-starter-catalog-jwt-decoder`. Spec: §0 D1 and its sub-decisions, §3
Properties, §5.0 steps 1, 2, 4, §6 `HmacJwtDecoder`/`JwtSecretGuard`.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `./gradlew test --tests "*HmacJwtDecoderTest"` runs and passes
      `decode_validHs256Token_returnsClaims`, `decode_wrongSecret_throwsBadJwt`,
      `decode_algNone_throwsBadJwt`, `decode_expiredToken_throwsBadJwt`,
      `decode_tokenWithoutExp_isAccepted`, `decode_tokenWithFutureExp_exposesInstant`,
      `decode_shortSecret_stillVerifies`, `decode_malformedToken_throwsBadJwt`.
- [ ] `*JwtSecretGuardTest`: `shortSecret_fails`, `longSecret_passes`.
- [ ] `*GrpcHealthCheckTest`: `check_withoutAuth_returnsUnauthenticated` (unchanged) and
      `check_withSharedSecretToken_returnsServing`.
- [ ] `*GrpcHealthCheckLocalProfileTest`: `check_withoutAuth_returnsServing` (unchanged).
      Evidence for all four: the Gradle test report lists every name as passed.

**Behavior, local profile** (`bootRun` with `local`)
- [ ] B1: `grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check` (no token) →
      `{"status":"SERVING"}`. Covers: D1, `local` token optional.
- [ ] B2: same with `-H "authorization: Bearer $T1"` → `SERVING`. Covers: a valid token decodes.
- [ ] B3: same with `-H "authorization: Bearer $BAD"` → `Code: Unauthenticated`. Covers: §0 D1
      consequence (present but invalid fails even where identity is not needed).
- [ ] B4: `grpcurl ... -H "authorization: Bearer $T1" -d '{}' localhost:9090 vertice.exercise.v1.ExerciseService/ListExercises`
      → succeeds with today's shape (nothing reads the identity yet). Covers: "nothing
      user-visible changes" (spec §1, Increment 0).

**Behavior, non-local** (stop the app; start it with
`JWT_SECRET=<secret> ./gradlew bootRun --args='--spring.grpc.server.reflection.enabled=true'`)
- [ ] B5: `JWT_SECRET=short-secret` → the app does **not** start; the log contains
      `JWT_SECRET must be at least 32 bytes`. Covers: §0 D1 `JwtSecretGuard` sub-decision (F12).
- [ ] B6: `JWT_SECRET=0123456789abcdef0123456789abcdef` (32 bytes) → starts.
      `grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check` → `Code: Unauthenticated`;
      with `-H "authorization: Bearer $(jwt $T1_ID TRAINER 0123456789abcdef0123456789abcdef)"`
      → `SERVING`; with `-H "authorization: Bearer $T1"` (signed with the default secret) →
      `Unauthenticated`. Covers: §5.0 step 1.

**Scope decisions honored**
- [ ] S1: `issuer-uri` is gone and the secret property exists. Evidence:
      `grep -n "issuer-uri\|vertice.jwt.secret" src/main/resources/application*.properties` → only
      `vertice.jwt.secret=${JWT_SECRET:dev-secret-change-me}` in `application.properties`.
- [ ] S2: one decoder bean for both transports. Evidence: `grep -rn "JwtDecoder jwtDecoder"
      src/main/java` → only `config/JwtDecoderConfig`.

**Must not change**
- [ ] N1: no proto changed. Evidence: `git diff main --stat -- src/main/proto` → empty.
- [ ] N2: no migration added. Evidence: `git diff main --stat -- src/main/resources/db` → empty.

### 1.2 PR2 — `CallerIdentity` resolver under `grpc/`

Branch: `feat/exercise-starter-catalog-caller-identity`. Spec: §0 D1 (resolver) and D2, §5.0
step 3, §6 `CallerIdentity`, `CallerIdentityResolver`, exceptions table rows for PR2.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*CallerIdentityResolverTest`: `require_withJwtAuthentication_returnsUserIdAndRole`,
      `require_withoutAuthentication_throwsUnauthenticated`,
      `require_withMissingIdClaim_throwsUnauthenticated`,
      `require_withNonNumericIdClaim_throwsUnauthenticated`,
      `require_withUnknownRole_throwsUnauthenticated`, `current_withoutAuthentication_isEmpty`.
- [ ] `*GrpcCallerIdentityTest` (port 19104): `call_withBearerToken_resolvesIdentity`,
      `call_withoutToken_requireFailsUnauthenticated`,
      `call_withoutToken_rpcNotRequiringIdentitySucceeds`, `call_withInvalidToken_failsUnauthenticated`.
- [ ] `*GrpcExceptionMappingTest`: `unauthenticatedException_mapsToUnauthenticated`,
      `permissionDeniedException_mapsToPermissionDenied`.

**Behavior**
- [ ] B1: §1.1 B1–B4 still pass (no controller calls the resolver yet). Covers: "no behavior
      change" (spec §10 PR2).

**Scope decisions honored**
- [ ] S1: the resolver lives under `grpc/` and services never read the security context.
      Evidence: `grep -rln "SecurityContextHolder" src/main/java` → only
      `grpc/CallerIdentityResolver.java`. Covers: F26.
- [ ] S2: both new exceptions are in both handlers. Evidence: `grep -n
      "UnauthenticatedException\|PermissionDeniedException"
      src/main/java/com/vertice/api/grpc/GrpcExceptionAdvice.java
      src/main/java/com/vertice/api/common/exception/GlobalExceptionHandler.java` → one handler
      each in both files (`UNAUTHENTICATED`/`401`, `PERMISSION_DENIED`/`403`). Covers: F20.

**Must not change**
- [ ] N1: `git diff origin/feat/exercise-starter-catalog-jwt-decoder --stat -- src/main/proto src/main/resources/db` → empty.

### 1.3 PR3 — Muscle-group model

Branch: `feat/exercise-starter-catalog-muscle-groups`. Fresh database. Spec: §0 D6, D7, the
no-primary decision, the `handleValidation` decision; §2 proto; §3 V22 and entity mapping; §4
group rules; §5.1; §5.4–5.5 (shape); §6 mapper.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*ExerciseServiceTest`: `createExercise_savesGroupsInRequestOrderWithoutPrimary`,
      `createExercise_deduplicatesGroupIds`, `createExercise_unknownGroup_throwsInvalidArgumentNamingId`,
      `updateExercise_replacesGroupsWithoutPrimary`,
      `getExercise_starterRow_isStarterTrueGroupsPrimaryFirst`, `listMuscleGroups_returnsIdOrder`,
      plus the kept `getExercise_withNullVideoUrl_returnsEmptyStringNotNull`,
      `getExercise_withNullDescription_returnsEmptyStringNotNull`, `getExercise_throwsWhenMissing`,
      `updateExercise_throwsWhenMissing`, `deleteExercise_throwsWhenMissing`.
- [ ] `*ExerciseControllerTest`: `listMuscleGroups_returnsGroups`,
      `createExercise_withoutMuscleGroupIds_throwsInvalidArgumentWithMessage`,
      `updateExercise_withoutMuscleGroupIds_throwsInvalidArgumentWithMessage`,
      `createExercise_withDuplicateGroupIds_isAllowed`, `createExercise_nameOver255_throwsInvalidArgument`,
      and the adapted existing ones (`createExercise_withValidRequest_returnsCreated`,
      `createExercise_withBlankName_throwsInvalidArgument`, the four `video_url` cases,
      `updateExercise_whenExists_returnsUpdated`, `*_whenMissing_throwsNotFound`,
      `deleteExercise_whenExists_succeeds`). No test named `*UnsetMuscleGroup*` remains.
- [ ] `*GrpcExceptionMappingTest`: `constraintViolationWithoutViolations_keepsMessageAsDescription`.
- [ ] `*ExerciseCatalogRepositoryTest` (port 19105): `muscleGroups_seededInLaunchOrder`,
      `createExercise_groupsSentOutOfOrder_comeBackByIdWithNoPrimary`,
      `updateExercise_withOverlappingGroups_replacesLinksWithNoPrimary`,
      `onePrimaryIndex_stillRejectsASecondPrimary`.

**Behavior** (local profile, fresh database)
- [ ] B1: `psql "SELECT version, success FROM flyway_schema_history WHERE version = '22'"` →
      `22|t`. `psql "SELECT count(*) FROM muscle_groups"` → `14`.
      `psql "SELECT string_agg(name, ',' ORDER BY id) FROM muscle_groups"` →
      `Peito,Costas,Ombros,Bíceps,Tríceps,Antebraço,Quadríceps,Posteriores de coxa,Glúteos,Panturrilhas,Abdômen,Lombar,Trapézio,Cardio`.
      Covers: R8, R9, D6.
- [ ] B2: `psql "SELECT column_name FROM information_schema.columns WHERE table_name = 'exercises' ORDER BY column_name"`
      → `description, id, name, owner_id, video_url` (no `muscle_group`). Covers: D6, §3 V22.
- [ ] B3: `grpcurl -plaintext -d '{}' localhost:9090 vertice.exercise.v1.ExerciseService/ListMuscleGroups`
      (no token) → 14 `{id, name}` objects, ids 1..14 in B1's order. Covers: §5.1.
- [ ] B4: `grpcurl -plaintext -d '{"name":"Rosca teste","muscle_group_ids":[6,4,4]}' localhost:9090 vertice.exercise.v1.ExerciseService/CreateExercise`
      → `muscleGroups` = `[{"id":"4","name":"Bíceps"},{"id":"6","name":"Antebraço"}]` (duplicate
      collapsed, id order, no primary) and `isStarter: true`: PR3 sets no owner yet (PR6 does),
      so the row is ownerless on this PR only. Then
      `psql "SELECT bool_or(is_primary), count(catalog_order) FROM exercise_muscle_groups WHERE exercise_id = <id>"`
      → `f|0`. Covers: R43, §0 no-primary decision.
- [ ] B5: same RPC with `"muscle_group_ids":[]` → `Code: InvalidArgument`,
      `Message: muscleGroupIds: must contain at least one muscle group`; with `[999]` →
      `InvalidArgument`, `muscleGroupIds: unknown muscle group 999`; with `"name":"  "` and `[1]`
      → `InvalidArgument`, `name: must not be blank`. Covers: R40/E13, R3, the `handleValidation`
      fallback (the description is not empty).
- [ ] B6: `UpdateExercise` on B4's id with `{"id":<id>,"exercise":{"name":"Rosca teste","muscle_group_ids":[]}}`
      → `InvalidArgument` with the same message. Covers: R31/E22.

**Scope decisions honored**
- [ ] S1: the proto matches spec §2. Evidence: `grep -n "reserved\|muscle_group" src/main/proto/vertice/exercise/v1/exercise.proto`
      → `reserved 5;`, `reserved 4;`, two `reserved "muscle_group";`,
      `repeated MuscleGroupResponse muscle_groups = 6;`, `repeated int64 muscle_group_ids = 5;`,
      `int64 muscle_group_id = 1;`; `grep -c "enum MuscleGroup"` → `0`. Covers: F9, D6.
- [ ] S2: nothing outside `plan/exercise` referenced the old enum. Evidence:
      `grep -rn "MuscleGroup\." src/main/java --include=*.java | grep -v plan/exercise` → no hits.
      Covers: F25.

**Must not change**
- [ ] N1: `workout.proto` and `workout_exercise.proto` unchanged. Evidence: `git diff main -- src/main/proto/vertice/plan` → empty.
- [ ] N2: `ListExercises` is still unscoped on this PR (returns every row). Covers: spec §10 PR3 "deliberately not here".

### 1.4 PR4 — Pre-starter cleanup migration V23 (one-way door)

Branch: `feat/exercise-starter-catalog-cleanup-migration`. Spec: §0 D8 and its "kept exercise that
no workout uses" sub-decision, §3 V23.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*PreStarterCleanupMigrationTest` (port 19106): `unkeptExercise_removedWithEntriesSetsAndLogs`,
      `sessionsAndFeedback_survive`, `keptExerciseUsedByOneTrainer_becomesPrivateToThatTrainer`,
      `keptExerciseUsedByTwoTrainers_oneCopyPerTrainer_entriesAndLogsFollow`,
      `keptExerciseGroups_filedFromKeepList_noPrimary_orderNull`,
      `keptExercise_readThroughService_groupsInIdOrder_notStarter`, `keptButUnusedExercise_isRemoved`,
      `unknownGroupNameInKeepList_failsMigration`, `unknownExerciseIdInKeepList_failsMigration`,
      `shippedKeepList_isEmpty`, `runsBeforeStarterSeed_byVersionNumber`. No test named
      `*primaryFlagged*` remains.

**Behavior** (real pre-starter data, the way production meets V23)
1. `docker compose down -v && docker compose up -d`; `git checkout main`; `bootRun` with `local`.
2. Create the §0 users, then on `main`'s shape: `CreateExercise`
   `{"name":"Supino antigo","muscle_group":"CHEST"}` → `OLD`; `CreateTrainingPlan` for T1/C1 →
   `P0`; `CreateWorkout` in P0 → `W0`; `CreateWorkoutExercise` `{workout_id: W0, exercise_id: OLD,
   order: 1}` → `WE0`; `vertice.plan.v1.ExerciseSetService/CreateExerciseSet`
   `{"workout_exercise_id":WE0,"set_number":1,"reps":10,"strategy":"STRAIGHT"}` → `ES0`;
   `vertice.session.v1.WorkoutSessionService/GetOrStartWorkoutLog`
   `{"workout_id":W0,"client_id":C1_ID,"week_start_date":"2026-09-21"}` → `WL0`;
   `RecordSetLog` `{"workout_log_id":WL0,"exercise_set_id":ES0,"weight":"40","reps":10}`.
   Record `psql "SELECT count(*) FROM workout_logs"` as `LOGS_BEFORE`.
3. Stop; `git checkout feat/exercise-starter-catalog-cleanup-migration`; `bootRun` with `local`.

- [ ] B1: `psql "SELECT version, success FROM flyway_schema_history WHERE version IN ('22','23') ORDER BY 1"`
      → `22|t` and `23|t`. Covers: ordering V22 → V23.
- [ ] B2: `psql "SELECT count(*) FROM exercises"` → `0`;
      `psql "SELECT count(*) FROM workout_exercises"` → `0`;
      `psql "SELECT count(*) FROM exercise_sets"` → `0`; `psql "SELECT count(*) FROM set_logs"` →
      `0`. Covers: R51–R53, E12 (the keep-list ships empty, D8).
- [ ] B3: `psql "SELECT count(*) FROM workout_logs"` → `LOGS_BEFORE`;
      `psql "SELECT count(*) FROM workouts WHERE id = $W0"` → `1`;
      `psql "SELECT count(*) FROM training_plans WHERE id = $P0"` → `1`. Covers: R54.

**Scope decisions honored**
- [ ] S1: shipped keep-list is empty and has no primary column. Evidence:
      `grep -n "INSERT INTO keep_list\|is_primary" src/main/resources/db/migration/V23__remove_pre_starter_exercises.sql`
      → only the commented example `-- Example: INSERT INTO keep_list VALUES (42, 'Peito');` and
      step 4's `FALSE` literal for `is_primary`; no `exactly one primary` text.
- [ ] S2: delete order. Evidence: code read of step 5: `set_logs` → `exercise_sets` →
      `workout_exercises` → `exercises`, and the file names no `workout_logs`, `workout_feedback`,
      `workouts` or `training_plans` in a `DELETE`. Covers: F4, R54.

**Must not change**
- [ ] N1: no Java main code. Evidence: `git diff origin/feat/exercise-starter-catalog-muscle-groups --stat -- src/main/java` → empty.

### 1.5 PR5 — Starter-set seed migration V24

Branch: `feat/exercise-starter-catalog-seed`. Fresh database. Spec: §3 V24.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*ExerciseCatalogRepositoryTest`: `starterSet_has199ExercisesWithNullOwner`,
      `starterSet_has283LinksAcross14Groups`,
      `starterSet_everyExerciseHasExactlyOnePrimaryWithCatalogOrder`,
      `starterSet_noDescriptionOrVideo`, `starterSet_perGroupPrimaryCountsMatchPrd`,
      `starterSet_namesUnique`, plus PR3's four.

**Behavior** (fresh database, local profile)
- [ ] B1: `psql "SELECT version, success FROM flyway_schema_history WHERE version = '24'"` → `24|t`.
- [ ] B2: the three counts:
      `psql "SELECT count(*) FROM exercises WHERE owner_id IS NULL"` → **199**;
      `psql "SELECT count(*) FROM muscle_groups"` → **14**;
      `psql "SELECT count(*) FROM exercise_muscle_groups emg JOIN exercises e ON e.id = emg.exercise_id WHERE e.owner_id IS NULL"`
      → **283**. Covers: R1, R8, R11, R12.
- [ ] B3: `psql "SELECT count(*) FROM exercises WHERE owner_id IS NULL AND (description IS NOT NULL OR video_url IS NOT NULL)"`
      → `0`. Covers: R4, R5.
- [ ] B4: per group, primary and total links:
      `psql "SELECT mg.name, count(*) FILTER (WHERE emg.is_primary), count(*) FROM exercise_muscle_groups emg JOIN muscle_groups mg ON mg.id = emg.muscle_group_id JOIN exercises e ON e.id = emg.exercise_id WHERE e.owner_id IS NULL GROUP BY mg.id, mg.name ORDER BY mg.id"`
      → Peito 20/24, Costas 22/23, Ombros 20/27, Bíceps 16/21, Tríceps 16/30, Antebraço 9/13,
      Quadríceps 20/23, Posteriores de coxa 13/18, Glúteos 14/37, Panturrilhas 8/8, Abdômen 17/19,
      Lombar 7/19, Trapézio 7/11, Cardio 10/10. Covers: R49, R50, spec §3 expected counts.
- [ ] B5: `psql "SELECT e.name FROM exercise_muscle_groups emg JOIN exercises e ON e.id = emg.exercise_id WHERE emg.muscle_group_id = 12 AND emg.is_primary ORDER BY emg.catalog_order"`
      → the PRD §10 Lombar list in order (Hiperextensão lombar no banco romano, Hiperextensão
      lombar na máquina, Superman no solo, Extensão lombar unilateral no banco, Prancha reversa,
      Bird dog no solo, Ponte isométrica no banco romano). Covers: R49.
- [ ] B6: `psql "SELECT string_agg(mg.name, ',' ORDER BY mg.id) FROM exercise_muscle_groups emg JOIN muscle_groups mg ON mg.id = emg.muscle_group_id JOIN exercises e ON e.id = emg.exercise_id WHERE e.name = 'Levantamento terra com barra'"`
      → `Costas,Posteriores de coxa,Lombar`. Covers: R11, E8.

**Must not change**
- [ ] N1: no Java main code. Evidence: `git diff origin/feat/exercise-starter-catalog-cleanup-migration --stat -- src/main/java` → empty.

### 1.6 PR6 — Exercise RPCs require identity; role, ownership and starter-set rules

Branch: `feat/exercise-starter-catalog-exercise-authorization`. §0 users and data. Spec: §0 D2,
D3, D5, the check-order decision; §2 status table; §4 identity/role/ownership rows; §5.2 steps 1
and 3, §5.3, §5.4–§5.6 except the in-use guard.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*ExerciseServiceTest`: `listExercises_clientRefused`,
      `listExercises_trainerPassesOwnId_adminPassesOwnId`, `getExercise_missing_notFoundBeforeVisibility`,
      `getExercise_trainerOtherTrainers_permissionDenied`, `getExercise_trainerOwn_allowed`,
      `getExercise_adminPrivate_permissionDenied`, `getExercise_adminStarter_allowed`,
      `getExercise_clientInOwnWorkout_allowed`, `getExercise_clientOutsideOwnWorkouts_permissionDenied`,
      `createExercise_clientRefused`, `createExercise_adminRefused`, `createExercise_setsOwnerToCaller`,
      `updateExercise_starter_permissionDeniedStarterMessage`, `updateExercise_otherTrainers_permissionDenied`,
      `updateExercise_own_replacesAllFields`, `updateExercise_clientRefused`,
      `deleteExercise_starter_permissionDeniedStarterMessage`, `deleteExercise_otherTrainers_permissionDenied`,
      `deleteExercise_clientRefused`.
- [ ] `*ExerciseControllerTest`: `listMuscleGroups_withoutToken_succeeds`,
      `listExercises_withoutToken_unauthenticated`, `getExercise_withoutToken_unauthenticated`,
      `createExercise_withoutToken_unauthenticated`, `updateExercise_withoutToken_unauthenticated`,
      `deleteExercise_withoutToken_unauthenticated`, `listExercises_clientToken_permissionDenied`,
      `updateExercise_starter_permissionDeniedWithMessage`,
      `deleteExercise_otherTrainers_permissionDeniedWithMessage`,
      `createExercise_passesCallerIdentityToService`.
- [ ] `*ExerciseCatalogRepositoryTest`: `findByOwnerIdIsNullOrOwnerId_returnsStarterAndOwnOnly`.

**Behavior** (`E` = `vertice.exercise.v1.ExerciseService`)
- [ ] B1: without a token: `ListMuscleGroups` → 14 groups; `ListExercises`, `GetExercise
      {"id":S1}`, `CreateExercise`, `UpdateExercise`, `DeleteExercise` → each `Code:
      Unauthenticated`, `Message: Caller identity required`. Covers: D1, §4 identity row.
- [ ] B2: `E/ListExercises` with `$T1` → 200 rows: `Remada do T1` (`isStarter` false) and 199
      rows with `isStarter: true`, in no fixed order (ordering arrives in PR8); no `Remada do T2`.
      With `$A1` → 199 rows, all starter. With `$C1` → `PermissionDenied`, `Role CLIENT is not
      allowed to list exercises`. Covers: R13–R15, R21, R25, D5.
- [ ] B3: `E/GetExercise` `{"id":X2}` with `$T1` → `PermissionDenied`, `You do not have access to
      exercise <X2>`; `{"id":999999}` with `$T1` → `NotFound`, `Exercise with id 999999 not
      found`; `{"id":X1}` with `$A1` → `PermissionDenied`; `{"id":S1}` with `$A1` → the row.
      Covers: R16/E18, R25, D2.
- [ ] B4: `E/GetExercise` `{"id":X1}` with `$C1` → the row (X1 is in W1, a workout of C1's plan
      P1); `{"id":X2}` with `$C1` → `PermissionDenied`. Covers: R20/E7, R22/E16.
- [ ] B5: `E/CreateExercise` `{"name":"x","muscle_group_ids":[1]}` with `$C1` → `PermissionDenied`,
      `Role CLIENT is not allowed to create exercises`; with `$A1` → `Role ADMIN is not allowed to
      create exercises`. With `$T1` → created, `isStarter` false, and
      `psql "SELECT owner_id FROM exercises WHERE id = <new id>"` → `$T1_ID`. Covers: R14, R23/E17, D5.
- [ ] B6: check order (§0, owner decision 2026-09-23): `E/CreateExercise`
      `{"name":"  ","muscle_group_ids":[1]}` with `$C1` → `InvalidArgument`, `name: must not be
      blank` (not `PermissionDenied`); `{"name":"x","muscle_group_ids":[]}` with `$A1` →
      `InvalidArgument`, `muscleGroupIds: must contain at least one muscle group`;
      `E/UpdateExercise` `{"id":X1,"exercise":{"name":"x","muscle_group_ids":[]}}` with `$C1` →
      `InvalidArgument`. Covers: §2 status-table order.
- [ ] B7: `E/UpdateExercise` `{"id":S1,"exercise":{"name":"Supino renomeado","muscle_group_ids":[1]}}`
      with `$T1` → `PermissionDenied`, `Exercise <S1> belongs to the shared starter set and cannot be
      changed`; `GetExercise {"id":S1}` still returns the original name. `DeleteExercise
      {"id":S1}` with `$T1` → `... cannot be deleted`. Covers: R26/E1, R27/E2, D3.
- [ ] B8: `E/UpdateExercise` and `E/DeleteExercise` on `X2` with `$T1` → `PermissionDenied`, `You do
      not have access to exercise <X2>`; on `999999` → `NotFound`; `UpdateExercise` on `X1` with
      `$C1` → `Role CLIENT is not allowed to change exercises`; `DeleteExercise` on `X1` with `$C1`
      → `Role CLIENT is not allowed to delete exercises`. Covers: R24, R32, R36, E17, E21.
- [ ] B9: `E/UpdateExercise` `{"id":X1,"exercise":{"name":"Remada do T1 v2","description":"nova","video_url":"https://v.test/2","muscle_group_ids":[2]}}`
      with `$T1` → all four fields replaced, `muscleGroups` = `[{"id":"2","name":"Costas"}]`.
      Covers: R29, R30, D11.

**Must not change**
- [ ] N1: every non-exercise RPC still works without a token, e.g.
      `grpcurl -plaintext -d "{\"training_plan_id\":$P1}" localhost:9090 vertice.plan.v1.WorkoutService/ListWorkouts`
      → succeeds. Covers: D10 "inherited gap elsewhere".

### 1.7 PR7 — `DeleteExercise` refuses an exercise a workout uses

Branch: `feat/exercise-starter-catalog-delete-in-use`. §0 data. Spec: §0 D4, §5.6 step 6, §6
`ExerciseInUseException`.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*ExerciseServiceTest`: `deleteExercise_inUse_throwsExerciseInUse`,
      `deleteExercise_unused_deletes`, `deleteExercise_checksStarterAndOwnershipBeforeInUse`.
- [ ] `*ExerciseControllerTest`: `deleteExercise_inUse_failedPreconditionWithMessage`.
- [ ] `*GrpcExceptionMappingTest`: `exerciseInUseException_mapsToFailedPrecondition`.

**Behavior**
- [ ] B1: `E/DeleteExercise {"id":X1}` with `$T1` (X1 is in W1) → `Code: FailedPrecondition`,
      `Message: Exercise <X1> is used by a workout and cannot be deleted`; X1 still exists.
      Covers: R34/E5, D4.
- [ ] B2: create an unused exercise as `$T1` → `X3`; `E/DeleteExercise {"id":X3}` with `$T1` →
      `{}`; again → `NotFound`. Covers: R35.
- [ ] B3: `E/DeleteExercise {"id":S1}` with `$T1` still → the starter-set message, not
      `FailedPrecondition`, even after S1 is added to a workout. Covers: §5.6 step order.

**Scope decisions honored**
- [ ] S1: `ExerciseInUseException` is in both handlers. Evidence: `grep -n ExerciseInUseException
      src/main/java/com/vertice/api/grpc/GrpcExceptionAdvice.java
      src/main/java/com/vertice/api/common/exception/GlobalExceptionHandler.java` →
      `FAILED_PRECONDITION` and `PRECONDITION_FAILED`. Covers: F20.

### 1.8 PR8 — `ListExercises` filter, search and ordering

Branch: `feat/exercise-starter-catalog-list-query`. §0 data. Spec: §0 D7, D12, the search
decision; §5.2 steps 2, 4–6; §6 `findVisible`.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*ExerciseCatalogRepositoryTest`: `findVisible_ownFirstThenStarterByName`,
      `findVisible_groupFilter_primaryInCatalogOrderThenSecondaryByName`,
      `findVisible_groupFilter_includesSecondaryGroupExercises`,
      `findVisible_search_caseInsensitiveSubstring`, `findVisible_search_wildcardsAreLiteral`,
      `findVisible_search_ignoresOtherTrainersRows`, `findVisible_adminId_returnsStarterOnly`,
      `findVisible_noFilter_starterByName`, `findVisible_groupAndSearch_combined`; and
      `findByOwnerIdIsNullOrOwnerId_returnsStarterAndOwnOnly` is gone.
- [ ] `*ExerciseServiceTest`: `listExercises_unknownGroup_notFound`,
      `listExercises_escapesLikeWildcards`, `listExercises_trimsSearch`.
- [ ] `*ExerciseControllerTest`: `listExercises_searchOver100_invalidArgument`,
      `listExercises_forwardsFilterAndSearch`.

**Behavior** (`$T1` unless stated)
- [ ] B0: `E/CreateExercise {"name":"Lombar do T1","muscle_group_ids":[12]}` → `L1` (T1's only
      Lombar exercise; §1.6 B9 left X1 with Costas only).
- [ ] B1: `E/ListExercises {"muscle_group_id":12}` → `Lombar do T1` first; then the seven
      primary-Lombar starter rows in §1.5 B5's order; then the twelve secondary-Lombar starter rows
      by name, among them `Levantamento terra com barra`; 20 rows in all. Covers: R45, R47–R50, E8.
- [ ] B2: `E/ListExercises {"muscle_group_id":1}` → first starter row `Supino reto com barra`,
      second `Supino inclinado com halteres`; `Mergulho nas paralelas` and `Supino fechado com
      barra` come after all 20 primary-Peito rows. Covers: R49, R50.
- [ ] B3: `E/ListExercises {"search":"  supino RETO "}` → only names containing "supino reto"
      case-insensitively, `Supino reto com barra` among them. `{"search":"%"}` → no rows.
      Covers: R46, E15, the search decision.
- [ ] B4: `E/ListExercises {"search":"Remada do T1"}` with `$T2` → no rows. Covers: E14.
- [ ] B5: `E/ListExercises {"muscle_group_id":999}` → `NotFound`, `MuscleGroup with id 999 not
      found`. A `search` of 101 characters → `InvalidArgument`, `search: size must be between 0
      and 100`; the same 101-character search with `$C1` → `InvalidArgument`, not
      `PermissionDenied` (check order, §0). Covers: §2 status table.
- [ ] B6: `E/ListExercises {}` with `$A1` → 199 rows, all starter, by name. Covers: R25.

### 1.9 PR9 — Workout-side guards R17–R19

Branch: `feat/exercise-starter-catalog-workout-guards`. **Not built yet**: until the branch
exists, mark every check here NOT CHECKED. The test names below are the plan in spec §7; replace
them with the built names when the branch lands. Needs a `P2` (T2's plan for a client of T2)
and `W2` (a workout in P2), created like `P1`/`W1`.

**Automated**
- [ ] `./gradlew build` passes.
- [ ] `*WorkoutExerciseServiceTest`: `createWorkoutExercise_otherTrainersWorkout_permissionDenied`,
      `createWorkoutExercise_otherTrainersExercise_permissionDenied`,
      `createWorkoutExercise_starterExerciseOwnWorkout_succeeds`,
      `createWorkoutExercise_clientCaller_permissionDenied`,
      `createWorkoutExercise_missingWorkout_notFoundBeforeOwnership`.
- [ ] `*WorkoutServiceTest`: `createWorkoutWithExercises_otherTrainersPlan_permissionDenied`,
      `createWorkoutWithExercises_otherTrainersExercise_permissionDenied`,
      `createWorkoutWithExercises_missingExercise_stillInvalidArgument`,
      `replaceWorkoutExercises_otherTrainersWorkout_permissionDenied`,
      `replaceWorkoutExercises_otherTrainersExercise_permissionDeniedBeforeRecordedDataCheck`,
      `cloneWorkout_sourceNotOwned_permissionDenied`, `cloneWorkout_targetPlanNotOwned_permissionDenied`,
      `cloneWorkout_bothOwned_deepCopiesAsBefore`.
- [ ] `*WorkoutExerciseControllerTest`: `createWorkoutExercise_withoutToken_unauthenticated`,
      `updateWorkoutExercise_withoutToken_stillSucceeds`. `*WorkoutControllerTest`:
      `cloneWorkout_withoutToken_unauthenticated`, `createWorkoutWithExercises_withoutToken_unauthenticated`,
      `replaceWorkoutExercises_withoutToken_unauthenticated`, `createWorkout_withoutToken_stillSucceeds`.
- [ ] `*ReplaceWorkoutExercisesIntegrationTest` passes with a trainer token.

**Behavior** (`W` = `vertice.plan.v1.WorkoutService`, `WE` = `vertice.plan.v1.WorkoutExerciseService`)
- [ ] B1: `WE/CreateWorkoutExercise {"workout_id":W1,"exercise_id":X2,"order":5}` with `$T1` →
      `PermissionDenied`, `You do not have access to exercise <X2>`. Covers: R17/E18.
- [ ] B2: `WE/CreateWorkoutExercise {"workout_id":W1,"exercise_id":S1,"order":5}` with `$T2` →
      `PermissionDenied`, `You do not have access to workout <W1>`; with `$A1` → the same. Covers:
      R18/E19, D10.
- [ ] B3: `W/CreateWorkoutWithExercises` into `P1` with `X2` in the list, as `$T1` →
      `PermissionDenied` for exercise X2; into `P2` as `$T1` → `You do not have access to training
      plan <P2>`. `W/ReplaceWorkoutExercises` on `W1` with `X2` → the exercise refusal, and W1's
      tree is unchanged. Covers: R17, R18.
- [ ] B4: `W/CloneWorkout {"source_workout_id":W2,"target_training_plan_id":P1,"name":"c"}` with
      `$T1` → `You do not have access to workout <W2>`; `{"source_workout_id":W1,"target_training_plan_id":P2,...}`
      → `You do not have access to training plan <P2>`. Covers: R19/E20.
- [ ] B5: without a token the four RPCs → `Unauthenticated`; `WE/UpdateWorkoutExercise` and
      `W/CreateWorkout` without a token still succeed. Covers: D10 scope.

## 2. Whole-feature checks (after the last PR)

- [ ] Every §1 section passes on the final branch (`list-query` for Increments 0–1; PR9's branch
      once it exists), on a fresh database.
- [ ] Migration end state: `psql "SELECT version FROM flyway_schema_history WHERE version IN ('22','23','24') AND success ORDER BY 1"`
      → `22`, `23`, `24`; the counts **199 / 14 / 283** of §1.5 B2 hold.
- [ ] End to end as the BFF drives it (T1): `ListMuscleGroups` → pick `12`;
      `ListExercises {"muscle_group_id":12,"search":"terra"}` → contains `Levantamento terra com
      barra`; `CreateExercise {"name":"Meu exercício","muscle_group_ids":[12,2]}` → `M`;
      `ListExercises {"muscle_group_id":12}` → `Meu exercício` among the own rows at the top;
      add `M` to `W1` (`CreateWorkoutExercise`); `DeleteExercise {"id":M}` → `FailedPrecondition`;
      delete that workout entry (`WE/DeleteWorkoutExercise`); `DeleteExercise {"id":M}` → `{}`.
- [ ] End-to-end refusals: `$C1` `ListExercises` → `PermissionDenied`; `$T2` `GetExercise {"id":X1}`
      → `PermissionDenied`; `$T1` `UpdateExercise` on `S1` → the starter-set message.
- [ ] The BFF's own verification (`vertice-bff/docs/specs/exercise-starter-catalog/verification.md`
      §1.1 B3–B4) passes against this API with the same `JWT_SECRET`.

Traceability, one row per PRD rule and edge case:

| Rule | Spec § | Check |
|---|---|---|
| R1, R12, R13 | §3 V24, §5.2 | 1.5 B2; 1.6 B2 |
| R2, R4, R5 | §3 V24 | 1.5 B2, B3 |
| R3, R11 | §3 V22/V24, §4 | 1.3 B5; 1.5 B2, B6 |
| R6, R7, R9, R10 | §3 V24 (content transcribed from PRD §10) | 1.5 B1, B4 (content review) |
| R8 | §3 V22, §5.1 | 1.3 B1, B3 |
| R14, R15 | §5.2, §5.4 | 1.6 B2, B5; 1.8 B4 |
| R16, R32, R36 | §5.3, §5.5, §5.6 | 1.6 B3, B8 |
| R17, R18, R19 | §5.7 (PR9, not built) | 1.9 B1–B4 |
| R20, R22 | §5.3 | 1.6 B4 |
| R21, R23, R24 | §4 role row, §5.2–§5.6 | 1.6 B2, B5, B8 |
| R25 | §0 D5, §5.2, §5.3 | 1.6 B2, B3; 1.8 B6 |
| R26, R27 | §5.5, §5.6 | 1.6 B7 |
| R28 | web (not this repo); `is_starter` provided | 1.6 B2 |
| R29, R30 | §5.5 | 1.6 B9 |
| R31, R40 | §4, §5.4, §5.5 | 1.3 B5, B6 |
| R33 | unchanged (ids only) | 1.6 B9 (same id) |
| R34, R35 | §5.6 | 1.7 B1, B2 |
| R37, R38 | not exposed (PRD §6) | 1.3 S1 (no such RPC) |
| R39, R41, R42, R43 | §4, §5.4 | 1.3 B4; 1.6 B5, B9 |
| R44 | no constraint | 1.8 B2 (own and starter names coexist) |
| R45, R46 | §5.2 | 1.8 B1, B3 |
| R47–R50 | §5.2 | 1.8 B1, B2 |
| R51–R55 | §3 V23 | 1.4 B2, B3 |
| R56–R59 | §3 V23, §0 D8 | 1.4 automated (fixture) |
| E1, E2 | §5.5, §5.6 | 1.6 B7 |
| E3, E4 | no constraint | 1.6 B5 (duplicate name allowed) |
| E5 | §5.6 | 1.7 B1 |
| E6 | unchanged | 1.6 B9 |
| E7 | §5.3 | 1.6 B4 |
| E8 | §5.2 | 1.5 B6; 1.8 B1 |
| E9, E10, E11 | §3 V24 content | 1.5 B3, B4 |
| E12 | §3 V23 | 1.4 B2 |
| E13, E22 | §4 | 1.3 B5, B6 |
| E14, E15 | §5.2 | 1.8 B3, B4 |
| E16, E17 | §5.2–§5.6 | 1.6 B2, B4, B5, B8 |
| E18 | §5.3, §5.7 | 1.6 B3; 1.9 B1 |
| E19, E20 | §5.7 | 1.9 B2, B4 |
| E21 | §5.5, §5.6 | 1.6 B8 |
| E23 | §3 V23 | 1.4 automated (fixture) |

## 3. Report format

```
Verification of vertice-api exercise-starter-catalog — PR<n> (<branch/commit>)
PASS <count> · FAIL <count> · NOT CHECKED <count>

FAIL 1.6 B6: expected InvalidArgument, got PermissionDenied. Evidence: <grpcurl output>.
NOT CHECKED 1.9: PR9 branch does not exist yet.
...
Verdict: <matches spec | does not match spec — <one line>>
```
