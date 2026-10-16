# vertice-api

Backend API for Vertice Coach, a platform for personal trainers to build and manage training
plans for their clients, and for clients to follow those plans, log their workouts, and track
their progress.

The service exposes its functionality entirely over gRPC (see [API](#api)); the HTTP port only
serves Spring Boot Actuator health/info endpoints.

## Tech stack

- Java 25 (via Gradle toolchain), Spring Boot 4.1
- gRPC (`spring-boot-starter-grpc-server`), Protocol Buffers (`com.google.protobuf` Gradle plugin)
- Spring Data JPA + PostgreSQL, Flyway for schema migrations
- Spring Security with an OAuth2/JWT resource server (disabled under the `local` profile)
- MapStruct for entity/DTO(proto) mapping, Lombok, Bean Validation
- Gradle (wrapper included, no local Gradle install required)

## Domain

Personal trainers create `TrainingPlan`s for their clients. Each plan is made up of `Workout`s
(specific training days), and each workout contains `WorkoutExercise`s (an `Exercise` from a
shared catalog, placed into that workout) with their own `ExerciseSet`s (reps/weight/strategy per
set). Clients log completed workouts (`WorkoutLog`/`SetLog`), leave feedback, and can view their
weight progress per exercise over time.

See [`docs/domain-model.md`](docs/domain-model.md) for the full entity breakdown and
[`docs/requirements.md`](docs/requirements.md) for the product requirements this API implements.
Individual features are documented as specs under [`docs/specs/`](docs/specs).

## API

All business functionality is implemented as gRPC services, defined as `.proto` files under
[`src/main/proto/vertice`](src/main/proto/vertice):

- `user.v1.UserService` — users (trainers and clients, unified with a `Role`)
- `plan.v1.TrainingPlanService` — training plans
- `plan.v1.WorkoutService` — workouts (including cloning an existing workout as a base)
- `plan.v1.WorkoutExerciseService` — exercises placed into a workout
- `plan.v1.ExerciseSetService` — sets within a workout exercise
- `exercise.v1.ExerciseService` — the shared exercise catalog
- `session.v1.WorkoutSessionService` — client workout session logging (start/resume, complete,
  per-set actuals, exercise progress)
- `session.v1.WorkoutFeedbackService` — client feedback on completed workouts

gRPC server reflection (for tools like `grpcurl`) is disabled by default and only enabled under
the `local` profile.

### Dates and times

**The business time zone is `America/Sao_Paulo`.** Every field the API exposes is one of two kinds,
and clients must treat them differently:

| Kind | Over the wire | Meaning | Examples |
| --- | --- | --- | --- |
| **Instant** | ISO-8601 with the `Z` designator, e.g. `2026-10-17T01:00:00Z` | An absolute point in time. Stored as `TIMESTAMPTZ`. Format it for display in the viewer's (or the business) zone — never read the UTC clock off the string. | `startedAt`, `completedAt`, `recordedAt`, `createdAt` |
| **Business date** | `yyyy-MM-dd`, e.g. `2026-10-31` | A local calendar day in `America/Sao_Paulo`. No time, no zone. Stored as `DATE`. | a plan's `startDate`/`endDate`, a log's `weekStartDate` |

Consequences for callers:

- **Never put a business date through a UTC conversion.** `new Date("2026-10-31")` is UTC midnight,
  which renders as 30/10/2026 in Brazil. Parse the three parts, or parse it as local midnight.
- **Derive "today" and the week start in `America/Sao_Paulo`**, not from
  `new Date().toISOString().slice(0, 10)` (that is the UTC day, so it flips at 21:00 local). The API
  computes neither: `weekStartDate` (which must be a Monday) and a plan's dates are supplied by the
  caller and stored verbatim.
- **Don't assume a fixed −03:00 offset.** Brazil observed DST until 2019, so older instants were
  recorded at −02:00. Use the zone, not an offset.
- The API's correctness does not depend on the server's `TZ` or `user.timezone`, and must not start
  to: `./gradlew build` runs the time-zone contract test a second time with the JVM pinned to
  `Pacific/Kiritimati` (UTC+14). See [`CLAUDE.md`](CLAUDE.md) ("Time: instants vs. business dates")
  for the API-side rules.

### Proto contract

The `.proto` files under `src/main/proto/vertice` are the single source of truth for the API
contract. [vertice-bff](https://github.com/herbertpdl/vertice-bff) doesn't define its own: it keeps
a copy under its `protos/vertice/` (same relative paths, `*.proto` files only) taken from this
directory at a pinned vertice-api commit. The copy is meant to be refreshed with
`npm run sync:protos` in vertice-bff, with a CI step there that fails when the copy differs from
the pinned ref; that script lands in vertice-bff's next PR (audit plan item P2-01), so until then
the copy is updated by hand. Change a proto here first, merge it, then sync the BFF.

CI guards the contract with [Buf](https://buf.build) (config in [`buf.yaml`](buf.yaml)):

- `buf lint`: the `STANDARD` rules, minus four that the existing protos predate and that can't be
  fixed without renaming enum values or messages the BFF uses (`ENUM_VALUE_PREFIX`,
  `RPC_REQUEST_RESPONSE_UNIQUE`, `RPC_REQUEST_STANDARD_NAME`, `RPC_RESPONSE_STANDARD_NAME`).
- `buf breaking` (pull requests only): the `FILE` rules against the PR's base branch, so a deleted
  or renumbered field, a renamed message/RPC, a changed type or a moved file fails the build.
  Comment-only changes pass. An intentional break needs a new package version (e.g. `v2`) instead.

To run them locally (Buf CLI 1.73.0, as pinned in CI):

```sh
buf lint
buf breaking --against '.git#branch=main,subdir=src/main/proto'
```

## Prerequisites

- JDK 25 (or let the Gradle toolchain provision one automatically)
- PostgreSQL 16 (or Docker, to run it in a container)
- Docker, if you prefer running the API in a container instead of natively

## Configuration

Runtime configuration lives in `src/main/resources/application.properties`, with the following
environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_USERNAME` | `vertice` | PostgreSQL username |
| `DB_PASSWORD` | `vertice` | PostgreSQL password |
| `JWT_SECRET` | `vertice-dev-jwt-secret-do-not-use-in-production` | HS256 secret shared with vertice-bff to verify its tokens. The default works only under the `local` profile; any other profile refuses to boot on a blank, padded, sub-32-byte or placeholder secret (`JwtSecretGuard`) |
| `PASSWORD_SETUP_TOKEN_TTL` | `72h` | How long a password setup (first-access) token stays usable (`vertice.password-setup.token-ttl`, a Spring `Duration`) |
| `VERTICE_ENV` | unset | Which environment this is. Under the `local` profile it must be unset, `local` or `dev` (case-insensitive), or the app refuses to boot (`LocalProfileGuard`). **Every deployed environment must set it** (e.g. `prod`): it is the only check that catches `local` against a deployed database that happens to be reachable as `postgres` |

`spring.datasource.url` defaults to `jdbc:postgresql://localhost:5432/vertice` (overridable via
the standard `SPRING_DATASOURCE_URL` environment variable, as done by `vertice-local`'s
docker-compose setup).

The HTTP server listens on port `8080`; the gRPC server listens on port `9090`.

## Running locally

### Standalone (native Gradle + a Postgres container)

This repo's own `docker-compose.yml` only starts Postgres:

```sh
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile ([`application-local.properties`](src/main/resources/application-local.properties))
disables authentication (see `LocalSecurityConfig`) and enables gRPC server reflection, so the
service can be exercised directly with tools like Postman or `grpcurl` without a running JWT
issuer.

Because it turns authentication off, `LocalProfileGuard` refuses to start the `local` profile
(alone or combined with another profile, e.g. `local,prod`) when `VERTICE_ENV` is set to anything
other than `local`/`dev`, or when `spring.datasource.url` points at a host other than `localhost`,
`127.0.0.1`, `::1` or the compose service `postgres` (a URL it can't parse, or one with
`host`/`PGHOST`/`service` query parameters, is refused too). The check runs before any bean is
created, so the app never connects to, or migrates, a non-local database under `local`. Because
`postgres` is allowlisted for compose and can also be a real database's hostname, deployed
environments must set `VERTICE_ENV`. The compose file publishes Postgres on `127.0.0.1` only.

Flyway runs the migrations under [`src/main/resources/db/migration`](src/main/resources/db/migration)
automatically on startup.

For hot reload without Docker, run a continuous compile in a second terminal alongside
`bootRun` — `spring-boot-devtools` restarts the app whenever the compiled classes change:

```sh
./gradlew build --continuous -x test
```

(Native filesystem events work reliably here since there's no Docker Desktop virtiofs bind mount
involved, unlike the container setup described below.)

### Full stack (with the BFF and web app)

If you also have `vertice-bff` and `vertice-web-react` checked out as siblings of this one, run
each natively in its own terminal:

```
Workspace/
├── vertice-api/         (this repo)
├── vertice-bff/
└── vertice-web-react/
```

1. This repo — as above (`docker compose up -d` + `./gradlew bootRun --args='--spring.profiles.active=local'`).
2. `vertice-bff` — `cp .env.example .env && npm install && npm run dev` (REST on `:3000`).
3. `vertice-web-react` — `npm install && npm run dev` (`:5173`).

This avoids Docker image-cache/rebuild pain, especially for the frontend. A `vertice-local`
repo with a `docker-compose.yml` also exists to run all of the above (plus Postgres) together
with one command — see its README — but native runs are the faster inner loop for day-to-day
development.

## Running tests

```sh
./gradlew test
```

Tests spin up against a real PostgreSQL instance (see the `postgres` service in
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) for the CI configuration), so a running
Postgres on `localhost:5432` — e.g. via `docker compose up -d` — is required first.

To run the full build (compile, generate proto sources, run tests) the same way CI does:

```sh
./gradlew build
```

`build` also runs `hostileTimeZoneTest`, which re-runs the time-zone contract test
(`common/time/BusinessTimeZoneIntegrationTest`) with the JVM pinned to `Pacific/Kiritimati`
(UTC+14) — proof that nothing depends on the host's zone. To run just that:

```sh
./gradlew hostileTimeZoneTest
```

## Project structure

```
src/main/java/com/vertice/api/
├── common/         Shared exceptions and validation (e.g. CPF validation)
├── config/         Security configuration (default + local profile)
├── grpc/           Cross-cutting gRPC concerns: exception mapping, health, request validation
├── plan/           TrainingPlan, Workout, WorkoutExercise, ExerciseSet, and workout sessions
├── plan/exercise/  The shared Exercise catalog
└── user/           User (trainer/client) accounts

src/main/proto/vertice/   Protobuf/gRPC service and message definitions
src/main/resources/db/migration/   Flyway migrations
docs/                      Domain model, requirements, and per-feature specs
```

## Continuous integration

Every push and pull request against `main` runs `buf lint` (and, on pull requests, `buf breaking`
against the base branch; see [Proto contract](#proto-contract)) and then `./gradlew build` against a
PostgreSQL 16 service container, as defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

A second job, `dependency-scan`, builds the Spring Boot jar and scans it with
[Grype](https://github.com/anchore/grype), failing on any high or critical advisory in a runtime
dependency. To reproduce it locally after `./gradlew bootJar`:

```sh
docker run --rm -v "$PWD/build/libs:/scan:ro" anchore/grype:latest dir:/scan --fail-on high
```

[Dependabot](.github/dependabot.yml) opens weekly update PRs for Gradle dependencies and GitHub
Actions (minor and patch updates grouped, at most 5 open per ecosystem).
