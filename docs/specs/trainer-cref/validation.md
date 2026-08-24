# Validation checklist: CREF field on Trainer

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual curl, or code read) — not assumed.

Re-verified after the spec was re-targeted from REST to gRPC (section 0 of `spec.md`).

## Design decision (section 2)

- [x] `cref` added to `trainer.proto` as a plain `string` field, no `optional` keyword — matches
      `name`/`email`/`cpf`'s style
- [x] `TrainerController`'s `CreateValidation`/`UpdateValidation` records unchanged — no annotation
      added for `cref`, confirmed by code read
- [x] Omitting `cref` never fails validation — `TrainerControllerTest#createTrainer_withoutCref_returnsCreated`

## Data model (section 3)

- [x] `Trainer.cref` field added, `@Column(length = 20)`, no `nullable = false` — nullable by default
- [x] Migration `V13__add_cref_to_trainers.sql` adds a nullable `cref VARCHAR(20)` column, no
      backfill/NOT NULL step
- [x] `V13` is the correct next migration number on this branch (`feature/grpc-workout`'s tip
      already occupies `V1`–`V12`) — confirmed via `ls src/main/resources/db/migration`

## Schema changes (section 4)

- [x] `cref` added as field 5 on `TrainerResponse`, field 4 on `TrainerRequest`, field 5 on
      `TrainerCreateRequest` in `trainer.proto` — existing field numbers (1–4, or 1–3 for
      `TrainerRequest`) untouched
- [x] `./gradlew generateProto compileJava compileTestJava` succeeds — generated builders/getters
      for `cref` exist and are used by tests

## Validation rules (section 5)

- [x] No format/pattern validation enforced — no test asserts a "malformed CREF" rejection
- [x] Omitted `cref` arrives as `""` (protobuf default), not `null`, on the entity —
      `TrainerServiceTest#createTrainer_leavesCrefBlankWhenOmitted` asserts `.isEmpty()`, not
      `.isNull()`
- [x] Providing `cref` on create is persisted and echoed —
      `TrainerControllerTest#createTrainer_withCref_returnsCreated`,
      `TrainerServiceTest#createTrainer_mapsCrefWhenProvided`
- [x] Providing `cref` on update is persisted and echoed —
      `TrainerControllerTest#updateTrainer_withCref_returnsUpdated`,
      `TrainerServiceTest#updateTrainer_updatesCref`

## Mapping (section 6)

- [x] No `TrainerMapper` changes were needed — `cref` flows entity↔proto purely through
      MapStruct's same-name auto-mapping, same as `cpf`

## Out of scope (section 7) — confirm nothing crept in

- [x] No format/checksum validation added for `cref`
- [x] No uniqueness constraint added on `cref`
- [x] No vertice-web changes made
- [x] `cref` did not become mandatory anywhere

## Sign-off

- [x] `./gradlew test --tests "com.vertice.api.trainer.*"` passes — all trainer-package tests
      green (includes full `@SpringBootTest` gRPC integration tests against the real local
      Postgres, not just mocked slices)
- [x] Full `./gradlew test`: 133 tests, 0 failures, 0 errors — this also resolved the two
      previously-failing full-context tests (`VerticeApiApplicationTests#contextLoads`,
      `GrpcHealthCheckTest`) noted in the original (REST-era) validation pass: those failed only
      because `main`'s schema/entities were stale relative to the shared local dev DB; integrating
      onto `feature/grpc-workout`'s tip fixed that as a side effect, not something this feature
      needed to fix directly
- [x] Spec and code reviewed side by side for drift — no drift
