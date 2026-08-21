# Validation checklist: Exercise catalog gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `exercise.proto` declares all 5 RPCs with the request/response shapes in the table
- [x] `ExerciseRequest` used for both create and the nested update body

## Business logic / controller (sections 3–4)

- [x] `ExerciseService` methods match `TrainerService`'s shape
- [x] `ExerciseMapper` maps entity ↔ proto with no custom logic needed
- [x] `ExerciseController` extends `ExerciseServiceGrpc.ExerciseServiceImplBase`, `@GrpcService`
- [x] Blank `name` on create/update → `INVALID_ARGUMENT` —
  `ExerciseControllerTest#createExercise_withBlankName_throwsInvalidArgument` + manual grpcurl
  (`"name: must not be blank"`)
- [x] Missing id on get/update/delete → `NOT_FOUND` — 3 controller tests + manual grpcurl
  get-after-delete

## Dead code (section 5)

- [x] `com.vertice.api.plan.exercise.dto.ExerciseRequest`/`ExerciseResponse` deleted

## Verification

- [x] `./gradlew test` passes — 92/92 (78 before this PR + 14 new: 5 service + 9 controller)
- [x] Manual `grpcurl`: create, get, update, list, delete, get-after-delete (`NOT_FOUND`), blank
  name (`INVALID_ARGUMENT`) — all verified against the real Postgres-backed service, exact
  behavior as designed
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
