# Validation checklist: Password storage for Trainer and Student

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

## Design decision (section 2)

- [ ] `TrainerRequest`/`StudentRequest` (used by PUT) are unchanged — no password field added there
- [ ] `TrainerCreateRequest`/`StudentCreateRequest` exist with `name`, `email`, `password` all required
- [ ] `SetPasswordRequest` exists, shared by both entities, `password` required

## Endpoints (section 3)

- [ ] `POST /api/trainers` requires `password`, rejects missing/short password with 422
- [ ] `PUT /api/trainers/{id}/password` returns 204 on success
- [ ] `PUT /api/trainers/{id}/password` returns 404 for missing trainer
- [ ] `PUT /api/trainers/{id}/password` returns 422 for too-short password
- [ ] `POST /api/students` requires `password`, rejects missing/short password with 422
- [ ] `PUT /api/students/{id}/password` returns 204 on success
- [ ] `PUT /api/students/{id}/password` returns 404 for missing student
- [ ] `PUT /api/students/{id}/password` returns 422 for too-short password
- [ ] `PUT /api/trainers/{id}` and `PUT /api/students/{id}` (name/email) still work unchanged, don't touch password

## Validation rules (section 4)

- [ ] Password shorter than 8 chars → 422 on create
- [ ] Password shorter than 8 chars → 422 on set-password

## Storage (section 5)

- [ ] `Trainer`/`Student` entities have `passwordHash`, not `password`
- [ ] `passwordHash` is `@ToString.Exclude`d
- [ ] `PasswordEncoderConfig` provides a `PasswordEncoder` bean (BCrypt), active regardless of profile
- [ ] Stored value is a real BCrypt hash (starts with `$2a$`/`$2b$`), never the raw password
- [ ] `TrainerMapper`/`StudentMapper` explicitly ignore `passwordHash` in both `toEntity` and any
      other generated-target mapping method — no accidental raw-to-hash-field copy
- [ ] Same raw password produces a different stored hash each time (BCrypt salting) — sanity check

## Migration (section 6)

- [ ] App boots cleanly against the existing local DB (with its 2 pre-existing test rows) without
      manual intervention
- [ ] Pre-existing rows get `password_hash = ''` and are not otherwise altered
- [ ] `password_hash` is `NOT NULL` after migration

## Response shape (section 7)

- [ ] `TrainerResponse`/`StudentResponse` bodies never contain `password` or `passwordHash` in any
      endpoint's response, including the ones just added

## Out of scope (section 8) — confirm nothing crept in

- [ ] No login/token endpoint added
- [ ] No password reset flow added
- [ ] No `SecurityConfig` changes

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
- [ ] Spec and code reviewed side by side for drift
