# Validation checklist: Trainer gRPC conversion

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `trainer.proto` declares all 6 RPCs with the request/response shapes in the table
- [x] `TrainerResponse`/`TrainerRequest`/`TrainerCreateRequest` field-for-field match the old REST
  schemas (minus id being response-only, minus password never appearing in any response)
- [x] No `Trainer`-named message — used `TrainerResponse` instead (the proto *file* itself still
  generates an incidental `Trainer.java` outer/descriptor class from the filename regardless of
  `java_multiple_files`, but nothing imports it, so no shadowing occurs in practice)

## Business logic (section 3)

- [x] `TrainerService` method signatures unchanged from REST version (same params, same names) —
  only the three `import` lines changed
- [x] `TrainerMapper` still MapStruct-generated, entity ↔ proto request/response — MapStruct
  auto-generates builder-pattern code for the entity→proto direction with zero extra config
  (confirmed via `TrainerResponse.newBuilder()...build()` in the compiled output); only a harmless
  warning about protobuf's own `Builder` methods (`mergeFrom`, `clearField`, etc.) being seen as
  unmapped "properties"
- [x] `Trainer` entity, `TrainerRepository`, migrations untouched — confirmed via `git diff`

## Controller (section 4)

- [x] `TrainerController` extends `TrainerServiceGrpc.TrainerServiceImplBase`, annotated
  `@GrpcService`
- [x] No `TrainersApi`/REST imports remain anywhere in the file
- [x] Each RPC delegates to the same `TrainerService` bean

## Validation (section 5)

- [x] Blank `name` on create → `INVALID_ARGUMENT` — `createTrainer_withBlankName_throwsInvalidArgument`
  + manual grpcurl (`"name: must not be blank"`)
- [x] Blank/malformed `email` on create → `INVALID_ARGUMENT` —
  `createTrainer_withMalformedEmail_throwsInvalidArgument`,
  `createTrainer_withBlankEmail_throwsInvalidArgument`
- [x] Missing/short `password` on create → `INVALID_ARGUMENT` —
  `createTrainer_withShortPassword_throwsInvalidArgument`
- [x] Same validation on update (name/email) — `updateTrainer_withBlankName_throwsInvalidArgument`
  — and on `SetTrainerPassword` (password) — `setTrainerPassword_withShortPassword_throwsInvalidArgument`

## `api.yaml` / REST removal (section 6)

- [x] `/api/trainers` paths removed from `api.yaml`
- [x] `TrainerRequest`/`TrainerCreateRequest`/`TrainerResponse` schemas removed from `api.yaml`
- [x] `SetPasswordRequest`/`ProblemDetail` still present (Student still needs them)
- [x] Old `TrainerControllerTest` (MockMvc) deleted
- [x] `./gradlew build`'s `openApiGenerate` task still succeeds, now generating only the Student
  interfaces/models from the remaining `api.yaml` content

## Testing (section 7)

- [x] `TrainerServiceTest` passes with proto-typed imports, same assertions as before (9/9)
- [x] New gRPC `TrainerControllerTest` (17 tests) covers: list, get (found/missing), create
  (success/duplicate/4 validation cases), update (success/missing/validation), delete (success/
  missing), set password (success/missing/too-short) — real RPC calls via
  `TrainerServiceGrpc.TrainerServiceBlockingStub`, `TrainerService` mocked via `@MockitoBean`,
  `local` profile to skip auth (already covered generically by `grpc-cross-cutting`)

## Out of scope (section 8) — confirm nothing crept in

- [x] No `TrainingPlan`/`Exercise` changes
- [x] `openapi-generator` Gradle plugin/toolchain itself untouched (still active for Student)

## Verification

- [x] `./gradlew test` passes — 60/60 (61 minus the 18 deleted REST tests, plus 17 new gRPC ones)
- [x] Manual: `grpcurl` against `local` profile exercised all 6 RPCs plus error paths against the
  real Postgres-backed service — create → get → update → set-password → duplicate-email
  (`AlreadyExists`) → validation (`InvalidArgument`) → delete → get-after-delete (`NotFound`), all
  behaving exactly as designed
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
