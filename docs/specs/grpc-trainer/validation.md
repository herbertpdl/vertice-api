# Validation checklist: Trainer gRPC conversion

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [ ] `trainer.proto` declares all 6 RPCs with the request/response shapes in the table
- [ ] `TrainerResponse`/`TrainerRequest`/`TrainerCreateRequest` field-for-field match the old REST
  schemas (minus id being response-only, minus password never appearing in any response)
- [ ] No `Trainer`-named message (avoids the same-package entity shadowing issue)

## Business logic (section 3)

- [ ] `TrainerService` method signatures unchanged from REST version (same params, same names)
- [ ] `TrainerMapper` still MapStruct-generated, entity ↔ proto request/response
- [ ] `Trainer` entity, `TrainerRepository`, migrations untouched — `git diff` confirms no changes
  to those files

## Controller (section 4)

- [ ] `TrainerController` extends `TrainerServiceGrpc.TrainerServiceImplBase`, annotated
  `@GrpcService`
- [ ] No `TrainersApi`/REST imports remain anywhere in the file
- [ ] Each RPC delegates to the same `TrainerService` bean

## Validation (section 5)

- [ ] Blank `name` on create → `INVALID_ARGUMENT`
- [ ] Blank/malformed `email` on create → `INVALID_ARGUMENT`
- [ ] Missing/short `password` on create → `INVALID_ARGUMENT`
- [ ] Same validation on update (name/email) and on `SetTrainerPassword` (password)

## `api.yaml` / REST removal (section 6)

- [ ] `/api/trainers` paths removed from `api.yaml`
- [ ] `TrainerRequest`/`TrainerCreateRequest`/`TrainerResponse` schemas removed from `api.yaml`
- [ ] `SetPasswordRequest`/`ProblemDetail` still present (Student still needs them)
- [ ] Old `TrainerControllerTest` (MockMvc) deleted
- [ ] `./gradlew build` still generates a valid (non-empty) REST interface set for Student from
  the remaining `api.yaml` content

## Testing (section 7)

- [ ] `TrainerServiceTest` passes with proto-typed imports, same assertions as before
- [ ] New gRPC `TrainerControllerTest` covers: list, get (found/missing), create (success/
  duplicate/validation×3), update (success/missing/duplicate), delete (success/missing), set
  password (success/missing/too-short)

## Out of scope (section 8) — confirm nothing crept in

- [ ] No `TrainingPlan`/`Exercise` changes
- [ ] `openapi-generator` Gradle plugin/toolchain itself untouched (still active for Student)

## Verification

- [ ] `./gradlew test` passes
- [ ] Manual: `grpcurl` against `local` profile exercises all 6 RPCs successfully
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
