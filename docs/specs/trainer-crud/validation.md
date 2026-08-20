# Validation checklist: Trainer CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

## Endpoints (section 2)

- [x] `GET /api/trainers` returns 200 with all trainers — `TrainerControllerTest#listTrainers_withJwt_returns200`
- [x] `POST /api/trainers` returns 201 + body with created trainer — `#createTrainer_withValidBody_returns201`
- [x] `GET /api/trainers/{id}` returns 200 for existing id — `#getTrainer_whenExists_returns200`
- [x] `GET /api/trainers/{id}` returns 404 for missing id — `#getTrainer_whenMissing_returns404`
- [x] `PUT /api/trainers/{id}` returns 200 for existing id — `#updateTrainer_whenExists_returns200`
- [x] `PUT /api/trainers/{id}` returns 404 for missing id — `#updateTrainer_whenMissing_returns404`
- [x] `DELETE /api/trainers/{id}` returns 204 for existing id — `#deleteTrainer_whenExists_returns204`
- [x] `DELETE /api/trainers/{id}` returns 404 for missing id — `#deleteTrainer_whenMissing_returns404`
- [x] Controllers implement the openapi-generated interfaces — `TrainerController implements TrainersApi`

## Data model (section 3)

- [x] Flyway migration exists for `trainers` table matching the entity exactly — `V1__create_trainers_table.sql`
- [x] App boots cleanly against a fresh DB with `ddl-auto=validate` — verified via `./gradlew bootRun` against `docker compose up` Postgres; also required adding `V2`-`V4` migrations for pre-existing Student/TrainingPlan/Exercise entities that had none
- [x] `email` has a DB-level unique constraint — `uq_trainers_email` in the migration

## Validation rules (section 4)

- [x] Blank `name` on create → 422 — `#createTrainer_withBlankName_returns422`
- [x] Blank `email` on create → 422 — `#createTrainer_withBlankEmail_returns422` (this test caught a real gap: `format: email` alone lets `""` through since `@Email` treats empty as valid; fixed by adding `minLength: 1` to the `email` schema in `api.yaml`, same as `name`)
- [x] Malformed `email` on create → 422 — `#createTrainer_withMalformedEmail_returns422`
- [x] Same validation applies on update — `updateTrainer` uses the same `@Valid TrainerRequest` generated interface parameter

## Business rules (section 5)

- [x] Create with duplicate email → rejected, no row inserted — `TrainerServiceTest#createTrainer_rejectsDuplicateEmail` (asserts `save` never called)
- [x] Update to an email owned by another trainer → rejected, no row changed — `#updateTrainer_rejectsEmailOwnedByAnotherTrainer`
- [x] Update keeping the trainer's own unchanged email → succeeds — `#updateTrainer_allowsKeepingOwnEmail`
- [x] Delete a trainer with no training plans → succeeds — `#deleteTrainer_whenExists_returns204` (no plans involved in this feature's scope)

## Error handling (section 6)

- [x] 422 responses use `ProblemDetail` shape from `GlobalExceptionHandler` — existing handler, exercised by validation tests
- [x] 404 responses use `ProblemDetail` shape via `ResourceNotFoundException` — exercised by not-found tests
- [x] 409 duplicate-email case added: exception class + handler + `api.yaml` updated with 409 response — `DuplicateEmailException`, `GlobalExceptionHandler#handleDuplicateEmail`, `api.yaml` `createTrainer`/`updateTrainer` 409 responses
- [x] No raw stack traces or DB constraint-violation messages leak to the client on duplicate email — email uniqueness is checked in the service *before* `save()`, so the DB constraint is never hit on the happy path

## Security (section 7)

- [x] Request without JWT → 401 on all trainer endpoints — `#listTrainers_withoutJwt_returns401`
- [x] Request with valid JWT → allowed regardless of claims/roles — all other controller tests use `.with(jwt())` with no role/claim setup and succeed
- [x] `/actuator/health` still accessible without auth — verified manually via `curl` returning 200 during `bootRun`; unchanged from existing `SecurityConfig`

## Mapping (section 8)

- [x] `TrainerMapper` (MapStruct) exists and is used in the service — no manual field copying

## Out of scope (section 9) — confirm nothing crept in

- [x] No pagination/filtering added beyond what's specified
- [x] No soft-delete logic added
- [x] No auth/login endpoints added

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes — 19/19 tests green (1 context test, 5 service tests, 13 controller tests)
- [x] Spec and code reviewed side by side for drift — no drift; the only spec deviation is the addition of V2-V4 migrations, which was necessary infrastructure (see Data model note above) rather than a scope change to Trainer behavior
