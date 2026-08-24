# Spec: Unify Trainer and Student into User + Role

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`, `docs/specs/student-crud/spec.md`,
`docs/specs/cpf-field/spec.md`, `docs/specs/trainer-cref/spec.md`,
`docs/specs/grpc-trainer/spec.md`, `docs/specs/grpc-student/spec.md`

## 1. Goal

Replace the `Trainer` and `Student` entities (and their fully duplicated proto services,
controllers, mappers, repositories, and services — confirmed byte-for-byte identical after
normalizing names) with one `User` entity distinguished by a `role` enum: `ADMIN`, `TRAINER`,
`CLIENT`. One gRPC `UserService` replaces `TrainerService`/`StudentService`.

`CLIENT` replaces `STUDENT` as the name for the trainer's paying customer/mentee — "student" was a
poor fit for a fitness-coaching product.

## 2. Why now, and why not before

This was deliberately deferred earlier: at the time, ~13 branches were actively building CRUD/gRPC
work against the two-entity shape, and merging would have triggered conflicts across all of them.
That work has since landed on `main` (see `docs/specs/grpc-workout/spec.md` and the PR #15
integration). With `main` current and no open PRs, this is now the right time — and inspecting the
real code confirmed the case: `TrainerController`/`StudentController` (and the mapper/repository/
proto pairs) are identical except for the name, and `TrainingPlan` — the one place a
Trainer/Student relationship might have created real modeling tension — has **no** relationship to
Student in code yet (only `trainer_id`; the domain model doc's "for a student" framing is
aspirational, not implemented). There was no existing FK/relationship code to design around.

## 3. Design decisions

### 3.1 Single table, role enum, not per-role tables or subclassing

`users` table, one `User` JPA entity, `role` column (`@Enumerated(EnumType.STRING)`). Rejected
alternatives:
- **Joined-table inheritance** (`User` base + `Trainer`/`Client` subclass tables): adds JPA
  inheritance complexity for a case where the only role-specific field is `cref` (one nullable
  column) — not enough divergence to justify it today. Revisit if role-specific fields grow.
- **Keep separate tables, add a shared `@MappedSuperclass`**: kills the Java-level duplication but
  not the two nearly-identical proto services, and doesn't solve the underlying "which table does
  this email belong to" ambiguity (see 3.3). Rejected in favor of the fuller unification.

### 3.2 `cref` stays a plain nullable column, not role-partitioned

`cref` (added in `docs/specs/trainer-cref/spec.md`) is only meaningful for `TRAINER`-role users,
but there's no dedicated trainer table to hold it anymore. It stays a nullable `varchar(20)` column
on `users`, always empty (`""`, per its existing proto3-default behavior) for non-trainers. New in
this spec: `UserService` rejects (`INVALID_ARGUMENT`) a non-blank `cref` on a request whose `role`
isn't `TRAINER` — a small application-level guard against nonsensical data, cheaper than a
DB `CHECK` constraint and easier to test.

### 3.3 Email and CPF become globally unique across all roles

Today, `trainers.email` and `students.email` are uniquely constrained independently — a trainer and
a student could share an email. Merging into one table with one `unique` column makes email (and
CPF) unique **across every role**. This is a deliberate behavior change, not just a refactor
side-effect: one email should mean one account for a real auth system, regardless of role,
otherwise "log in with this email" is ambiguous. Same reasoning applies to CPF, which was already
mandatory and unique per-entity.

### 3.4 One gRPC `UserService`, not per-role facades

Single `user.proto` replaces `trainer.proto`/`student.proto`. `ListUsers` takes an optional `role`
filter (`ROLE_UNSPECIFIED` = no filter, list everyone) rather than separate `ListTrainers`/
`ListStudents`/`ListAdmins` RPCs. Nothing external consumes the current `TrainerService`/
`StudentService` API yet (vertice-web has no client code calling either), so this was the cheapest
possible time to collapse the API surface along with the entity — waiting would only make it more
expensive.

### 3.5 Role-based authorization is explicitly out of scope

`GrpcSecurityConfig`/`SecurityConfig` currently implement "any authenticated caller may do
anything," documented as intentional (no role/scope differentiation). `role` on `User` is a data
classification field only in this spec — it does not gate any endpoint. Wiring `role` into
authorization is a separate, later spec.

## 4. Data model

```
users
  id             BIGINT PK
  name           VARCHAR(255) NOT NULL
  email          VARCHAR(255) NOT NULL, UNIQUE  (global, see 3.3)
  cpf            VARCHAR(11)  NOT NULL, UNIQUE  (global, see 3.3)
  cref           VARCHAR(20)  NULL             (TRAINER-only in practice, see 3.2)
  password_hash  VARCHAR(255) NOT NULL
  role           VARCHAR(20)  NOT NULL          (ADMIN | TRAINER | CLIENT)
