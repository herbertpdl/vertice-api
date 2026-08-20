# Validation checklist: Trainer CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

## Endpoints (section 2)

- [ ] `GET /api/trainers` returns 200 with all trainers
- [ ] `POST /api/trainers` returns 201 + Location/body with created trainer
- [ ] `GET /api/trainers/{id}` returns 200 for existing id
- [ ] `GET /api/trainers/{id}` returns 404 for missing id
- [ ] `PUT /api/trainers/{id}` returns 200 for existing id
- [ ] `PUT /api/trainers/{id}` returns 404 for missing id
- [ ] `DELETE /api/trainers/{id}` returns 204 for existing id
- [ ] `DELETE /api/trainers/{id}` returns 404 for missing id
- [ ] Controllers implement the openapi-generated interfaces (not hand-written `@RequestMapping`s that could drift from `api.yaml`)

## Data model (section 3)

- [ ] Flyway migration exists for `trainers` table matching the entity exactly
- [ ] App boots cleanly against a fresh DB (`docker compose up` + `./gradlew bootRun`) with `ddl-auto=validate`
- [ ] `email` has a DB-level unique constraint

## Validation rules (section 4)

- [ ] Blank `name` on create → 422
- [ ] Blank `email` on create → 422
- [ ] Malformed `email` on create → 422
- [ ] Same validation applies on update

## Business rules (section 5)

- [ ] Create with duplicate email → rejected, no row inserted
- [ ] Update to an email owned by another trainer → rejected, no row changed
- [ ] Update keeping the trainer's own unchanged email → succeeds (not flagged as duplicate against itself)
- [ ] Delete a trainer with no training plans → succeeds

## Error handling (section 6)

- [ ] 422 responses use `ProblemDetail` shape from `GlobalExceptionHandler`
- [ ] 404 responses use `ProblemDetail` shape via `ResourceNotFoundException`
- [ ] 409 duplicate-email case added: exception class + handler + `api.yaml` updated with 409 response
- [ ] No raw stack traces or DB constraint-violation messages leak to the client on duplicate email

## Security (section 7)

- [ ] Request without JWT → 401 on all trainer endpoints
- [ ] Request with valid JWT → allowed regardless of claims/roles
- [ ] `/actuator/health` still accessible without auth (unaffected by this change)

## Mapping (section 8)

- [ ] `TrainerMapper` (MapStruct) exists and is used in the service — no manual field copying

## Out of scope (section 9) — confirm nothing crept in

- [ ] No pagination/filtering added beyond what's specified
- [ ] No soft-delete logic added
- [ ] No auth/login endpoints added

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
- [ ] Spec and code reviewed side by side for drift (spec updated if implementation intentionally diverged)
