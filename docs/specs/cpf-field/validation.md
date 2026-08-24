# Validation checklist: CPF field for Trainer and Student

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Scope decisions (section 0) — confirm nothing crept beyond what's documented

- [x] Unique + required, same as email
- [x] Format + checksum validated, not just length
- [x] Backfill mirrors `password-storage`'s placeholder-then-NOT-NULL shape, adapted to be
  per-row unique (`LPAD(id, 11, '0')` instead of a single shared empty string) since `cpf` — unlike
  `password_hash` — also needs a unique constraint

## Data model (section 2)

- [x] `V7__add_cpf_to_trainers.sql` / `V8__add_cpf_to_students.sql` exist, mirror `V5`/`V6`'s shape
  (with the unique-per-row backfill adaptation above)
- [x] `Trainer`/`Student` entities have `cpf` (`nullable = false, unique = true`)
- [x] `TrainerRepository`/`StudentRepository` have `findByCpf`
- [x] App boots cleanly against the existing local DB with `ddl-auto=validate` — migration ran
  against real pre-existing rows (id 2 backfilled to `00000000002`, confirmed via `grpcurl`)

## Validation (section 3)

- [x] `@Cpf` rejects wrong length — `CpfValidatorTest` (too short / too long cases)
- [x] `@Cpf` rejects all-same-digit sequences — `00000000000`/`11111111111` cases
- [x] `@Cpf` rejects wrong check digits — `12345678901` case
- [x] `@Cpf` accepts a real valid CPF — `11144477735`

## Controller / request shape (section 4)

- [x] `cpf` present in `TrainerResponse`/`StudentResponse`, `TrainerRequest`/`StudentRequest`,
  `TrainerCreateRequest`/`StudentCreateRequest`
- [x] `CreateValidation`/`UpdateValidation` records validate `cpf` on both controllers

## Duplicate handling (section 5)

- [x] `DuplicateCpfException` exists, mirrors `DuplicateEmailException`
- [x] `assertCpfAvailable` wired into both services' create/update paths
- [x] `GrpcExceptionAdvice` maps it to `ALREADY_EXISTS`
- [x] `GlobalExceptionHandler` (REST) intentionally left untouched — confirmed no changes in diff

## Testing (section 7)

- [x] `CpfValidatorTest` passes (9/9)
- [x] `TrainerServiceTest`/`StudentServiceTest` updated + duplicate-CPF cases pass (11/11, 10/10)
- [x] `TrainerControllerTest`/`StudentControllerTest` updated + invalid/duplicate CPF cases pass
  (20/20, 20/20)

## Verification

- [x] `./gradlew test` passes — 78/78 (59 before this spec + 9 `CpfValidatorTest` + 2 additional
  service-layer duplicate-CPF/keep-own-CPF cases per resource + 3 additional controller-layer
  invalid/duplicate-CPF cases per resource)
- [x] Manual: `grpcurl` — create with valid CPF succeeds, invalid CPF (`12345678901`, fails
  checksum) → `INVALID_ARGUMENT` (`"cpf: must be a valid CPF"`), duplicate CPF → `ALREADY_EXISTS`
  (`"CPF already in use: ..."`), update keeping own CPF succeeds, verified for both Trainer and
  Student against the real Postgres-backed service
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
