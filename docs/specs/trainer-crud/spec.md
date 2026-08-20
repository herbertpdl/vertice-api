# Spec: Trainer CRUD

Status: Draft
Owner: hebertpdl@gmail.com
Related: `src/main/resources/openapi/api.yaml` (Trainers paths already defined)

## 1. Goal

Provide full CRUD management of trainers via `vertice-api`, backed by Postgres, so the BFF/web
clients can list, create, view, update, and delete trainer records.

## 2. Endpoints

Already declared in `openapi/api.yaml` — implementation must conform to it exactly (status codes,
schemas, operationIds). Summary:

| Method | Path               | Operation ID   | Success | Notes                        |
|--------|--------------------|----------------|---------|-------------------------------|
| GET    | /api/trainers      | listTrainers   | 200     | Returns all trainers          |
| POST   | /api/trainers      | createTrainer  | 201     | Body: TrainerRequest          |
| GET    | /api/trainers/{id} | getTrainer     | 200     | 404 if not found              |
| PUT    | /api/trainers/{id} | updateTrainer  | 200     | 404 if not found              |
| DELETE | /api/trainers/{id} | deleteTrainer  | 204     | 404 if not found              |

Controllers must implement the interfaces generated from `api.yaml` by the
`org.openapi.generator` plugin (`interfaceOnly: true`) rather than hand-rolling request mappings,
so the spec and the code cannot drift silently.

## 3. Data model

Table `trainers` (entity `Trainer` already exists in `com.vertice.api.trainer`):

| Column | Type          | Constraints                  |
|--------|---------------|-------------------------------|
| id     | bigint        | PK, identity                  |
| name   | varchar       | not null                      |
| email  | varchar       | not null, unique               |

Requires a Flyway migration under `src/main/resources/db/migration`
(e.g. `V1__create_trainers_table.sql`) — none exists yet, and `ddl-auto=validate` means the app
will fail to boot without it.

## 4. Validation rules

- `name`: required, non-blank (`@NotBlank`, already on `TrainerRequest`).
- `email`: required, non-blank, valid email format (`@NotBlank @Email`, already on
  `TrainerRequest`).
- `email` must be unique across trainers (DB constraint + application-level check before insert/
  update, so we can return a clean error instead of a raw constraint-violation stack trace).

## 5. Business rules

- Creating a trainer with an email that already exists → reject, do not create.
- Updating a trainer to an email used by a *different* trainer → reject, do not update.
- Deleting a trainer that has associated `TrainingPlan` rows: out of scope for this spec (no FK
  enforcement decision yet) — deletion should simply work for trainers with no plans; the
  training-plan relationship is not exercised until the TrainingPlan feature lands.

## 6. Error handling

| Scenario                          | Status | Body                                  |
|------------------------------------|--------|----------------------------------------|
| Validation failure (blank/invalid) | 422    | ProblemDetail (existing handler)       |
| Trainer not found (get/update/delete) | 404 | ProblemDetail (existing handler)     |
| Duplicate email (create/update)    | 409    | ProblemDetail — **new**, not yet in `api.yaml` or `GlobalExceptionHandler` |

Action item: add a `409` response + `DuplicateEmailException` (or similar) mapped in
`GlobalExceptionHandler`, and add the `409` response entry to `api.yaml` for `createTrainer` and
`updateTrainer`.

## 7. Security

- All `/api/trainers/**` endpoints require a valid JWT (per existing `SecurityConfig`,
  `anyRequest().authenticated()`).
- No role/scope differentiation for this spec — any authenticated caller may perform any Trainer
  CRUD operation. (Revisit later if trainer-vs-admin roles are introduced.)

## 8. Mapping

Use MapStruct (`TrainerMapper`, already a project dependency but no mapper class exists yet) to
convert between `Trainer` entity and `TrainerRequest`/`TrainerResponse`, instead of manual
field-by-field mapping in the service.

## 9. Out of scope

- Pagination/filtering/sorting on `listTrainers` (returns full list for now).
- Soft delete.
- Trainer authentication/login (JWT is issued externally).
