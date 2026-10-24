# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Backend API for Vertice Coach: personal trainers build training plans for clients; clients follow
them, log workouts, and track progress. All business functionality is exposed over **gRPC only**
(`.proto` files under `src/main/proto/vertice`); the HTTP port serves nothing but Spring Boot
Actuator health/info.

Java 25, Spring Boot 4.1, Spring Data JPA + PostgreSQL, Flyway, MapStruct, Lombok. See
`README.md` for the full tech stack and local-run instructions (including running the full stack
via the sibling `vertice-local` repo).

## Commands

```sh
docker compose up -d                                   # Postgres only, required for tests too
./gradlew bootRun --args='--spring.profiles.active=local'  # run locally, auth disabled, grpc reflection on
./gradlew test                                          # run all tests (needs Postgres on localhost:5432)
./gradlew test --tests "com.vertice.api.trainerclient.TrainerClientServiceTest"   # single test class
./gradlew test --tests "*TrainerClientServiceTest.createClientForTrainer_persistsRelationship"  # single test method
./gradlew build                                          # full build: compile, generate proto sources, test — what CI runs
```

There is no linter/formatter task configured (no Checkstyle/Spotless in `build.gradle`).

CI (`.github/workflows/ci.yml`) has two jobs: `build-and-test` (`buf lint`, `buf breaking`
against the PR's base on pull requests, then `./gradlew build`; the required check, so don't rename
it) and `dependency-scan` (Grype on the boot jar, fails on high/critical
runtime advisories). A failing scan is usually fixed by bumping the Spring Boot plugin or, when
Boot hasn't caught up, a `set('<lib>.version', …)` override in `build.gradle`'s `ext` block;
drop the override once Boot manages that version. Dependabot (`.github/dependabot.yml`) covers
gradle and github-actions weekly.
Third-party actions (anything outside `actions/*`) are pinned by full commit SHA with a version
comment; Dependabot bumps both together.

## Architecture

### Vertical slices, not layers-first packages

Code is organized by domain aggregate under `src/main/java/com/vertice/api/`, each with its own
`Controller` (gRPC endpoint), `Service` (business logic + `@Transactional`), `Mapper` (MapStruct,
entity ↔ proto), `Repository` (Spring Data JPA), and JPA entity, e.g.:

```
plan/              TrainingPlan
plan/workout/       Workout, WorkoutExercise, ExerciseSet
plan/exercise/       Exercise catalog
plan/session/        WorkoutLog, SetLog, WorkoutFeedback (client-facing logging/feedback)
trainerclient/        Trainer↔client relationship (join between two Users)
user/                  User (trainer/client, unified by Role)
grpc/                  Cross-cutting: exception mapping, health, request validation, proto conversion helpers
config/                Spring Security wiring (shared-secret JWT decoder, local-profile auth bypass) and boot
                       guards (BootGuards → JwtSecretGuard / LocalProfileGuard)
common/                Shared exceptions and Bean Validation constraints (e.g. @Cpf)
```

### Authentication: JWT, wired separately for REST and gRPC

