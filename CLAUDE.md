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

CI (`.github/workflows/ci.yml`) has two jobs: `build-and-test` (`./gradlew build`; the required
check, so don't rename it) and `dependency-scan` (Grype on the boot jar, fails on high/critical
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
scoped: HS256 only, `exp` present, `iss == "vertice-bff"`, `aud` containing `"vertice-api"`
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
`UNAUTHENTICATED`. Controllers call `resolve()`, `requireUser()` (service → `PERMISSION_DENIED`,
anonymous → `UNAUTHENTICATED`) or `requireService(...)` and pass the caller on; the rules themselves
throw `PermissionDeniedException` → `PERMISSION_DENIED`. The user domain's rules live in
`user/UserAuthorization` (one `check*` per RPC, called by `UserController` after request
validation). The exercise RPCs use `requireUser()`. Plans, workouts, sessions, feedback and
trainer-client RPCs still trust caller-supplied ids until P1-01 PR B/C.

| RPC | Allowed callers |
| --- | --- |
| `VerifyCredentials` | `service:bff` (login); a user for their own stored email only (`POST /auth/password`) |
| `CreateUser` | `service:bff` (register), never with role ADMIN |
| `SetPasswordWithToken` | `service:bff` |
| `ListUsers` | ADMIN, `service:ops`; anonymous under `local` (BFF `VERIFY_PASSWORDS=false` dev login) |
| `ListUsersWithoutPassword` | ADMIN, `service:ops` (all); TRAINER, narrowed to their active clients (BFF setup-link reissue) |
| `CreatePasswordSetupToken` | `service:ops`, ADMIN; TRAINER for their active client who has no password yet |
| `GetUser` | self, ADMIN, the target's active trainer |
| `UpdateUser` | ADMIN (anything); self or the target's active trainer, role unchanged |
| `SetUserPassword` | self, ADMIN |
| `DeleteUser` | self, ADMIN |

"Active trainer" means a `trainer_clients` row with `ended_at IS NULL` and the target in role
CLIENT. Role changes are ADMIN-only. Non-local test contexts must set a valid (≥32-byte,
non-placeholder) `vertice.jwt.secret`. `CreatePasswordSetupToken` is effectively a password-reset
primitive, hence its narrow rule; see `docs/specs/password-lifecycle/spec.md`.

`docs/domain-model.md` is the map for how `TrainingPlan → Workout → WorkoutExercise → ExerciseSet`
nest and how the `Exercise` catalog fits in — read it before touching that hierarchy, the entity
names are easy to confuse.

### "Controller" means gRPC service impl, not REST

Every `*Controller` class (e.g. `TrainerClientController`) is a `@GrpcService` extending a
generated `*ServiceGrpc.*ImplBase`, not a Spring MVC `@RestController`. There is no REST layer in
this codebase beyond Actuator. Don't assume MVC conventions (`@RequestBody`, `@Valid`, etc.) apply.

### Generated proto code

Proto-generated Java lands under `com.vertice.api.generated.grpc.*` (not colocated with
hand-written code) — mirrors the `.proto` package path, e.g. `trainerclient/v1/trainer_client.proto`
→ `com.vertice.api.generated.grpc.trainerclient.v1`. Generated after `./gradlew generateProto`
(part of `build`/`test`/`bootRun`).

### Request validation: manual, not `@Valid`

gRPC handlers build request objects by hand from proto fields, so Bean Validation doesn't run
automatically the way it does for REST's `@RequestBody`. The pattern (see any `*Controller`):
define a private `record` with Bean Validation annotations mirroring the fields to validate, then
call `GrpcRequestValidator#validate(record)` before delegating to the service. `@Cpf` is a custom
constraint under `common/validation`.

### Exception mapping: two parallel handlers, same exception types

`GrpcExceptionAdvice` (`@GrpcAdvice`, in `grpc/`) is the gRPC-native counterpart to
`GlobalExceptionHandler` (`@RestControllerAdvice`, for Actuator) — same domain exceptions
(`ResourceNotFoundException`, `DuplicateEmailException`, `DuplicateCpfException`,
`ConstraintViolationException`), mapped to `Status` codes instead of `ProblemDetail`. Add new
domain exceptions to both if they can surface from gRPC-reachable code paths.

### Proto ↔ Java conversion helpers (`grpc/Proto*`)

Proto3 has no null and no `BigDecimal`/`Instant`/nullable-string equivalents, so conversions are
centralized rather than repeated per mapper: `ProtoDates`, `ProtoInstants`, `ProtoDecimals`
(decimal fields cross the wire as strings — blank means "unset"), `ProtoStrings`. MapStruct
mappers reference these via `@Named` methods. Enum zero-values (e.g. `DAY_OF_WEEK_UNSPECIFIED`)
map to Java `null` via `@ValueMapping(source = "..._UNSPECIFIED", target = MappingConstants.NULL)`
— follow this same pattern for any new proto enum requiring a Java-side mapping.

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
