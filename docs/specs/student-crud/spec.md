# Spec: Student CRUD

Status: Deprecated — superseded by `docs/specs/grpc-student/spec.md` (REST → gRPC), then by
`docs/specs/user-unification/spec.md` (Student entity itself removed, merged into `User`). The
REST endpoints and `StudentController` described below no longer exist.
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md` (same shape, mirrored here)

## 1. Goal

Provide full CRUD management of students via `vertice-api`, backed by Postgres, so the BFF/web
clients can list, create, view, update, and delete student records. Mirrors the Trainer CRUD
feature exactly — same entity shape (`name` + unique `email`), same validation/business rules,
same error handling and security model.

## 2. Endpoints

Not yet declared in `openapi/api.yaml` (only Trainers exists there today). Add a `Students` tag
and paths, following the same structure as Trainers:

| Method | Path               | Operation ID   | Success | Notes                        |
|--------|--------------------|----------------|---------|-------------------------------|
| GET    | /api/students      | listStudents   | 200     | Returns all students          |
| POST   | /api/students      | createStudent  | 201     | Body: StudentRequest          |
| GET    | /api/students/{id} | getStudent     | 200     | 404 if not found              |
| PUT    | /api/students/{id} | updateStudent  | 200     | 404 if not found              |
| DELETE | /api/students/{id} | deleteStudent  | 204     | 404 if not found              |

`StudentController` must implement the interface generated from `api.yaml`
(`org.openapi.generator`, `interfaceOnly: true`), same as `TrainerController implements
TrainersApi`.

## 3. Data model

Table `students` (entity `Student` already exists in `com.vertice.api.student`, migration
`V2__create_students_table.sql` already exists from the Trainer feature's boot-fix):

| Column | Type          | Constraints                  |
|--------|---------------|-------------------------------|
| id     | bigint        | PK, identity                  |
| name   | varchar       | not null                      |
| email  | varchar       | not null, unique               |

No new migration needed — `V2` already matches the entity.

## 4. Validation rules

- `name`: required, non-blank (`minLength: 1` in the schema, same as Trainer).
- `email`: required, non-blank, valid email format (`format: email` + `minLength: 1` — the
  `minLength` is required, not optional, per the bug found in Trainer CRUD where `@Email` alone
  lets `""` through).
- `email` must be unique across students.

## 5. Business rules

- Creating a student with an email that already exists → reject, do not create.
- Updating a student to an email used by a *different* student → reject, do not update.
- Updating a student keeping their own unchanged email → succeeds.
- Deleting a student → simply works; no other entity currently references `Student`, so no
  cascade/orphan concerns.

## 6. Error handling

| Scenario                          | Status | Body                                  |
|------------------------------------|--------|----------------------------------------|
| Validation failure (blank/invalid) | 422    | ProblemDetail (existing handler)       |
| Student not found (get/update/delete) | 404 | ProblemDetail (existing handler)     |
| Duplicate email (create/update)    | 409    | ProblemDetail, via the existing `DuplicateEmailException` + `GlobalExceptionHandler#handleDuplicateEmail` (already generic, not Trainer-specific) |

## 7. Security

- All `/api/students/**` endpoints require a valid JWT (per existing `SecurityConfig`,
  `anyRequest().authenticated()`). No new security config needed.
- No role/scope differentiation for this spec, same as Trainer.

## 8. Mapping

Use MapStruct (`StudentMapper`, mirroring `TrainerMapper`) to convert between `Student` entity
and `StudentRequest`/`StudentResponse` generated models.

## 9. Out of scope

- Pagination/filtering/sorting on `listStudents`.
- Soft delete.
- Student authentication/login (JWT is issued externally).
- Any relationship between Student and TrainingPlan/Trainer (not modeled yet).