Auth is JWT via OAuth2 resource server: the caller (vertice-bff) sends its own HS256 JWT as
`authorization: Bearer <token>`, verified with the shared secret `vertice.jwt.secret`
(`JWT_SECRET`, same value on both sides; shared dev default
`vertice-dev-jwt-secret-do-not-use-in-production`, same as vertice-bff's) by
`config/HmacJwtDecoder` — a hand-rolled decoder because Nimbus refuses secrets under 32 bytes and
`local` accepts any secret. Outside `local`, `config/JwtSecretGuard` refuses to boot on a blank,
whitespace-padded, sub-32-byte or placeholder secret (dev default included; list mirrors
vertice-bff's `src/config/env.ts`). Non-local requires a
valid token on every call. Under the `local` profile REST auth is disabled entirely
(`config/LocalSecurityConfig`), and gRPC (`grpc/GrpcSecurityConfig`'s `local`-profile bean)
permits every call but still decodes a bearer token when one is present — an absent token is
anonymous, a present but invalid one is `UNAUTHENTICATED`. The decoder requires every token to be
scoped: HS256 only, `typ` absent or `JWT`, `exp` present (60 s clock skew), `iss == "vertice-bff"`, `aud` containing `"vertice-api"`
(`HmacJwtDecoder.ISSUER`/`AUDIENCE`); the same checks run under `local` whenever a token is sent.
Tests mint scoped tokens with `src/test/.../grpc/TestJwts` (`token`, `serviceToken`, `asCaller`,
`asService`).

Under `local` (even combined, e.g. `local,prod`), `config/LocalProfileGuard` refuses to boot when
`VERTICE_ENV` is set to anything but `local`/`dev`, or when `spring.datasource.url`'s host isn't
`localhost`/`127.0.0.1`/`::1`/`postgres` (unparseable URLs fail closed), so `local`-profile tests
must keep a localhost datasource. `postgres` may also be a deployed DB's hostname, so deployed
environments must set `VERTICE_ENV`. Both guards are plain classes run by `config/BootGuards`, an
`EnvironmentPostProcessor` registered in `META-INF/spring.factories`: they fire before any bean
exists (so before Hikari/Flyway touch the database, and regardless of lazy initialization). Keep
new boot-time checks there rather than in `@PostConstruct` beans. The Gradle test task drops
`VERTICE_ENV` and `JWT_SECRET` from the test JVM's environment; tests that boot the guards (`config/GuardBoot`)
pass every property they depend on as command-line args so the developer's shell can't leak in.
The two transports need separate wiring: Spring Boot's default gRPC
OAuth2 auto-config is `@ConditionalOnMissingBean(AuthenticationProcessInterceptor.class)`, so
`GrpcSecurityConfig` defining its own bean is what makes the local-profile bypass exist for gRPC
at all — it doesn't inherit anything from the REST-side `SecurityConfig`/`LocalSecurityConfig`.

### Trust model: BFF = first layer, API = authoritative layer

vertice-bff checks role and ownership before calling, but the API must hold on its own: a token
used directly against `:9090` gets exactly what the matrix below allows. `grpc/CallerIdentityResolver`
turns the token into a `grpc/Caller` (sealed): a user (`CallerIdentity`: numeric `sub`, legacy
`id` accepted only when equal to `sub`, `role` ADMIN/TRAINER/CLIENT), a service
(`Caller.Service`: `sub` `service:bff` or `service:ops` with `role` `SERVICE`, see `ServiceSubject`),
or `Caller.Anonymous` (no token, only reachable under `local`). Any other subject/role is
`UNAUTHENTICATED`. **Roles are re-checked against the DB on every call:** a user caller must still
exist, not deleted (`deleted_at IS NULL`, set by `DeleteAccount`), with exactly the token's `role`
(`UserRepository#findRoleById`, one primary-key query of the role column), otherwise
`UNAUTHENTICATED` "Session no longer valid", which the BFF maps to 401 and the web answers by
logging out. So a deleted or re-roled user loses access on their next call, not
when the 8 h token expires. The lookup runs once per RPC (`grpc/CallerScopeInterceptor` keeps the
resolved caller in the call's gRPC `Context`, never across calls); services and anonymous callers
skip it. Tests therefore need their user callers to exist: integration tests save them, and
controller tests that mock `UserRepository` stub `findRoleById` (`PlanCallers.storeUsers`). Controllers call `resolve()`, `requireUser()` (service → `PERMISSION_DENIED`,
anonymous → `UNAUTHENTICATED`), `requireUserOrAnonymous()` (reference data) or
`requireService(...)` and pass the caller on; the rules themselves throw
`PermissionDeniedException` → `PERMISSION_DENIED`. Each aggregate has an authorization class
called by its controller after request validation: `user/UserAuthorization` (one `check*` per
RPC), `trainerclient/TrainerClientAuthorization`, `plan/TrainingPlanAuthorization`,
`plan/workout/WorkoutAuthorization` (workouts, workout exercises, sets) and
`plan/session/WorkoutSessionAuthorization` (logs, set logs, feedback). The plan-tree ones are
built from `plan/PlanAccess` over `plan/PlanAccessRepository` (one projection query: the plan's
trainer and client for any id in the tree). The exercise RPCs pass the `requireUser()` caller to
`ExerciseService`, which applies the catalog's role and ownership rules.

**Every RPC must ask who is calling.** `grpc/RpcAuthorizationCoverageTest` reads every method of
every bound service and calls it under `local` with no token, as `service:bff` and as
`service:ops`; anything the matrix doesn't allow must answer `UNAUTHENTICATED`/`PERMISSION_DENIED`.
A new RPC is covered automatically: resolve the caller before validating (or add a valid request
to `VALID_REQUESTS`), and add it to `ALLOWED` only if the token contract lets a service or no
token call it. Its owner/non-owner rules still need their own controller and integration tests.
`grpc/ClientRefusalCoverageTest` is the same probe for the CLIENT role (web-trainer-only-access
R12–R17): every bound RPC outside its `CLIENT_ALLOWED` list is called by the client of a real plan,
with every id field filled from that plan's tree, and must answer `PERMISSION_DENIED`, leaving
every table's row count and the content of the seeded rows unchanged. A new RPC a client may call goes into `CLIENT_ALLOWED`; any other is covered
automatically.

Plan-tree conventions: every RPC needs a user token (`requireUser()`: service → `PERMISSION_DENIED`,
no token → `UNAUTHENTICATED`). A plan's trainer reads and writes it and everything under it, its
client only reads, ADMIN bypasses. A workout log belongs to its client: only they (or ADMIN)
start it, record sets, complete it or send feedback; the plan's trainer reads it. A `trainer_id`/`client_id`
in a request is the caller's own for a caller with that role (`0` means "me" where `0` is
accepted); any other id is `PERMISSION_DENIED`, never silently rewritten. **No existence oracle:**
to anyone but ADMIN a missing id and someone else's id are the same `PERMISSION_DENIED` (`You do
not have access to <resource> <id>`), decided before the service loads anything; ADMIN skips the
lookup and gets the service's `NOT_FOUND`. An exercise put into a workout must be visible to the
plan's trainer (starter set or their own, `Exercise#isVisibleToTrainer`; spec
exercise-starter-catalog PR9): another trainer's is `PERMISSION_DENIED`, a missing one keeps its
existing `NOT_FOUND`/`INVALID_ARGUMENT` (the exercise RPCs already tell those apart).

| RPC | Allowed callers |
| --- | --- |
| `VerifyCredentials` | `service:bff` (login); a user for their own stored email only (`POST /auth/password`) |
| `CreateUser` | `service:bff` (register), never with role ADMIN |
| `RecordConsent` | any user, for themself only (the request has no user id; ADMIN can't consent for anyone else); never a service |
| `SetPasswordWithToken` | `service:bff` (deprecated: use `RedeemPasswordSetupToken`) |
| `RedeemPasswordSetupToken` | `service:bff`; returns the token's user (contact view) only after the token is consumed |
| `ListUsers` | ADMIN, `service:ops`; anonymous under `local` (BFF `VERIFY_PASSWORDS=false` dev login). Paginated (`page_size` ≤ 100, default 100; `page_token`) |
| `ListUsersWithoutPassword` | ADMIN (all); TRAINER, narrowed to their active clients (BFF setup-link reissue) |
| `CreatePasswordSetupToken` | `service:ops`, ADMIN; TRAINER for their active client who has no password yet |
| `GetUser` | self (self view), ADMIN, the target's active trainer (detail view); a trainer with any plan for the target gets the summary view (dashboard names of former clients) |
| `UpdateUser` | ADMIN (anything); self or the target's active trainer, role unchanged |
| `SetUserPassword` | self, ADMIN |
| `DeleteUser` | self, ADMIN; FAILED_PRECONDITION while the user has training data (plans, logs, clients, exercises) |
| `DeleteAccount` | any user, for their own account only (the request has no id); never a service. A TRAINER with active clients gets FAILED_PRECONDITION |
| `ExportUserData` | any user, for their own data only (the request has no id); never a service. RESOURCE_EXHAUSTED past `vertice.export.*` |
| `ListTrainingPlans` | TRAINER (own plans), CLIENT (plans for them), ADMIN (as filtered) |
| `GetTrainingPlan`, `ListWorkouts`, `GetWorkout`, `ListWorkoutExercises`, `GetWorkoutExercise`, `ListExerciseSets`, `GetExerciseSet` | the plan's trainer or client, ADMIN |
| `CreateTrainingPlan` | TRAINER as themself (client on their active roster), ADMIN |
| `UpdateTrainingPlan`, `DeleteTrainingPlan`, `CreateWorkout`, `CreateWorkoutWithExercises`, `UpdateWorkout`, `DeleteWorkout`, `ReplaceWorkoutExercises`, `CreateWorkoutExercise`, `UpdateWorkoutExercise`, `DeleteWorkoutExercise`, `CreateExerciseSet`, `UpdateExerciseSet`, `DeleteExerciseSet` | the plan's trainer, ADMIN |
| `CloneWorkout` | the trainer of both the source workout's plan and the target plan, ADMIN |
| `ListWorkoutLogs`, `GetLastSetLogs` | the plan's trainer, the plan's client (as themself), ADMIN |
| `GetOrStartWorkoutLog`, `RestartWorkoutLog` | the plan's client (as themself), ADMIN; never a TRAINER (web-trainer-only-access R15) |
| `GetWorkoutLog`, `ListSetLogs` | the log's client, the plan's trainer, ADMIN |
| `RecordSetLog`, `DeleteSetLog`, `CompleteWorkoutLog`, `DiscardWorkoutLog`, `SubmitWorkoutFeedback` | the log's client, ADMIN |
| `GetExerciseProgress` | the client themself, their active trainer, ADMIN |
| `ListWorkoutFeedback` | TRAINER for their own `trainer_id`, ADMIN |
| `CreateClientForTrainer`, `ListClientsForTrainer`, `IsTrainersClient` | TRAINER for their own `trainer_id`, ADMIN (any trainer); never a CLIENT or a service |
| `GetClientTrainer` | CLIENT as themself (the request's `client_id` 0 or their own), ADMIN; never a TRAINER |
| `EndTrainerClientRelationship` | TRAINER for their own `trainer_id` and a client on their active roster, ADMIN (any active relationship); never a CLIENT or a service |
| `ListExercises` | TRAINER (starter set + own), ADMIN (starter set) |
| `GetExercise` | TRAINER (starter set or own), ADMIN (starter set), a CLIENT for an exercise in one of their workouts |
| `CreateExercise`, `UpdateExercise`, `DeleteExercise` | TRAINER, on their own exercises only (the starter set is read-only) |
| `ListMuscleGroups` | any user; anonymous under `local` |
| `grpc.health.v1.Health/Check` | any valid token (anonymous under `local`); infrastructure, carries no data |

**Personal data in responses (P1-03).** Every RPC returning a user picks one `user/UserView`; the
`UserResponse` shape stays the same and the fields outside the view are empty, so older clients
still decode it. Detail (email, CPF, CREF) only ever goes to the user themself, ADMIN or the
target's active trainer; a list never carries a CPF or CREF. Duplicate email/CPF errors are fixed
messages (`Email already in use`, `CPF already in use`) that never echo the value.

| View | Fields | RPCs |
| --- | --- | --- |
| self | everything, consent state included | `GetUser` for the user themself |
| detail | everything but the consent state | `GetUser` (ADMIN, active trainer), `UpdateUser`, `CreateClientForTrainer` |
| contact | id, name, role, email | `ListUsers` (ops setup links, local dev login match by email), `CreateUser`, `VerifyCredentials` (the BFF's session), `RedeemPasswordSetupToken` (the web's first-access screen) |
| summary | id, name, role | `ListClientsForTrainer` (the BFF roster), `ListUsersWithoutPassword`, `GetUser` for a trainer who only has a plan with the target |

**Consent and CPF (P1-04).** `users` records the Terms version a user accepted
(`accepted_terms_version`, `terms_accepted_at`, set by `CreateUser` or `RecordConsent(TERMS)`) and
the health-data consent (`health_data_consent_version`, `health_data_consent_at`,
`RecordConsent(HEALTH_DATA)`), each version and time together (V27 check constraints). `CreateUser`
requires the version (INVALID_ARGUMENT when empty): no account without an acceptance on record. CPF is optional for a CLIENT and required for a
TRAINER (`UserService`, backstopped by V27's `ck_users_trainer_cpf`); an empty CPF is stored as
NULL, and `uq_users_cpf` is a partial unique index over set values. `docs/data-inventory.md` lists
every personal-data column with its purpose, legal basis, retention and readers; its product/legal
decisions are pending legal review.

**Account deletion (P1-05, D5).** `DeleteAccount` (`user/AccountDeletionService`, one transaction,
native statements in `user/AccountDeletionRepository` in foreign-key order) deletes the caller's
feedback, set logs, workout logs, the plans written for them and their unused private exercises,
ends their relationships, deletes their setup tokens and anonymises the `users` row
(`User#anonymise`: name `Usuário removido`, email/CPF/CREF/password/consent set to NULL,
`deleted_at` set; V29 relaxes `ck_users_trainer_cpf` and V30 `email NOT NULL` for deleted rows
only). The email is NULL, never a placeholder: a predictable one could be claimed by a live account
first and block the deletion on `uq_users_email_lower`. Addresses on the `.invalid` TLD are refused
on every write (`Emails#isReserved`, INVALID_ARGUMENT) as defence in depth. The row stays because
plans the user wrote for others point at it. A deleted account is missing everywhere:
`findRoleById` (tokens die), `UserService#findByIdOrThrow` (GetUser, UpdateUser, RecordConsent,
SetUserPassword, DeleteUser are `NOT_FOUND`, ADMIN included), the email lookup (VerifyCredentials),
`ListUsers`, `ListUsersWithoutPassword`, `CreatePasswordSetupToken`, and every lookup that would
attach new data to a user: `CreateClientForTrainer` (the trainer), `CreateTrainingPlan` (trainer or
client) and `GetOrStartWorkoutLog` (the client) are `NOT_FOUND` for a deleted one. Any new lookup
of a user that writes must filter `User#isDeleted` too. Each deletion logs one INFO line with
actor id, user id and role only.

**Data export (P1-05).** `ExportUserData` (`user/UserDataExportService`) returns one JSON document
(`user/UserDataExport` records, `formatVersion` bumped on any renamed or removed key) with
everything about the caller: profile, consent, relationships, plans as client or trainer with the
whole tree, their own logs/set logs/feedback, private exercises. Never credentials, never a
trainer's clients' sessions. `user/UserDataExportQueries` is plain SQL, one query per section with
`LIMIT max-rows + 1`, in one read-only `REPEATABLE READ` transaction; the JSON is written into a
`user/BoundedOutputStream` that refuses the write crossing `max-size`, so an oversized document is
never built whole. Past `vertice.export.max-rows` or `max-size` it is `RESOURCE_EXHAUSTED`
(`ExportTooLargeException`), never truncated. Its timestamp columns are `TIMESTAMPTZ`, so a raw-SQL
read of one uses `getObject(column, OffsetDateTime.class).toInstant()` — see "Time: instants vs.
business dates".

"Active trainer" means a `trainer_clients` row with `ended_at IS NULL` and the target in role
CLIENT. `EndTrainerClientRelationship` sets `ended_at` and nothing else (P1-05): the client's
account, the plans and every log stay. The trainer then loses everything an active link grants
(roster, detail view, `GetExerciseProgress`, setup links, `CreateTrainingPlan`/`UpdateTrainingPlan`
for that client) and keeps what the plan-tree rules give a plan's trainer, which don't look at the
link: reading the plans they wrote and the logs under them, the summary `GetUser` view, and the
plan-tree writes other than `UpdateTrainingPlan`. The client keeps reading those plans.
Role changes are ADMIN-only. Non-local test contexts must set a valid (≥32-byte,
non-placeholder) `vertice.jwt.secret`. `CreatePasswordSetupToken` is effectively a password-reset
primitive, hence its narrow rule; see `docs/specs/password-lifecycle/spec.md`.

`docs/domain-model.md` is the map for how `TrainingPlan → Workout → WorkoutExercise → ExerciseSet`
nest and how the `Exercise` catalog fits in — read it before touching that hierarchy, the entity
names are easy to confuse.

### "Controller" means gRPC service impl, not REST

Every `*Controller` class (e.g. `TrainerClientController`) is a `@GrpcService` extending a
generated `*ServiceGrpc.*ImplBase`, not a Spring MVC `@RestController`. There is no REST layer in
this codebase beyond Actuator. Don't assume MVC conventions (`@RequestBody`, `@Valid`, etc.) apply.

### The protos are the BFF's contract

vertice-bff keeps a copy of `src/main/proto/vertice/**/*.proto` under its `protos/vertice/`, taken at
a pinned vertice-api commit (to be refreshed by its `npm run sync:protos`, coming in the BFF's
P2-01 PR; README "Proto contract"). So a proto change here is a contract change: `buf breaking`
(`FILE` rules, `buf.yaml`) fails the PR on a deleted/renumbered field, rename or type change.
Evolve additively (new fields with new numbers, `reserved` for removed ones); comment-only edits
pass. Lint is `STANDARD` minus four naming rules the existing protos predate; new protos should
follow them anyway (`<Rpc>Request`/`<Rpc>Response`, enum values prefixed with the enum name).
After merging, sync the BFF copy.

### Generated proto code

Proto-generated Java lands under `com.vertice.api.generated.grpc.*` (not colocated with
hand-written code) — mirrors the `.proto` package path, e.g. `trainerclient/v1/trainer_client.proto`
→ `com.vertice.api.generated.grpc.trainerclient.v1`. Generated after `./gradlew generateProto`
(part of `build`/`test`/`bootRun`).

### Request validation: manual, not `@Valid`

gRPC handlers build request objects by hand from proto fields, so Bean Validation doesn't run
automatically the way it does for REST's `@RequestBody`. The pattern (see any `*Controller`):
define a private `record` with Bean Validation annotations mirroring the fields to validate, then
call `GrpcRequestValidator#validate(record)` before delegating to the service. `@Cpf` and
`@Password` are custom constraints under `common/validation`.

Every free-text field has a `@Size(max = …)` equal to its `VARCHAR(n)` column, and every quantity
(reps, seconds, order, set number) an `@Max` (P1-09). `@Size` counts UTF-16 units, as JavaScript's `.length` does in the BFF and web, so a
character outside the BMP counts twice and the bound is never looser than Postgres's. The
workout-tree numbers and the caps (20 exercises per workout, 10 sets per exercise, on the per-item
RPCs too) live in `plan/workout/WorkoutLimits`; decimal precision per column in `ProtoDecimals`.
vertice-bff and vertice-web-react mirror these numbers, so change them together.

### Exception mapping: two parallel handlers, same exception types

`GrpcExceptionAdvice` (`@GrpcAdvice`, in `grpc/`) is the gRPC-native counterpart to
`GlobalExceptionHandler` (`@RestControllerAdvice`, for Actuator) — same domain exceptions
(`ResourceNotFoundException`, `DuplicateEmailException`, `DuplicateCpfException`,
`ConstraintViolationException`), mapped to `Status` codes instead of `ProblemDetail`. Add new
domain exceptions to both if they can surface from gRPC-reachable code paths.

A delete that a foreign key would refuse is checked first and refused with
`HasRecordedDataException` → `FAILED_PRECONDITION` (`<Resource> <id> has <what> and cannot be
deleted`): a plan or workout with workout logs, a workout exercise or set with set logs, a user
with training data (`ExerciseInUseException` is the catalog's equivalent). Behind that,
`GrpcExceptionAdvice` maps any `DataIntegrityViolationException` (a missed check, or a race past
one): unique key → `ALREADY_EXISTS` (the fixed duplicate email/CPF message for those indexes),
foreign key → `FAILED_PRECONDITION`, anything else → `INVALID_ARGUMENT` (logged at ERROR: a
validation gap, while the unique/FK races are WARN). The description is fixed text and the log
line carries only the SQLSTATE and constraint name. Postgres error text can carry input values, so
`application.properties` sets pgjdbc's `logServerErrorDetail=false` (no `Detail: Key (cpf)=(…)` in
exception messages) and turns `org.hibernate.orm.jdbc.error` off; keep both.

### Proto ↔ Java conversion helpers (`grpc/Proto*`)

Proto3 has no null and no `BigDecimal`/`Instant`/nullable-string equivalents, so conversions are
centralized rather than repeated per mapper: `ProtoDates`, `ProtoInstants`, `ProtoDecimals`
(decimal fields cross the wire as strings — blank means "unset"), `ProtoStrings`. MapStruct
mappers reference these via `@Named` methods. Enum zero-values (e.g. `DAY_OF_WEEK_UNSPECIFIED`)
map to Java `null` via `@ValueMapping(source = "..._UNSPECIFIED", target = MappingConstants.NULL)`
— follow this same pattern for any new proto enum requiring a Java-side mapping.

### Time: instants vs. business dates

Two different kinds of value, and conflating them is what XREPO-006/REL-API-008 were (P2-02).
**The business time zone is `America/Sao_Paulo`.** The rule the BFF and the web mirror
(`vertice-bff/docs/api-contract.md`):

| Kind | Java | Column | Over the wire | Examples |
| --- | --- | --- | --- | --- |
| **Instant** — a point in time | `Instant` | `TIMESTAMPTZ` | ISO-8601 with the `Z` designator (`ProtoInstants`) | `started_at`, `completed_at`, `recorded_at`, `created_at`, `updated_at`, `deleted_at`, `terms_accepted_at`, `expires_at` |
| **Business date** — a local calendar day in `America/Sao_Paulo` | `LocalDate` | `DATE` | `yyyy-MM-dd`, no zone, no time (`ProtoDates`) | `training_plans.start_date`/`end_date`, `workout_logs.week_start_date` |

- **Instants are stored as instants.** V31 moved every `Instant` column to `TIMESTAMPTZ`. Hibernate
  already bound an `Instant` as UTC whatever the JVM's zone, but a bare `TIMESTAMP` left the zone as
  an unenforced convention, and the DB-side `DEFAULT now()` clauses genuinely got it wrong (`now()`
  is `TIMESTAMPTZ`; casting it into `TIMESTAMP` uses the session's zone, which pgjdbc takes from the
  JVM's). A new `Instant` column must be `TIMESTAMPTZ`. In raw SQL, read one as
  `OffsetDateTime` (`UserDataExportQueries#instant`) and write one with an explicit offset — a bare
  `TIMESTAMP '…'` literal is resolved against the session's zone. Reading with `getTimestamp` is
  correct for a `TIMESTAMPTZ` column; `OffsetDateTime` is the house style, to stay on `java.time`.
- **Converting an `Instant` column rewrites its table.** `ALTER COLUMN … TYPE timestamptz` takes
  ACCESS EXCLUSIVE and rewrites, and Flyway runs a migration file in one transaction, so the locks
  on every table in the file are held until the last one finishes. Size the window against
  `set_logs`/`workout_logs` and set `lock_timeout` for the deploy — see V31's header.
- **Business dates are never derived from an instant here.** The API computes no `today` and no week
  start: `week_start_date` (validated to be a Monday) and a plan's `start_date`/`end_date` arrive as
  `yyyy-MM-dd` from the caller and are stored as `DATE`, so no zone can shift them. Deriving the
  local day of an instant is the caller's job, and it must use `America/Sao_Paulo`, not a UTC slice
  (`new Date().toISOString().slice(0,10)` is the bug this replaced). If the API ever needs its own
  "today", it must take the zone explicitly — never `LocalDate.now()`.
- **Never assume a fixed −03:00.** Brazil observed DST until 2019, so rows from before then were
  recorded at −02:00. Use the `America/Sao_Paulo` zone rules, not an offset.
- **Correctness must not depend on the host.** No `TZ`, `user.timezone` or
  `hibernate.jdbc.time_zone` setting is required, and none may become required.
  `common/time/BusinessTimeZoneIntegrationTest` states the whole contract and runs twice: in the
  ordinary `test` task, and again under the `hostileTimeZoneTest` Gradle task with the JVM pinned to
  `Pacific/Kiritimati` (UTC+14, across the date line from the business zone). `check` depends on it,
  so `./gradlew build` runs both. Add to that test rather than starting a new one.
- Historic rows are left as they are: V31 reinterprets stored values as the UTC they already were
  (`USING col AT TIME ZONE 'UTC'`) and rewrites no data. Logs keyed by a UTC-derived week keep their
  key; the rule applies going forward.

### Testing pattern: one Service test + one Controller test per aggregate

- `*ServiceTest`: plain Mockito unit test (`@ExtendWith(MockitoExtension.class)`), mapper
  instantiated directly via `Mappers.getMapper(...)` rather than mocked.
- `*ControllerTest`: `@SpringBootTest` bringing up a real gRPC server on a fixed test port
  (`spring.grpc.server.port=...`, one distinct port per test class), talking to it through a real
  `ManagedChannel`/blocking stub, with the `Service` layer replaced via `@MockitoBean`. Runs
  `@ActiveProfiles("local")`. This is what exercises `GrpcRequestValidator` and
  `GrpcExceptionAdvice` end-to-end over the wire. Calls send the caller an RPC's rules require
  (`TestJwts.asCaller`/`asService`); authorization tests cover allowed and denied callers per RPC.

### Migrations

Flyway migrations live in `src/main/resources/db/migration`, run automatically on startup.
Version numbers are sequential but not zero-padded (`V9` then `V10`) — check the highest existing
number before adding one, don't infer ordering from string sort.

## Feature workflow: write a spec first

Non-trivial new features get a spec under `docs/specs/<feature-name>/spec.md` before/alongside
implementation — see existing ones (e.g. `docs/specs/clone-workout/spec.md`) for the format:
`Status`/`Owner`/`Related` header, a `## 0. Scope decisions` section stating the non-obvious
choices and *why* (not just the what), then the design. `docs/requirements.md` is the source
product requirements those specs trace back to. When implementing a feature that doesn't already
have a spec, write one first in this format rather than jumping straight to code.

Specs are the *technical* design. The *product* definition a spec is derived from is a PRD under
`docs/prds/<feature-name>/prd.md` (same slug as the spec folder), produced with the `/prd-creator` skill
(`.claude/skills/prd-creator/`): it interviews the owner until the rules and edge cases are exhausted and
contains no technical design. When a PRD exists for a feature, the spec's `Related` line must
point at it and every spec decision must trace to a PRD rule, edge case, or decision.

Between the PRD and the spec sits a technical assessment under
`docs/assessments/<feature-name>/assessment.md` (same slug), produced with the
`/technical-assessment` skill (`.claude/skills/technical-assessment/`): it maps every PRD rule
onto the codebase and records evidence-backed findings on security, data model, compatibility,
performance, logging, metrics, testing, and rollout. When one exists, the spec's `Related` line
also points at it and its `## 0. Scope decisions` must resolve every Blocker and High finding.

## Branching

Always branch from an up-to-date `main` (check and pull/update `main` before creating a new
branch), and open PRs back against `main`.