```

Migration `V14__create_users_table.sql`:
- Creates `users` with `id GENERATED BY DEFAULT AS IDENTITY` (not `ALWAYS`) so trainer rows can be
  reinserted with their **original** `id` preserved — `training_plans.trainer_id` already has a
  live FK to `trainers(id)` with real (local test) data, and must keep resolving after the merge.
- Copies `trainers` rows into `users` with explicit `id`, `role = 'TRAINER'`.
- Advances the `users_id_seq` past the highest preserved id.
- Copies `students` rows into `users` with **fresh** auto-generated ids and `role = 'CLIENT'` —
  nothing references `students.id` today, so no id preservation is needed there.
- Repoints `training_plans`'s FK from `trainers(id)` to `users(id)` (column stays named
  `trainer_id` — it still means "the trainer who owns this plan," now just stored as a `User` id;
  renaming the column is a cosmetic change deliberately left out of this spec).
- Drops `trainers` and `students`.
- If any local trainer/student row currently shares an email or CPF with a row in the other table,
  this migration's unique constraints will fail it — acceptable for local dev data (no
  production data exists yet, confirmed at the start of this work), not something the migration
  tries to silently resolve.

## 5. proto (`vertice/user/v1/user.proto`)

```proto
enum Role {
  ROLE_UNSPECIFIED = 0;
  ADMIN = 1;
  TRAINER = 2;
  CLIENT = 3;
}

service UserService {
  rpc ListUsers(ListUsersRequest) returns (ListUsersResponse);   // optional role filter
  rpc GetUser(GetUserRequest) returns (UserResponse);
  rpc CreateUser(UserCreateRequest) returns (UserResponse);
  rpc UpdateUser(UpdateUserRequest) returns (UserResponse);
  rpc DeleteUser(DeleteUserRequest) returns (google.protobuf.Empty);
  rpc SetUserPassword(SetUserPasswordRequest) returns (google.protobuf.Empty);
}
```

`UserResponse`/`UserRequest`/`UserCreateRequest` carry `name, email, cpf, cref, role` (+ `password`
on create). `role` is required and validated as not `ROLE_UNSPECIFIED` (see section 6) — proto3
enums always have a zero value, so "role omitted" and "role explicitly unspecified" aren't
distinguishable, same class of limitation already accepted for `cref`'s blank-vs-unset ambiguity
in `docs/specs/trainer-cref/spec.md`.

## 6. Validation rules

- `name`: required, non-blank (unchanged from Trainer/Student).
- `email`: required, non-blank, valid format, **globally** unique (3.3).
- `cpf`: required, valid CPF (existing `@Cpf` validator, reused as-is), **globally** unique (3.3).
- `cref`: optional, `maxLength: 20`, no format validation (unchanged from
  `docs/specs/trainer-cref/spec.md`) — **new**: rejected with `INVALID_ARGUMENT` if non-blank and
  `role != TRAINER` (3.2).
- `role`: required, must not be `ROLE_UNSPECIFIED`.
- `password`: required on create, `minLength: 8` (unchanged).

## 7. Mapping

`UserMapper` (MapStruct) replaces `TrainerMapper`/`StudentMapper` — same auto-map-by-name pattern,
`id`/`passwordHash` ignored on the entity-writing directions, same as before.

## 8. Out of scope

- Role-based authorization/access control (3.5).
- Admin-specific endpoints or behavior — `ADMIN` exists as a role value only.
- Renaming the `training_plans.trainer_id` column.
- Distinguishing NULL from empty-string storage for `cref` (carried over from
  `docs/specs/trainer-cref/spec.md`, unaffected by this merge).
- Any vertice-web/vertice-bff changes (neither consumes this API yet).
- Deleting the now-superseded `feature/cpf-field`, `feature/grpc-trainer`, `feature/grpc-student`,
  etc. branches from the remote — out of scope for this spec, a separate housekeeping decision.
