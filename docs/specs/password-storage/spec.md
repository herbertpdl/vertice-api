# Spec: Password storage for Trainer and Student

Status: Deprecated — described password storage on the separate Trainer/Student entities, which
`docs/specs/user-unification/spec.md` removed. The `passwordHash` design survives on the unified
`User` entity (carried forward by that spec), but this spec's Trainer/Student-specific design no
longer matches the codebase.
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`, `docs/specs/student-crud/spec.md`

## 0. Scope decision

Trainers and students will eventually log in via email/password. This spec covers **only** the
data model and secure storage of credentials — hashing, migrations, and the endpoints to set a
password on creation and to change it later. It does **not** cover:

- A login endpoint that validates credentials and issues a token.
- Any change to `SecurityConfig`, which still expects JWTs from an external OAuth2 issuer for all
  requests. How that issuer will eventually validate against these stored credentials (or
  whether vertice-api itself becomes the issuer) is an open architecture question, deliberately
  deferred to a later spec.

This is intentionally a narrower slice than "add login" — it gets the credential storage right as
a foundation without committing to an auth architecture today.

## 1. Goal

Trainer and Student can each have a securely-hashed password: set at creation, changed via a
dedicated endpoint, never returned in any response, never stored or logged in plaintext.

## 2. Design decision: separate schemas for create vs. update

The existing `TrainerRequest`/`StudentRequest` schemas are shared between `POST` (create) and
`PUT` (update) via the same `$ref`. Adding a required `password` field there would force every
unrelated profile update (e.g. changing just the name) to resend the password — bad UX and not
what "password management" should look like.

Instead:

- `TrainerCreateRequest` / `StudentCreateRequest` (new): `name`, `email`, `password` — all
  required. Used only by `POST /api/trainers` and `POST /api/students`.
- `TrainerRequest` / `StudentRequest` (existing, unchanged): `name`, `email`. Still used only by
  `PUT /api/trainers/{id}` and `PUT /api/students/{id}` — password is untouched by this endpoint.
- `SetPasswordRequest` (new, shared by both entities — identical shape): `password`, required.
  Used by two new endpoints, one per entity (see below).

## 3. New/changed endpoints

| Method | Path                        | Operation ID        | Body                 | Notes |
|--------|-----------------------------|---------------------|-----------------------|-------|
| POST   | /api/trainers               | createTrainer        | TrainerCreateRequest  | **Changed**: now requires `password` |
| PUT    | /api/trainers/{id}/password | setTrainerPassword   | SetPasswordRequest    | New. 204 on success, 404 if trainer missing, 422 if password too short |
| POST   | /api/students               | createStudent         | StudentCreateRequest | **Changed**: now requires `password` |
| PUT    | /api/students/{id}/password | setStudentPassword   | SetPasswordRequest    | New. 204 on success, 404 if student missing, 422 if password too short |

`PUT /api/trainers/{id}` and `PUT /api/students/{id}` (name/email update) are unchanged.

## 4. Validation rules

- `password`: required, `minLength: 8` on both `TrainerCreateRequest`/`StudentCreateRequest` and
  `SetPasswordRequest`.

## 5. Storage

- Entity field: `passwordHash` (not `password`) — makes it explicit at the code level that this
  is never plaintext. Column `password_hash`, `varchar(255)`, `not null`.
- Hashing: BCrypt via Spring Security's `PasswordEncoder` (`BCryptPasswordEncoder`), a new shared
  bean (`PasswordEncoderConfig`), not gated by the `local` profile — needed regardless of how auth
  is eventually wired up.
- The plaintext `password` from the request is hashed in the service layer before being set on
  the entity. The mapper explicitly ignores `passwordHash` (same pattern as `id`) so MapStruct
  never has a chance to copy a source `password` string directly into it.
- `Trainer`/`Student` entities get `@ToString.Exclude` on `passwordHash` so it can never leak via
  an accidental `toString()` in logs, even though it's already a hash and not plaintext — belt
  and suspenders.
- **Known limitation, accepted for this scope**: the openapi-generated `TrainerCreateRequest`/
  `SetPasswordRequest` model classes include a generated `toString()` that would include the raw
  `password` field if logged. This is a codegen template limitation, not something we can fix
  without customizing the generator. Mitigation: don't log full request bodies; revisit if this
  becomes a real logging path.

## 6. Migration

Two new tables already have rows from manual testing (one trainer, one student). To avoid a
destructive reset of local dev data, the migration adds the column nullable, backfills existing
rows with an empty-string placeholder (which can never match a submitted password once
BCrypt-compared, since it isn't a valid BCrypt hash — those rows are effectively locked out until
a real password is set via `setTrainerPassword`/`setStudentPassword`), then sets `NOT NULL`:

```sql
ALTER TABLE trainers ADD COLUMN password_hash VARCHAR(255);
UPDATE trainers SET password_hash = '' WHERE password_hash IS NULL;
ALTER TABLE trainers ALTER COLUMN password_hash SET NOT NULL;
```

(mirrored for `students`)

## 7. Response shape

`TrainerResponse`/`StudentResponse` are unchanged — no password/hash field, ever.

## 8. Out of scope

- Login endpoint / token issuance.
- Password reset/forgot-password flow.
- Rate limiting or lockout on password attempts (no login endpoint exists yet to attempt against).
- Migrating the two pre-existing local test records to a real password (they're locked out per
  section 6, by design — reasonable for throwaway local test data).
- Any change to `SecurityConfig`/JWT handling.
