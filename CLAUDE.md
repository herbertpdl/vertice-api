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
config/                Spring Security wiring (JWT resource server, local-profile auth bypass)
common/                Shared exceptions and Bean Validation constraints (e.g. @Cpf)
```

### Authentication: JWT, wired separately for REST and gRPC

Auth is JWT via OAuth2 resource server (`spring.security.oauth2.resourceserver.jwt.issuer-uri`),
flat "any authenticated caller may do anything" — no role/scope differentiation yet. Under the
`local` profile it's disabled entirely (`config/LocalSecurityConfig` for REST,
`grpc/GrpcSecurityConfig`'s `local`-profile bean for gRPC) so endpoints can be exercised manually
without a running JWT issuer. The two transports need separate wiring: Spring Boot's default gRPC
OAuth2 auto-config is `@ConditionalOnMissingBean(AuthenticationProcessInterceptor.class)`, so
`GrpcSecurityConfig` defining its own bean is what makes the local-profile bypass exist for gRPC
at all — it doesn't inherit anything from the REST-side `SecurityConfig`/`LocalSecurityConfig`.

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
  `GrpcExceptionAdvice` end-to-end over the wire.

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

## Branching

Always branch from an up-to-date `main` (check and pull/update `main` before creating a new
branch), and open PRs back against `main`.
