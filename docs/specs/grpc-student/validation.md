# Validation checklist: Student gRPC conversion

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [ ] `student.proto` declares all 6 RPCs with the request/response shapes in the table
- [ ] `StudentResponse`/`StudentRequest`/`StudentCreateRequest` field-for-field match the old REST
  schemas

## Business logic / controller / validation (section 3)

- [ ] `StudentService`/`StudentMapper` method signatures unchanged, only imports retargeted
- [ ] `StudentController` extends `StudentServiceGrpc.StudentServiceImplBase`, `@GrpcService`, no
  REST imports remain
- [ ] Validation records mirror the old REST rules (name/email/password)
- [ ] `Student` entity, `StudentRepository`, migrations untouched

## `api.yaml` (section 4)

- [ ] `/api/students` paths removed
- [ ] `StudentRequest`/`StudentCreateRequest`/`StudentResponse`/`SetPasswordRequest` schemas
  removed
- [ ] `ProblemDetail` removed (nothing references it anymore)
- [ ] `api.yaml` has an empty `paths:` map
- [ ] `./gradlew build`'s `openApiGenerate` task still succeeds against the now-empty spec

## Testing (section 5)

- [ ] `StudentServiceTest` passes with proto-typed imports
- [ ] New gRPC `StudentControllerTest` covers the same cases `TrainerControllerTest` does

## Out of scope (section 6) — confirm nothing crept in

- [ ] No `TrainingPlan`/`Exercise` changes
- [ ] `api.yaml`/openapi-generator toolchain not deleted (left for `grpc-cleanup`)

## Verification

- [ ] `./gradlew test` passes
- [ ] Manual: `grpcurl` against `local` profile exercises all 6 RPCs + error paths
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
