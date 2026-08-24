# Validation checklist: Unify Trainer and Student into User + Role

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl/psql, or code read) — not assumed.

## Design decisions (section 3)

- [x] Single `User` entity/`users` table, `role` enum column — no subclassing/joined-table
      inheritance — confirmed by code read of `User.java`
- [x] `cref` stays a plain nullable column; `UserService.assertCrefOnlyForTrainer` rejects
      non-blank `cref` for non-`TRAINER` roles — `UserServiceTest#createUser_rejectsCrefForNonTrainer`
- [x] Email and CPF are globally unique (single `unique` constraint on the merged table, no
      per-role scoping) — `UserServiceTest#createUser_rejectsDuplicateEmailAcrossRoles` (a CLIENT
      create is rejected for reusing a TRAINER's email); DB-level: `uq_users_email`/`uq_users_cpf`
      in `V14__create_users_table.sql`
- [x] One `UserService` gRPC service (`user.proto`) replaces `TrainerService`/`StudentService` —
      old proto files deleted, confirmed via `git rm`
- [x] No authorization logic added — `role` is not checked anywhere in `GrpcSecurityConfig`/
      `SecurityConfig`, confirmed by code read (unchanged files)

## Data model (section 4)

- [x] Migration `V14__create_users_table.sql` ran cleanly against the real local dev DB (not just
      a fresh schema) — verified via `psql`: trainer id `7` preserved exactly (matches its
      pre-migration id), students `1`/`2` got fresh ids `8`/`9`, roles set correctly
      (`TRAINER`/`CLIENT`/`CLIENT`)
- [x] `training_plans.trainer_id` still resolves after the migration — verified via `psql`:
      `training_plans` row `id=3` still has `trainer_id=7`, and `fk_training_plans_trainer` now
      references `users(id)`
- [x] `trainers`/`students` tables no longer exist — verified via `psql \dt`
- [x] `users_id_seq` was advanced past the preserved trainer ids — confirmed indirectly: the
      student rows got ids `8`/`9` (not colliding with trainer id `7`), which only happens if the
      sequence was advanced correctly

## proto (section 5)

- [x] `Role` enum: `ROLE_UNSPECIFIED = 0`, `ADMIN`, `TRAINER`, `CLIENT` — matches
      `com.vertice.api.user.Role`'s three real constants
- [x] `ListUsers` takes an optional `role` filter — `UserControllerTest#listUsers_returnsAll` (no
      filter) and `#listUsers_withRoleFilter_passesFilterThrough`
- [x] `./gradlew generateProto compileJava` succeeds — generated builders/getters for all new
      message/enum types exist and are used by tests

## Validation rules (section 6)

- [x] `role` required, `ROLE_UNSPECIFIED` rejected with `INVALID_ARGUMENT` on both create and
      update — `UserControllerTest#createUser_withMissingRole_throwsInvalidArgument`,
      `#updateUser_withMissingRole_throwsInvalidArgument`
- [x] `name`/`email`/`password`/`cpf` validation unchanged from Trainer/Student —
      `UserControllerTest#createUser_withBlankName_throwsInvalidArgument`,
      `#createUser_withMalformedEmail_throwsInvalidArgument`,
      `#createUser_withShortPassword_throwsInvalidArgument`,
      `#createUser_withInvalidCpf_throwsInvalidArgument`
- [x] `cref` optional, no format validation, rejected only when set for a non-TRAINER role (6) —
      see design-decisions row above

## Mapping (section 7)

- [x] `UserMapper` auto-maps by name, same pattern as `TrainerMapper`/`StudentMapper`
- [x] **Bug found and fixed during this work**: `UserResponse toResponse(User user)` originally
      NPE'd on any real DB-loaded row with `cref == null` (every CLIENT/ADMIN, or a TRAINER who
      hasn't set one) — protobuf string setters reject `null`, and `cref` is the only nullable
      string column on `User`. Fixed by routing `cref` through `ProtoStrings.nullToEmpty`, the same
      pattern `TrainingPlanMapper` already uses for its nullable `description` field. Caught by
      `UserServiceTest#listUsers_withoutFilter_returnsAll`/`#listUsers_withRoleFilter_returnsOnlyThatRole`
      once those were given realistic (fully-populated) entities to map — not caught by any
      controller test, since those mock the service layer entirely and never exercise the real
      mapper against a DB-loaded entity

## Out of scope (section 8) — confirm nothing crept in

- [x] No role-based authorization added
- [x] No admin-specific endpoints/behavior beyond the enum value existing
- [x] `training_plans.trainer_id` column name unchanged
- [x] No vertice-web/vertice-bff changes made

## Sign-off

- [x] `./gradlew clean test` passes — full suite green (all trainer/student tests replaced by
      equivalent-or-broader user tests; training-plan tests updated and passing against the real
      migrated local DB)
- [x] Spec and code reviewed side by side for drift — no drift
