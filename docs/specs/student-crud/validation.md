# Validation checklist: Student CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

## Endpoints (section 2)

- [ ] `GET /api/students` returns 200 with all students
- [ ] `POST /api/students` returns 201 + body with created student
- [ ] `GET /api/students/{id}` returns 200 for existing id
- [ ] `GET /api/students/{id}` returns 404 for missing id
- [ ] `PUT /api/students/{id}` returns 200 for existing id
- [ ] `PUT /api/students/{id}` returns 404 for missing id
- [ ] `DELETE /api/students/{id}` returns 204 for existing id
- [ ] `DELETE /api/students/{id}` returns 404 for missing id
- [ ] `StudentController` implements the openapi-generated `StudentsApi` interface

## Data model (section 3)

- [ ] App boots cleanly with the existing `V2__create_students_table.sql` (no new migration needed)
- [ ] `email` has a DB-level unique constraint (already present in `V2`)

## Validation rules (section 4)

- [ ] Blank `name` on create → 422
- [ ] Blank `email` on create → 422 (schema must have `minLength: 1` on email, not just `format: email`)
- [ ] Malformed `email` on create → 422
- [ ] Same validation applies on update

## Business rules (section 5)

- [ ] Create with duplicate email → rejected, no row inserted
- [ ] Update to an email owned by another student → rejected, no row changed
- [ ] Update keeping the student's own unchanged email → succeeds
- [ ] Delete a student → succeeds

## Error handling (section 6)

- [ ] 422 responses use `ProblemDetail` shape from `GlobalExceptionHandler`
- [ ] 404 responses use `ProblemDetail` shape via `ResourceNotFoundException`
- [ ] 409 duplicate-email uses the existing generic `DuplicateEmailException` (no new exception class needed)
- [ ] `api.yaml` has 409 responses on `createStudent`/`updateStudent`

## Security (section 7)

- [ ] Request without JWT → 401 on all student endpoints
- [ ] Request with valid JWT → allowed regardless of claims/roles

## Mapping (section 8)

- [ ] `StudentMapper` (MapStruct) exists and is used in the service — no manual field copying

## Out of scope (section 9) — confirm nothing crept in

- [ ] No pagination/filtering added beyond what's specified
- [ ] No soft-delete logic added
- [ ] No Student/TrainingPlan or Student/Trainer relationship added

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
- [ ] Spec and code reviewed side by side for drift (spec updated if implementation intentionally diverged)
