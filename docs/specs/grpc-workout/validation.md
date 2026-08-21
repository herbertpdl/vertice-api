# Validation checklist: Workout gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract / business logic / controller

- [x] `workout.proto` declares all 5 RPCs, shares `vertice.plan.v1`
- [x] `ListWorkouts` scoped by `training_plan_id`
- [x] `WorkoutService` validates the training plan exists before create → `NOT_FOUND` otherwise
- [x] `WorkoutMapper` maps `training_plan_id` via `trainingPlan.id`
- [x] Update never changes `trainingPlan`
- [x] Delete cascades to `WorkoutExercise`/`ExerciseSet` (unchanged JPA cascade)
- [x] `WorkoutRepository.findByTrainingPlanId` added
- [x] `WorkoutController` extends `WorkoutServiceGrpc.WorkoutServiceImplBase`, `@GrpcService`
- [x] Blank `name` on create/update → `INVALID_ARGUMENT`
- [x] Missing training plan on create → `NOT_FOUND`
- [x] Missing workout on get/update/delete → `NOT_FOUND`

## Verification

- [x] `./gradlew test` passes — 127/127 (110 before this PR + 17 new: 7 service + 10 controller)
- [x] Manual `grpcurl` against the real Postgres-backed service (trainer → plan → workout fixture
  chain): create (valid plan), create (missing plan → `NotFound`), get, update, list (scoped to
  plan), delete, get-after-delete (`NotFound`) — all exactly as designed
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
