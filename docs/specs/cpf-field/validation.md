# Validation checklist: CPF field for Trainer and Student

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Scope decisions (section 0) — confirm nothing crept beyond what's documented

- [ ] Unique + required, same as email
- [ ] Format + checksum validated, not just length
- [ ] Backfill mirrors the password-storage placeholder pattern exactly

## Data model (section 2)

- [ ] `V7__add_cpf_to_trainers.sql` / `V8__add_cpf_to_students.sql` exist, mirror `V5`/`V6`'s shape
- [ ] `Trainer`/`Student` entities have `cpf` (`nullable = false, unique = true`)
- [ ] `TrainerRepository`/`StudentRepository` have `findByCpf`
- [ ] App boots cleanly against a fresh DB with `ddl-auto=validate`

## Validation (section 3)

- [ ] `@Cpf` rejects wrong length
- [ ] `@Cpf` rejects all-same-digit sequences
- [ ] `@Cpf` rejects wrong check digits
- [ ] `@Cpf` accepts a real valid CPF

## Controller / request shape (section 4)

- [ ] `cpf` present in `TrainerResponse`/`StudentResponse`, `TrainerRequest`/`StudentRequest`,
  `TrainerCreateRequest`/`StudentCreateRequest`
- [ ] `CreateValidation`/`UpdateValidation` records validate `cpf` on both controllers

## Duplicate handling (section 5)

- [ ] `DuplicateCpfException` exists, mirrors `DuplicateEmailException`
- [ ] `assertCpfAvailable` wired into both services' create/update paths
- [ ] `GrpcExceptionAdvice` maps it to `ALREADY_EXISTS`
- [ ] `GlobalExceptionHandler` (REST) intentionally left untouched

## Testing (section 7)

- [ ] `CpfValidatorTest` passes
- [ ] `TrainerServiceTest`/`StudentServiceTest` updated + duplicate-CPF cases pass
- [ ] `TrainerControllerTest`/`StudentControllerTest` updated + invalid/duplicate CPF cases pass

## Verification

- [ ] `./gradlew test` passes
- [ ] Manual: `grpcurl` — create with valid CPF succeeds, invalid CPF → `INVALID_ARGUMENT`,
  duplicate CPF → `ALREADY_EXISTS`
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
