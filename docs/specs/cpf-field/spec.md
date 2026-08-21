# Spec: CPF field for Trainer and Student

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`, `docs/specs/student-crud/spec.md`,
`docs/specs/password-storage/spec.md` (same "one spec, one PR, covers both entities" shape as this
one), `docs/specs/grpc-trainer/spec.md`, `docs/specs/grpc-student/spec.md` (the current gRPC
surface this extends)

## 0. Scope decisions

The user asked for a mandatory `cpf` field on both Trainer and Student, without specifying
uniqueness, validation depth, or how to handle the handful of existing local rows that predate
this field — the three follow-up questions asked went unanswered (session timeout). Proceeding
with the same defaults that were proposed and flagged as recommended, documented here so they're
easy to challenge on review rather than silently baked in:

- **Unique, like email**: a CPF is a Brazilian individual taxpayer ID — one per person by
  definition — so it gets the same treatment `email` already has: `NOT NULL` + DB unique
  constraint, checked before insert/update via the same `assertXAvailable` pattern
  `TrainerService`/`StudentService` already use for email.
- **Format + checksum validation**: not just "11 digits" but the real CPF check-digit algorithm,
  so obviously-fake values (`111.111.111-11`, `000.000.000-00`, sequential digits) are rejected,
  not just malformed ones. Stored/transmitted as exactly 11 digits, no punctuation — same
  "canonical form over the wire, formatting is a client concern" choice already made for other
  fields (no phone numbers or similar formatted fields exist yet to compare against, but this
  mirrors how `email`/`name` are plain strings with no client-side masking baked into the API).
- **Backfill existing rows via placeholder, same as `password-storage`**: add the column nullable,
  backfill existing rows with a placeholder value that can never pass the checksum validation
  (so it's inert, not a real usable value), then set `NOT NULL` — identical shape to
  `V5__add_password_hash_to_trainers.sql`/`V6__add_password_hash_to_students.sql`.

If any of these three are wrong, they're isolated (migration backfill value, one `@Cpf`
annotation, one unique constraint) — cheap to revisit.

## 1. Goal

Trainer and Student both gain a required, unique, validated `cpf` field, available everywhere
`name`/`email` already are: create, get, update, list — over gRPC (REST no longer exists for
either resource, see `grpc-trainer`/`grpc-student`).

## 2. Data model

`ALTER TABLE trainers ADD COLUMN cpf VARCHAR(11)`, same for `students`. New migrations:
`V7__add_cpf_to_trainers.sql`, `V8__add_cpf_to_students.sql`, following `V5`/`V6`'s
nullable-backfill-NOT NULL shape, with one difference: `cpf` needs a *unique* backfill value
per existing row (unlike `password_hash`, which shares one empty-string placeholder across all
rows — fine there since it isn't unique). Backfilling every row with the same placeholder would
violate the new unique constraint the moment it's added, so the placeholder is derived from each
row's own `id` instead:

```sql
ALTER TABLE trainers ADD COLUMN cpf VARCHAR(11);
UPDATE trainers SET cpf = LPAD(id::text, 11, '0') WHERE cpf IS NULL;
ALTER TABLE trainers ALTER COLUMN cpf SET NOT NULL;
ALTER TABLE trainers ADD CONSTRAINT uq_trainers_cpf UNIQUE (cpf);
```

The placeholder never needs to itself pass `@Cpf` validation (see §3) — migrations write straight
to the DB, bypassing Bean Validation entirely, and nothing re-validates an existing row unless it
goes through `UpdateTrainer`/`UpdateStudent`, which supplies a whole new value anyway.

`Trainer`/`Student` entities: `@Column(name = "cpf", nullable = false, unique = true) private
String cpf;`. `TrainerRepository`/`StudentRepository` get a `findByCpf(String cpf)` method,
mirroring `findByEmail`.

## 3. Validation: new shared `@Cpf` constraint

A new Jakarta Bean Validation constraint, `com.vertice.api.common.validation.Cpf` +
`CpfValidator`, shared by both resources' controllers (same spirit as `SetPasswordRequest` being
one shared shape both resources used in the old REST `api.yaml`) — validates:

- Exactly 11 digits, no punctuation.
- Not one of the 10 all-same-digit sequences (`00000000000`, `11111111111`, ... `99999999999`) —
  mathematically valid under the checksum below but universally treated as fake/placeholder by
  every real CPF validator, Brazil's included.
- Passes the official two check-digit algorithm (`docs/specs/cpf-field/spec.md` §3.1 below).

### 3.1 Check-digit algorithm

For an 11-digit string `d[0..10]`:

1. First check digit: `sum = Σ d[i] * (10 - i)` for `i` in `0..8`; `remainder = sum % 11`;
   expected digit is `0` if `remainder < 2` else `11 - remainder`. Must equal `d[9]`.
2. Second check digit: same formula over `i` in `0..9` with weights `(11 - i)`, compared against
   `d[10]`.

## 4. Controller / request shape (gRPC)

`cpf` is added to `TrainerResponse`/`StudentResponse` (repeated in `List*`), `TrainerRequest`/
`StudentRequest` (update body — same "person's own record" fields as `name`/`email`, so it's
editable, matching how a REST version of this would have shaped it), and `TrainerCreateRequest`/
`StudentCreateRequest`. `TrainerController`/`StudentController`'s private validation records
(`CreateValidation`, `UpdateValidation`) gain a `@Cpf String cpf` field alongside the existing
`@NotBlank`/`@Email` ones — same mechanism `grpc-trainer`/`grpc-student` already built, no new
validation plumbing needed beyond the annotation itself.

## 5. Duplicate handling

New `DuplicateCpfException` (`com.vertice.api.common.exception`), mirroring `DuplicateEmailException`
exactly rather than generalizing the two into one type — smaller, lower-risk change, consistent
with the existing one-exception-per-field convention. `TrainerService`/`StudentService` gain an
`assertCpfAvailable(cpf, excludingId)` private method, called from `create*`/`update*` the same way
`assertEmailAvailable` already is. `GrpcExceptionAdvice` gains one more `@GrpcExceptionHandler`
mapping `DuplicateCpfException` → `Status.ALREADY_EXISTS`. Not wired into REST's
`GlobalExceptionHandler` — that class currently has zero active callers (REST is gone for Trainer/
Student, `TrainingPlan` has no endpoints yet), so adding a handler there would be dead code.

## 6. Out of scope

- Formatting/masking (`XXX.XXX.XXX-XX`) — client/BFF concern, not this API's.
- CPF-based lookup RPC (`GetTrainerByCpf` or similar) — not asked for.
- Any change to REST, `openapi/api.yaml` (deleted in `grpc-cleanup`), or `TrainingPlan`/`Exercise`.
- Migrating the placeholder-backfilled existing rows to real CPFs — same as `password-storage`'s
  equivalent call for password hashes, those rows are locked out of passing validation on their
  next update until someone sets a real value.

## 7. Testing

- `CpfValidatorTest`: direct unit test of the checksum algorithm — known-valid CPFs pass,
  known-fake ones (`00000000000`, wrong check digits, wrong length) fail.
- `TrainerServiceTest`/`StudentServiceTest`: add `cpf` to existing request construction, add
  duplicate-CPF rejection cases mirroring the existing duplicate-email ones.
- `TrainerControllerTest`/`StudentControllerTest`: add `cpf` to existing valid-request cases, add
  invalid-CPF → `INVALID_ARGUMENT` and duplicate-CPF → `ALREADY_EXISTS` cases.
