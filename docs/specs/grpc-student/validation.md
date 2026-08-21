# Validation checklist: Student gRPC conversion

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `student.proto` declares all 6 RPCs with the request/response shapes in the table
- [x] `StudentResponse`/`StudentRequest`/`StudentCreateRequest` field-for-field match the old REST
  schemas

## Business logic / controller / validation (section 3)

- [x] `StudentService`/`StudentMapper` method signatures unchanged, only imports retargeted
- [x] `StudentController` extends `StudentServiceGrpc.StudentServiceImplBase`, `@GrpcService`, no
  REST imports remain
- [x] Validation records mirror the old REST rules (name/email/password) — same shape as
  `TrainerController`'s
- [x] `Student` entity, `StudentRepository`, migrations untouched — confirmed via `git diff`

## `api.yaml` (section 4)

- [x] `/api/students` paths removed
- [x] `StudentRequest`/`StudentCreateRequest`/`StudentResponse`/`SetPasswordRequest` schemas
  removed
- [x] `ProblemDetail` removed (nothing references it anymore)
- [x] `api.yaml` has an empty `paths: {}` map — only `openapi`/`info` remain besides it
- [x] `./gradlew build`'s `openApiGenerate` task still succeeds against the now-empty spec (no
  errors, generates zero interfaces/models — confirmed via a clean `compileTestJava`)

## Testing (section 5)

- [x] `StudentServiceTest` passes with proto-typed imports (8/8)
- [x] New gRPC `StudentControllerTest` (17 tests) covers the same cases `TrainerControllerTest`
  does — list, get (found/missing), create (success/duplicate/4 validation cases), update
  (success/missing/validation), delete (success/missing), set password (success/missing/too-short)

## Out of scope (section 6) — confirm nothing crept in

- [x] No `TrainingPlan`/`Exercise` changes
- [x] `api.yaml`/openapi-generator toolchain not deleted (left for `grpc-cleanup`)

## Verification

- [x] `./gradlew test` passes — 59/59 (60 from `grpc-trainer`, minus the 18 deleted REST
  `StudentControllerTest` cases, plus 17 new gRPC ones — the 1 fewer is the REST-only "no JWT →
  401" case, redundant with `grpc-cross-cutting`'s cross-cutting auth tests, not a coverage gap)
- [x] Manual: `grpcurl` against `local` profile exercised all 6 RPCs plus error paths against the
  real Postgres-backed service — create → get → update → set-password → duplicate-email
  (`AlreadyExists`) → validation (`InvalidArgument`, malformed email) → delete → get-after-delete
  (`NotFound`) → list; also confirmed both `vertice.trainer.v1.TrainerService` and
  `vertice.student.v1.StudentService` are simultaneously registered and independently reachable
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
