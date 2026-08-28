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
| `JWT_ISSUER_URI` | `http://localhost:9000` | OAuth2/JWT issuer used to validate bearer tokens |

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

Flyway runs the migrations under [`src/main/resources/db/migration`](src/main/resources/db/migration)
automatically on startup.

### Full stack (with the web app and BFF)

If you also have the `vertice-web` and `vertice-bff` repos checked out as siblings of this one,
use the [`vertice-local`](../vertice-local) repo to run the whole stack (Postgres, this API, the
BFF, and the web app) together via Docker Compose, with source bind-mounted for hot reload:

```sh
Workspace/
├── vertice-local/   (run docker compose from here)
├── vertice-web/
├── vertice-bff/
└── vertice-api/     (this repo)
```

```sh
cd ../vertice-local
cp .env.example .env   # first time only
docker compose up --build
```

In that setup, this API's `Dockerfile` builds a `dev` image (JDK 25 + the Gradle wrapper); the
container entrypoint ([`docker/dev-entrypoint.sh`](docker/dev-entrypoint.sh)) polls `src/` for
changes and triggers an incremental Gradle recompile, which `spring-boot-devtools` then picks up
to restart the app — needed because Gradle's native `--continuous` file watching doesn't reliably
see changes through Docker Desktop's virtiofs bind mounts on macOS.

See `vertice-local`'s README for full details, including the full list of service URLs.

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

Every push and pull request against `main` runs `./gradlew build` against a PostgreSQL 16 service
container, as defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
