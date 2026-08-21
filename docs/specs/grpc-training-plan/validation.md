# Validation checklist: TrainingPlan gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [ ] `training_plan.proto` declares all 5 RPCs, `package vertice.plan.v1`
- [ ] `ListTrainingPlans` scoped by `trainer_id`

## Business logic / controller (sections 3–4)

- [ ] `TrainingPlanService` validates the trainer exists before create → `NOT_FOUND` otherwise
- [ ] `TrainingPlanMapper` maps `trainer_id` via `trainer.id` source path
- [ ] Update never changes `trainer`
- [ ] Delete cascades to `Workout`/`WorkoutExercise`/`ExerciseSet`
- [ ] `TrainingPlanRepository.findByTrainerId` added
- [ ] `TrainingPlanController` extends `TrainingPlanServiceGrpc.TrainingPlanServiceImplBase`,
  `@GrpcService`, replaces the old REST stub file entirely
- [ ] Blank `name` on create/update → `INVALID_ARGUMENT`
- [ ] Missing trainer on create → `NOT_FOUND`
- [ ] Missing plan on get/update/delete → `NOT_FOUND`

## Dead code (section 5)

- [ ] `com.vertice.api.plan.dto.TrainingPlanRequest`/`TrainingPlanResponse` deleted

## Verification

- [ ] `./gradlew test` passes
- [ ] Manual `grpcurl`: create (valid trainer), create (missing trainer → `NotFound`), get, update,
  list (scoped), delete, get-after-delete (`NotFound`), blank name (`InvalidArgument`)
- [ ] Spec and code reviewed side by side for drift

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
