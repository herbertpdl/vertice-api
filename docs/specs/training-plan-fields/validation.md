# Validation checklist: TrainingPlan client assignment, dates, and level

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Data model (section 2)

- [x] `V15` migration follows nullable-add → backfill → `NOT NULL`, verified live against the
  running `vertice-postgres` container (one pre-existing `training_plans` row, backfilled and
  migrated successfully — confirmed via `./gradlew test` exercising the real Flyway migration
  against that DB, and a direct `docker exec ... psql` read of `training_plans`/`users` before
  writing the migration)
- [x] `TrainingPlan` entity gains `client` (`@ManyToOne`, mirrors `trainer`), `startDate`,
  `endDate` (`LocalDate`), `level` (`PlanLevel` enum) — code read
- [x] `TrainingPlanRepository.findByClientId` added — code read

## Contract (section 3)

- [x] `PlanLevel` proto enum added (`PLAN_LEVEL_UNSPECIFIED`/`BEGINNER`/`INTERMEDIATE`/`ADVANCED`)
- [x] `TrainingPlanResponse`/`TrainingPlanRequest`/`TrainingPlanCreateRequest` all gain
  `client_id`/`start_date`/`end_date`/`level`
- [x] `ListTrainingPlansRequest` gains `client_id` alongside `trainer_id`

## Validation rules (section 4)

- [x] Blank `name` → `INVALID_ARGUMENT` —
  `TrainingPlanControllerTest#createTrainingPlan_withBlankName_throwsInvalidArgument`
- [x] `client_id` unset (`0`) → `INVALID_ARGUMENT` —
  `#createTrainingPlan_withMissingClientId_throwsInvalidArgument`
- [x] `client_id` not resolving to a `CLIENT` → `NOT_FOUND` —
  `TrainingPlanServiceTest#createTrainingPlan_throwsWhenClientMissing`,
  `#createTrainingPlan_throwsWhenUserIsNotClient`
- [x] Blank `start_date`/`end_date` → `INVALID_ARGUMENT` —
  `TrainingPlanControllerTest#createTrainingPlan_withBlankStartDate_throwsInvalidArgument`
- [x] Unparsable date string → `INVALID_ARGUMENT` —
  `TrainingPlanServiceTest#createTrainingPlan_throwsWhenDateUnparsable`
- [x] `end_date` before `start_date` → `INVALID_ARGUMENT` —
  `TrainingPlanServiceTest#createTrainingPlan_throwsWhenEndDateBeforeStartDate`
- [x] `end_date` equal to `start_date` allowed —
  `TrainingPlanServiceTest#createTrainingPlan_allowsEndDateEqualToStartDate`
- [x] `level` unset (`PLAN_LEVEL_UNSPECIFIED`) → `INVALID_ARGUMENT` —
  `TrainingPlanControllerTest#createTrainingPlan_withUnsetLevel_throwsInvalidArgument`
- [x] Update re-validates and can reassign `client_id`/dates/level —
  `TrainingPlanServiceTest#updateTrainingPlan_updatesFieldsAndReassignsClient`,
  `#updateTrainingPlan_throwsWhenNewClientMissing`

## Listing (section 3)

- [x] Trainer-only filter — `TrainingPlanServiceTest#listTrainingPlans_filtersByTrainerOnly`
- [x] Client-only filter — `#listTrainingPlans_filtersByClientOnly`
- [x] Both filters (AND semantics) — `#listTrainingPlans_filtersByBothTrainerAndClient`
- [x] Neither filter returns everything — `#listTrainingPlans_returnsAllWhenNeitherFilterSet`
- [x] Controller passes `0` proto defaults through as `null` to the service —
  `TrainingPlanControllerTest#listTrainingPlans_withTrainerIdFilter_passesTrainerIdOnly`,
  `#listTrainingPlans_withClientIdFilter_passesClientIdOnly`,
  `#listTrainingPlans_withNoFilters_passesBothNull`

## Mapping (section 5)

- [x] `ProtoDates` (`stringToDate`/`dateToString`) added, mirrors `ProtoStrings` — code read
- [x] `PlanLevel` proto↔entity mapping uses `@ValueMapping` for the unmapped
  `PLAN_LEVEL_UNSPECIFIED`/`UNRECOGNIZED` source constants, mirrors `UserMapper#mapRole` — code
  read

## Verification

- [x] `./gradlew test` passes — 116/116 (full suite, run against the real Postgres container)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
