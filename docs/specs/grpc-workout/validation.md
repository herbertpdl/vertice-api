# Validation checklist: Workout gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract / business logic / controller

- [ ] `workout.proto` declares all 5 RPCs, shares `vertice.plan.v1`
- [ ] `ListWorkouts` scoped by `training_plan_id`
- [ ] `WorkoutService` validates the training plan exists before create → `NOT_FOUND` otherwise
- [ ] `WorkoutMapper` maps `training_plan_id` via `trainingPlan.id`
- [ ] Update never changes `trainingPlan`
- [ ] Delete cascades to `WorkoutExercise`/`ExerciseSet`
- [ ] `WorkoutRepository.findByTrainingPlanId` added
- [ ] `WorkoutController` extends `WorkoutServiceGrpc.WorkoutServiceImplBase`, `@GrpcService`
- [ ] Blank `name` on create/update → `INVALID_ARGUMENT`
- [ ] Missing training plan on create → `NOT_FOUND`
- [ ] Missing workout on get/update/delete → `NOT_FOUND`

## Verification

- [ ] `./gradlew test` passes
- [ ] Manual `grpcurl`: create (valid plan), create (missing plan → `NotFound`), blank name
  (`InvalidArgument`), get, update, list (scoped), delete, get-after-delete (`NotFound`)
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
