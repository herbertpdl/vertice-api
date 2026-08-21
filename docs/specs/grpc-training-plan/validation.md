# Validation checklist: TrainingPlan gRPC CRUD

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Contract (section 2)

- [x] `training_plan.proto` declares all 5 RPCs, `package vertice.plan.v1`
- [x] `ListTrainingPlans` scoped by `trainer_id`

## Business logic / controller (sections 3–4)

- [x] `TrainingPlanService` validates the trainer exists before create → `NOT_FOUND` otherwise —
  `TrainingPlanServiceTest#createTrainingPlan_throwsWhenTrainerMissing` + manual grpcurl
- [x] `TrainingPlanMapper` maps `trainer_id` via `trainer.id` source path
- [x] Update never changes `trainer`
- [x] Delete cascades to `Workout`/`WorkoutExercise`/`ExerciseSet` (JPA cascade, unchanged from
  `workout-exercise-sets`)
- [x] `TrainingPlanRepository.findByTrainerId` added
- [x] `TrainingPlanController` extends `TrainingPlanServiceGrpc.TrainingPlanServiceImplBase`,
  `@GrpcService`, replaces the old REST stub file entirely
- [x] Blank `name` on create/update → `INVALID_ARGUMENT`
- [x] Missing trainer on create → `NOT_FOUND`
- [x] Missing plan on get/update/delete → `NOT_FOUND`
- [x] Nullable `description` reuses `ProtoStrings#nullToEmpty` —
  `TrainingPlanServiceTest#listTrainingPlans_returnsPlansForTrainer` asserts empty (not crashing)
  + manual grpcurl create-without-description succeeded live

## Dead code (section 5)

- [x] `com.vertice.api.plan.dto.TrainingPlanRequest`/`TrainingPlanResponse` deleted

## Verification

- [x] `./gradlew test` passes — 110/110 (93 before this PR + 17 new: 7 service + 10 controller)
- [x] Manual `grpcurl` against the real Postgres-backed service: create (valid trainer, no
  description — confirms the null-safety fix live), create (missing trainer → `NotFound`), blank
  name (`InvalidArgument`), get, update, list (scoped to trainer), delete, get-after-delete
  (`NotFound`)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
