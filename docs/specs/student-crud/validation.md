# Validation checklist: Student CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

## Endpoints (section 2)

- [x] `GET /api/students` returns 200 with all students — `StudentControllerTest#listStudents_withJwt_returns200`; also verified live via curl (`[]` on empty DB)
- [x] `POST /api/students` returns 201 + body with created student — `#createStudent_withValidBody_returns201`; also verified live via curl
- [x] `GET /api/students/{id}` returns 200 for existing id — `#getStudent_whenExists_returns200`
- [x] `GET /api/students/{id}` returns 404 for missing id — `#getStudent_whenMissing_returns404`
- [x] `PUT /api/students/{id}` returns 200 for existing id — `#updateStudent_whenExists_returns200`
- [x] `PUT /api/students/{id}` returns 404 for missing id — `#updateStudent_whenMissing_returns404`
- [x] `DELETE /api/students/{id}` returns 204 for existing id — `#deleteStudent_whenExists_returns204`
- [x] `DELETE /api/students/{id}` returns 404 for missing id — `#deleteStudent_whenMissing_returns404`
- [x] `StudentController` implements the openapi-generated `StudentsApi` interface

## Data model (section 3)

- [x] App boots cleanly with the existing `V2__create_students_table.sql` — verified via `bootRun` against Postgres, no new migration needed
- [x] `email` has a DB-level unique constraint — already present in `V2`, confirmed by live duplicate-email test returning 409

## Validation rules (section 4)

- [x] Blank `name` on create → 422 — `#createStudent_withBlankName_returns422`
- [x] Blank `email` on create → 422 — `#createStudent_withBlankEmail_returns422` (schema has `minLength: 1` on email from the start, learned from Trainer CRUD)
- [x] Malformed `email` on create → 422 — `#createStudent_withMalformedEmail_returns422`
- [x] Same validation applies on update — `updateStudent` uses the same `@Valid StudentRequest` generated interface parameter

## Business rules (section 5)

- [x] Create with duplicate email → rejected, no row inserted — `StudentServiceTest#createStudent_rejectsDuplicateEmail` + live curl (409)
- [x] Update to an email owned by another student → rejected, no row changed — `#updateStudent_rejectsEmailOwnedByAnotherStudent`
- [x] Update keeping the student's own unchanged email → succeeds — `#updateStudent_allowsKeepingOwnEmail`
- [x] Delete a student → succeeds — `#deleteStudent_whenExists_returns204`

## Error handling (section 6)

- [x] 422 responses use `ProblemDetail` shape from `GlobalExceptionHandler`
- [x] 404 responses use `ProblemDetail` shape via `ResourceNotFoundException`
- [x] 409 duplicate-email uses the existing generic `DuplicateEmailException` — no new exception class needed, confirmed reused as-is
- [x] `api.yaml` has 409 responses on `createStudent`/`updateStudent`

## Security (section 7)

- [x] Request without JWT → 401 on all student endpoints — `#listStudents_withoutJwt_returns401`
- [x] Request with valid JWT → allowed regardless of claims/roles — all other controller tests use `.with(jwt())` with no role/claim setup and succeed

## Mapping (section 8)

- [x] `StudentMapper` (MapStruct) exists and is used in the service — no manual field copying

## Out of scope (section 9) — confirm nothing crept in

- [x] No pagination/filtering added beyond what's specified
- [x] No soft-delete logic added
- [x] No Student/TrainingPlan or Student/Trainer relationship added

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes — 37/37 tests green (1 context, 5+5 service, 13+13 controller across Trainer and Student)
- [x] Spec and code reviewed side by side for drift — no drift; implementation matches spec exactly, no new migration needed as predicted
