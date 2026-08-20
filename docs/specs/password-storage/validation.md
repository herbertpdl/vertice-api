# Validation checklist: Password storage for Trainer and Student

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

## Design decision (section 2)

- [x] `TrainerRequest`/`StudentRequest` (used by PUT) are unchanged — no password field added there
- [x] `TrainerCreateRequest`/`StudentCreateRequest` exist with `name`, `email`, `password` all required
- [x] `SetPasswordRequest` exists, shared by both entities, `password` required

## Endpoints (section 3)

- [x] `POST /api/trainers` requires `password`, rejects missing/short password with 422 — `TrainerControllerTest#createTrainer_withMissingPassword_returns422`/`#createTrainer_withShortPassword_returns422` + live curl
- [x] `PUT /api/trainers/{id}/password` returns 204 on success — `#setTrainerPassword_whenExists_returns204` + live curl
- [x] `PUT /api/trainers/{id}/password` returns 404 for missing trainer — `#setTrainerPassword_whenMissing_returns404` + live curl
- [x] `PUT /api/trainers/{id}/password` returns 422 for too-short password — `#setTrainerPassword_withShortPassword_returns422` + live curl
- [x] `POST /api/students` requires `password`, rejects missing/short password with 422 — `StudentControllerTest#createStudent_withMissingPassword_returns422`/`#createStudent_withShortPassword_returns422`
- [x] `PUT /api/students/{id}/password` returns 204 on success — `#setStudentPassword_whenExists_returns204` + live curl
- [x] `PUT /api/students/{id}/password` returns 404 for missing student — `#setStudentPassword_whenMissing_returns404`
- [x] `PUT /api/students/{id}/password` returns 422 for too-short password — `#setStudentPassword_withShortPassword_returns422`
- [x] `PUT /api/trainers/{id}` and `PUT /api/students/{id}` (name/email) still work unchanged, don't touch password — `TrainerServiceTest#updateTrainer_allowsKeepingOwnEmail` asserts `passwordHash` unchanged after update; also verified live (renamed a trainer via PUT, hash in DB identical before/after)

## Validation rules (section 4)

- [x] Password shorter than 8 chars → 422 on create — see above
- [x] Password shorter than 8 chars → 422 on set-password — see above

## Storage (section 5)

- [x] `Trainer`/`Student` entities have `passwordHash`, not `password`
- [x] `passwordHash` is `@ToString.Exclude`d
- [x] `PasswordEncoderConfig` provides a `PasswordEncoder` bean (BCrypt), active regardless of profile — not `@Profile`-gated, confirmed by code read
- [x] Stored value is a real BCrypt hash (starts with `$2a$`/`$2b$`), never the raw password — verified live via `docker exec ... psql`, e.g. `$2a$10$WhfBUYw...`
- [x] `TrainerMapper`/`StudentMapper` explicitly ignore `passwordHash` in both `toEntity` and any
      other generated-target mapping method — code read; compiles with zero MapStruct warnings
- [x] Same raw password produces a different stored hash each time (BCrypt salting) — `TrainerServiceTest#passwordEncoder_saltsSamePasswordDifferently`

## Migration (section 6)

- [x] App boots cleanly against the existing local DB (with its 2 pre-existing test rows) without
      manual intervention — `VerticeApiApplicationTests` (full `@SpringBootTest`) passed against the real local DB, and `bootRun` succeeded live
- [x] Pre-existing rows get `password_hash = ''` and are not otherwise altered — by migration SQL construction (only touches rows where `password_hash IS NULL`); note the specific 2 pre-existing rows from earlier manual testing had actually already been deleted via the DELETE endpoint by the time this ran, so this was verified by reading the migration SQL rather than observing it live
- [x] `password_hash` is `NOT NULL` after migration — `ALTER COLUMN ... SET NOT NULL` in the migration; confirmed indirectly by `ddl-auto=validate` passing (Hibernate validates nullability against the entity's `nullable = false`)

## Response shape (section 7)

- [x] `TrainerResponse`/`StudentResponse` bodies never contain `password` or `passwordHash` in any
      endpoint's response, including the ones just added — schemas have no such field; verified live via curl on create/get/update responses

## Out of scope (section 8) — confirm nothing crept in

- [x] No login/token endpoint added
- [x] No password reset flow added
- [x] No `SecurityConfig` changes

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes — 54/54 tests green
- [x] Spec and code reviewed side by side for drift — no drift
