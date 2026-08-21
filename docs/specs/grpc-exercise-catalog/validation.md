# Validation checklist: Exercise catalog gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [ ] `exercise.proto` declares all 5 RPCs with the request/response shapes in the table
- [ ] `ExerciseRequest` used for both create and the nested update body

## Business logic / controller (sections 3–4)

- [ ] `ExerciseService` methods match `TrainerService`'s shape
- [ ] `ExerciseMapper` maps entity ↔ proto with no custom logic needed
- [ ] `ExerciseController` extends `ExerciseServiceGrpc.ExerciseServiceImplBase`, `@GrpcService`
- [ ] Blank `name` on create/update → `INVALID_ARGUMENT`
- [ ] Missing id on get/update/delete → `NOT_FOUND`

## Dead code (section 5)

- [ ] `com.vertice.api.plan.exercise.dto.ExerciseRequest`/`ExerciseResponse` deleted

## Verification

- [ ] `./gradlew test` passes
- [ ] Manual `grpcurl`: create, get, update, list, delete, get-after-delete (`NOT_FOUND`), blank
  name (`INVALID_ARGUMENT`)
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
