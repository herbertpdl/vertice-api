# Technical assessment: Create Workout With Exercises

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/prds/create-workout-with-exercises/prd.md`, `docs/specs/workout-exercise-sets/spec.md`
(entity/migration layer this builds on), `docs/specs/workout-exercise-crud/spec.md` and
`docs/specs/exercise-set-crud/spec.md` (the one-at-a-time RPCs this feature adds a nested
alternative to), `docs/specs/clone-workout/spec.md` (nearest existing precedent for building a
`Workout`→`WorkoutExercise`→`ExerciseSet` tree in one transaction), `docs/specs/grpc-workout/spec.md`
(the CRUD pattern the new RPCs should follow), `docs/specs/workout-session-logging/spec.md` and
`docs/specs/exercise-progress/spec.md` (the `SetLog`/`WorkoutLog` model R12–R14 depend on)
Spec: not yet written (will be `docs/specs/create-workout-with-exercises/spec.md`)

## 1. Summary

Overall risk: **Medium**. No new tables, columns, or migrations are needed — `Workout` →
`WorkoutExercise` → `ExerciseSet` already models everything the PRD asks for
(`docs/specs/workout-exercise-sets/spec.md`), and `WorkoutService.cloneWorkout` already proves the
"build the whole tree in memory, save once" pattern the create side needs
(`src/main/java/com/vertice/api/plan/workout/WorkoutService.java:64-100`). The real risk is on the
edit side: `set_logs.exercise_set_id` has no `ON DELETE CASCADE`
(`src/main/resources/db/migration/V18__create_workout_logs_and_set_logs_tables.sql:21`), so a
bulk-replace (R11) that deletes an `ExerciseSet` a client has logged against without first checking
for that log will not "refuse the update" the way R12 asks — it will either fail with a raw,
unmapped foreign-key violation, or (via JPA's `orphanRemoval = true`) silently attempt a delete that
the database rejects. The R12/R13 refusal has to be an explicit application-level check that runs
*before* any delete is issued, with a new repository query and a new domain exception, not a
side-effect of the existing cascade. Separately, the PRD's own default-strategy rule (R6) directly
contradicts the existing single-set-create validation, which rejects an unset strategy outright
(`ExerciseSetController.java:70-76`) — the nested path cannot reuse that validation as-is.

| Id | Severity | One line |
|---|---|---|
| F2 | Blocker | R12's refusal must run before any delete; the DB has no cascade to fall back on and will surface a raw constraint violation instead of the required per-entity message |
| F1 | High | R6 (default strategy when omitted) is the opposite of the existing single-set-create rule that rejects `SET_STRATEGY_UNSPECIFIED`; the nested path needs its own validation, not reuse of `ExerciseSetController`/`ExerciseSetMapper` |
| F4 | High | PRD doesn't say whether bulk-create/replace are new RPCs or extensions of `CreateWorkout`/`WorkoutExercise` update; this is a real proto-shape decision with compatibility consequences |

## 2. PRD coverage map

| Rule | Lands on | Notes |
|---|---|---|
| R1 (create with no exercises) | Existing — `WorkoutService.createWorkout` (`plan/workout/WorkoutService.java:37-43`) | Already the only path; unaffected. |
| R2 (create with exercise list) | New surface — `plan/workout` (proto + service) | See F4, F5. |
| R3 (nested sets per exercise) | New surface — `plan/workout` | See F5. |
| R4 (order = list position) | New surface — `plan/workout` | Existing `WorkoutExerciseRequest.order` is an explicit required int (`workout-exercise-crud/spec.md` §3, `@Min(1)`); the nested path derives it instead, a different rule for the same field name. |
| R5 (set number = list position) | New surface — `plan/workout` | Same relationship to `exercise-set-crud/spec.md`'s explicit `set_number`. |
| R6 (default strategy STRAIGHT) | New surface, **conflicts with existing rule** | See F1 (High). |
| R7 (same exercise twice) | Existing — already satisfied | No uniqueness constraint on `(workout_id, exercise_id)` in `workout_exercises` (`V10__create_workout_exercises_table.sql`); nothing to change. |
| R8 (max 20 exercises) | New surface — validation | See F5, F10. |
| R9 (max 10 sets/exercise) | New surface — validation | Same. |
| R10 (reject whole submission on any invalid entry) | New surface — service | See F10. |
| R11 (bulk-replace existing workout) | New surface — `plan/workout` | See F2, F3, F8, F9. |
| R12 (refuse if removing logged data) | New surface — **Blocker** | See F2, F3. |
| R13 (name the blocking exercise/set) | New surface | See F3. |
| R14 (opened-but-empty session doesn't block) | Existing — already satisfied by the data model | `WorkoutSessionService.getOrStartWorkoutLog`/`startWorkoutLog` (`plan/session/WorkoutSessionService.java:46-52,123-136`) create only a `WorkoutLog` row; a `SetLog` row is created only by `recordSetLog` (`:54-71`). A blocking check keyed on `SetLog` existence per `ExerciseSet` naturally satisfies R14 with no extra logic. |
| R15 (one-at-a-time flows unchanged) | Existing — already satisfied | `WorkoutExerciseController`/`ExerciseSetController` RPCs are untouched by this PRD; see Options for how the new RPCs stay additive. |
| R16 (client experience unchanged) | Existing — already satisfied | No client-facing proto, entity, or read path (`WorkoutSessionService`) changes are implied by anything in this PRD. |
| E1 (empty list = empty workout) | R1 | Trivial once R2's list is optional. |
| E2 (21 exercises rejected) | R8, R10 | |
| E3 (nonexistent catalog exercise) | R10 | Needs a batch existence check — see F10. |
| E4 (11 sets rejected) | R9, R10 | |
| E5 (duplicate exercise allowed) | R7 | |
| E6 (exercise with zero sets) | R3 | |
| E7 (replace, nothing logged) | R11 | |
| E8 (replace blocked by logged set) | R12, R13 | |
| E9 (opened-but-empty session doesn't block) | R14 | |
| E10 (concurrent bulk edits, last write wins) | — (Out of scope: "Conflict detection") | Matches existing app-wide behavior — no `@Version`/`@Lock` anywhere (`grep -rn "@Version\|@Lock" src/main/java` → no results). Nothing to build; see F9. |
| E11 (single-exercise add still works) | R15 | |

## 3. Current state

`Workout` → `WorkoutExercise` → `ExerciseSet` is fully modeled and has full one-at-a-time gRPC CRUD
(`workout-exercise-crud`, `exercise-set-crud`); nothing in this PRD requires touching the entities,
repositories, or migrations. `Workout#workoutExercises` and `WorkoutExercise#exerciseSets` are both
`cascade = CascadeType.ALL, orphanRemoval = true`
(`plan/workout/Workout.java:48`, `plan/workout/WorkoutExercise.java:53`), which is exactly the
mechanism `WorkoutService.cloneWorkout` already uses to build and persist a whole tree in one
`save()` call — the closest existing precedent for R2/R3's "build the whole day in one step."

The session-logging side is separate: `WorkoutLog` (one per client, workout, week —
`plan/session/WorkoutLog.java`) is the "session," and `SetLog` (one per `WorkoutLog` ×
`ExerciseSet`, unique on that pair — `V18` migration) is the "actual recorded performance data" R12
refers to. `SetLogRepository` today only supports point lookups
(`findByWorkoutLogId`, `findByWorkoutLogIdAndExerciseSetId`) and one specialized query for progress
tracking (`findCompletedSetLogsForClientAndExercise`) — nothing that answers "which of these
`ExerciseSet` ids have any `SetLog` at all," which is what R12's check needs.

No spec in this codebase has previously accepted a gap specific to bulk delete-with-guard; the
nearest related accepted gap is the systemic one from `training-plan-fields/spec.md` §0: "no
authorization enforcement added... any authenticated caller may do anything." This feature inherits
that gap unchanged (see F6) rather than closing or worsening it in a new way.

## 4. Findings by dimension

### 1. PRD fit

**F1 — High.** R6 ("a set entry that does not specify a strategy defaults to a plain working set")
is the *opposite* of the existing single-set-create rule.

- Where: `ExerciseSetController.java:70-76` (`requireStrategy` throws
  `ConstraintViolationException` when `strategy == SET_STRATEGY_UNSPECIFIED`);
  `ExerciseSetMapper.java` (`@ValueMapping(source = "SET_STRATEGY_UNSPECIFIED", target =
  MappingConstants.NULL)`, whose Javadoc explicitly says the controller rejects unset before the
  mapper ever sees it). Relates to R6.
- Why it matters: if the nested-set path reuses `ExerciseSetController`'s validation or
  `ExerciseSetMapper` unchanged, every set entry that omits `strategy` (the common case R6 exists
  for) gets rejected instead of defaulted, breaking R6/E6 outright.
- Recommendation: the nested path needs its own mapping step that treats
  `SET_STRATEGY_UNSPECIFIED` as `STRAIGHT` (the enum's "plain working set" per
  `plan/workout/SetStrategy.java`), separate from the existing controller's reject-on-unset
  behavior. Don't extend `requireStrategy`/the existing `@ValueMapping` to serve both meanings.

**F4 — High.** The PRD is silent on whether "create a workout with a nested exercise list" is a new
RPC or an extension of the existing `CreateWorkout`, and whether "bulk-replace" is a new RPC or an
extension of `WorkoutExercise` update.

- Where: `src/main/proto/vertice/plan/v1/workout.proto` (`WorkoutCreateRequest` has no
  repeated field today); `workout_exercise.proto` (`UpdateWorkoutExerciseRequest` touches one row).
  Relates to R2, R3, R11.
- Why it matters: this is a real proto-surface decision, not an implementation detail — it decides
  whether existing `CreateWorkout`/`UpdateWorkoutExercise` callers see new optional fields
  (backward compatible either way, since proto3 additions are additive) or a wholly new RPC
  appears. It also decides which aggregate's controller/service owns the new logic.
- Recommendation: see Options (§5).

### 2. Data model and migrations

Nothing found requiring a migration. Checked: all five entities in the hierarchy already carry
every field R1–R14 reference (order, set number, strategy, reps, weight, etc. — all present since
`workout-exercise-sets`/`workout-exercise-crud`/`exercise-set-crud`); `V21` is still the highest
migration (`ls src/main/resources/db/migration | sort -V | tail -1`); `ddl-auto=validate` means any
entity/migration drift would already be failing CI, and none of the PRD's rules ask for a new
column. No index concerns: the caps (R8/R9) bound the new write to ≤200 rows per call, well under
anything that needs a new index, and reads are all by existing FK-indexed-by-convention lookups
(`workout_id`, `workout_exercise_id`).

### 3. API contract and backward compatibility

**F5 — Medium.** Whatever RPC shape §5/F4 settles on, the nested exercise/set entries need new
proto messages distinct from `WorkoutExerciseCreateRequest`/`ExerciseSetCreateRequest`.

- Where: `workout_exercise.proto`, `exercise_set.proto` — both `*CreateRequest` messages carry a
  parent FK (`workout_id`, `workout_exercise_id`) that doesn't exist yet for an exercise/set nested
  inside a not-yet-created workout in the same call. Relates to R2, R3, R8, R9.
- Why it matters: the existing messages can't be reused unmodified for the nested case; using them
  as-is would force the client to invent placeholder parent ids.
- Recommendation: new messages (e.g. a nested exercise-entry message embedding a `repeated`
  nested set-entry message), each field-for-field matching the persisted shape minus the parent FK
  and the position field (R4/R5 auto-number). Both are purely additive proto changes regardless of
  where they attach (new RPC or new field on an existing request) — no existing field is renamed,
  renumbered, or reinterpreted, so no compatibility break for `vertice-bff`. Confirmed no proto file
  uses `reserved` yet (`grep -rn reserved src/main/proto` → no results), consistent with this being
  a pure addition, not a removal.

Nothing else found. Checked: no existing RPC's request/response shape needs to change for any rule
in this PRD; the client-facing read paths (`WorkoutSessionService`, `plan/exercise`) are untouched
per R16.

### 4. Security and privacy

**F6 — Medium.** No new authorization gap, but this feature raises the blast radius of the existing
one.

- Where: `grpc/GrpcSecurityConfig` (flat "any authenticated caller" — no role/scope/ownership
  check anywhere; confirmed `grep -rn "SecurityContext\|Authentication\|Principal" src/main/java
  --include=*.java | grep -v config/` returns nothing outside config). Relates to R2, R3, R11 (all
  new write surface).
- Why it matters: today a guessed/enumerated `training_plan_id` or `workout_id` lets any
  authenticated caller create or delete *one* row belonging to another trainer. This feature lets
  the same caller create or delete up to 200 rows (20×10) in a single call, or wipe an entire
  workout's authored content via bulk-replace — same gap, materially larger single-call impact.
- Recommendation: not this feature's job to close (matches `training-plan-fields/spec.md` §0's
  precedent of documenting rather than fixing); the spec's `## 0. Scope decisions` should state
  explicitly that it inherits this gap unchanged, given the larger blast radius, rather than being
  silent about it.

Nothing else found. Checked: no new free-text field beyond what `notes` already is (`VARCHAR(255)`,
unchanged); no PII/health-adjacent field is newly exposed (weight/reps already cross the wire via
`ExerciseSetResponse`); `ResourceNotFoundException` will echo ids for the new "exercise not found"
/"blocking set" checks the same way it already does elsewhere — consistent with the existing
baseline, not a new enumeration surface.

### 5. Data integrity and consistency

**F2 — Blocker.** R12's refusal has to be an explicit pre-check, not a byproduct of the existing
delete cascade.

- Where: `V18__create_workout_logs_and_set_logs_tables.sql:21` — `fk_set_logs_exercise_set`
  has no `ON DELETE CASCADE` (Postgres default is `NO ACTION`). `Workout#workoutExercises` and
  `WorkoutExercise#exerciseSets` are `cascade = CascadeType.ALL, orphanRemoval = true`
  (`Workout.java:48`, `WorkoutExercise.java:53`) — JPA-side only, no DB-level cascade (matches the
  dimension-2 baseline: only `V21` uses `ON DELETE`/index at all). Relates to R11, R12, R13.
- Why it matters: if bulk-replace is implemented as "diff the submitted list against the current
  tree, let `orphanRemoval` delete what's missing," any `ExerciseSet` slated for removal that still
  has a `SetLog` row will fail at the database with a raw FK violation the moment Hibernate flushes
  the delete — not the "tell the trainer which exercise/set is blocking" response R13 requires, and
  not necessarily inside a clean rollback boundary the caller can act on (surfaces as `UNKNOWN`
  per the dimension-7 baseline, since no exception type today maps a raw
  `DataIntegrityViolationException` to a meaningful status).
- Recommendation: before persisting anything, run a query for existing `SetLog` rows against the
  `ExerciseSet`/`WorkoutExercise` ids the new list would remove; if any exist, throw a new domain
  exception (F3) and make zero writes. Only after that check passes should the diff/delete/insert
  actually run.

**F3 — Medium** (resolve together with F2). No repository method or exception type exists for this
check today.

- Where: `SetLogRepository.java` has `findByWorkoutLogId`,
  `findByWorkoutLogIdAndExerciseSetId`, `findCompletedSetLogsForClientAndExercise` — none answer
  "which of these `ExerciseSet` ids have any `SetLog`." `common/exception/` has
  `ResourceNotFoundException`, `DuplicateEmailException`, `DuplicateCpfException` — none fit
  "refused because removing this row would drop recorded data." Relates to R12, R13.
- Why it matters: without a purpose-built query, the check is at best an N+1 loop (one
  `findByWorkoutLogIdAndExerciseSetId`-shaped call per set the replacement removes); without a
  purpose-built exception, `GrpcExceptionAdvice` has nothing to map this refusal to besides the
  generic `ConstraintViolationException`→`INVALID_ARGUMENT` path already used for hand-written
  precondition messages (dimension-7 baseline) — which is semantically about malformed input, not
  a conflict with existing state.
- Recommendation: one query (e.g. "find `SetLog`s whose `exerciseSet.workoutExercise.workout.id =
  :workoutId` and `exerciseSet.id` in the removed-id set", following the existing chained-predicate
  `@Query` style `SetLogRepository`'s own Javadoc cites for the same reason — `exercise-progress/spec.md`
  §2). One new exception (e.g. `WorkoutExerciseHasRecordedDataException`, carrying which
  exercise/set id blocked it for R13's message), added to both `GrpcExceptionAdvice` and
  `GlobalExceptionHandler` per CLAUDE.md's exception-mapping convention, mapped to
  `Status.FAILED_PRECONDITION` (closer semantically than `INVALID_ARGUMENT` — the request is
  well-formed, it's the current state that blocks it).

**F9 — Medium.** No existing pattern combines "read current state, diff, conditionally refuse, else
write" in one transaction; the closest precedents are narrower.

- Where: `WorkoutSessionService.recordSetLog` (`:54-71`) is a single upsert-by-natural-key, and
  `WorkoutService.cloneWorkout` (`:64-100`) is build-then-save-once with no read-then-decide step.
  Neither combines a blocking read with a conditional multi-row write. No `@Version`/`@Lock`
  anywhere (`grep -rn "@Version\|@Lock" src/main/java` → no results); services are class-level
  `@Transactional` with default isolation. Relates to R11, R12; E10.
- Why it matters: between the R12 check and the actual delete/insert, another concurrent call
  (a second bulk-replace, or a client's `recordSetLog`) could change what "has recorded data"
  means. The PRD explicitly puts conflict detection out of scope (E10, "last write wins, no
  conflict detection"), so this is not a new gap to close — but the spec should say so explicitly
  rather than have the check-then-act window read as an oversight.
- Recommendation: state in `## 0. Scope decisions` that the R12 check races the same way every
  other read-then-write in this app does today, consistent with E10, and that this is accepted.

**F8 — Medium.** Bulk-replace needs to load a workout's existing exercise/set tree to diff and to
run the R12 check; nothing in this codebase currently fetches a nested association eagerly.

- Where: no `@EntityGraph`/`JOIN FETCH` anywhere (`grep -rn "EntityGraph\|JOIN FETCH"
  src/main/java` → no results); all associations are `FetchType.LAZY`
  (`Workout.workoutExercises`, `WorkoutExercise.exerciseSets`). `WorkoutService.cloneWorkout`
  already walks `source.getWorkoutExercises()` → `.getExerciseSets()` the plain lazy way. Relates
  to R11.
- Why it matters: at the PRD's own caps (≤20 exercises × ≤10 sets ⇒ ≤200 rows) a lazy walk is an
  N+1 of up to ~21 queries, not a correctness problem, but it's the first time this codebase would
  do it for a *write* path that also needs the result to make a refuse/proceed decision — worth a
  batch fetch rather than perpetuating the pattern into a second place.
- Recommendation: fetch the workout's current tree with one batched query (`JOIN FETCH` or a
  derived query returning `WorkoutExercise` with sets) rather than repeating
  `cloneWorkout`'s lazy-walk style, since this path is a write-path decision input, not a
  read-only response like clone's.

Nothing else found on cascades beyond F2/F8: `WorkoutExercise`→`ExerciseSet` deletion via
`orphanRemoval` for entries genuinely being removed (and confirmed to have no `SetLog`) works exactly
as `cloneWorkout`'s sibling deletion paths already rely on elsewhere in this codebase.

### 6. Performance and scalability

Nothing found beyond F8. Checked: R8/R9's caps (20 exercises × 10 sets) bound every new write to
≤200 rows, well inside what a single `@Transactional` save already handles today via
`cloneWorkout`; no pagination concern since none of the new RPCs list anything new; no aggregation
added.

### 7. Error handling

Covered by F1 (wrong-direction validation reuse) and F2/F3 (missing refusal path/exception type).
Nothing additional found: `GrpcExceptionAdvice`'s four existing mappings
(`ResourceNotFoundException`→`NOT_FOUND`, `DuplicateEmailException`/`DuplicateCpfException`→
`ALREADY_EXISTS`, `ConstraintViolationException`→`INVALID_ARGUMENT`) already cover "exercise id
doesn't exist" (E3, reuse `ResourceNotFoundException`) and the cap violations (E2/E4, reuse
`ConstraintViolationException` via Bean Validation) without needing new exception types — only R12's
refusal needs one (F3).

**F10 — Medium.** R10 ("whole submission rejected" on any invalid entry) is satisfiable by
validating everything before the transaction's single `save()`, matching the existing
build-then-save-once pattern (`cloneWorkout`) — but the caps/existence checks need to run as batch
checks, not per-entry fail-fast, for the "which one" to be reportable.

- Where: `ExerciseRepository` has no batch existence helper beyond inherited
  `findAllById(Iterable<Long>)` (`ExerciseRepository.java` — empty interface, only
  `JpaRepository`'s defaults). Relates to R8, R9, R10, E2, E3, E4.
- Why it matters: checking each of up to 20 exercise ids with individual `findById` calls works but
  is 20 round trips where one `findAllById` suffices, and the PRD doesn't require every invalid
  entry to be named — just that the whole thing is rejected — so this is an efficiency/clarity
  choice, not a correctness one.
- Recommendation: batch-validate (`findAllById` then diff the requested id set against what came
  back) rather than looping `findById`; decide in the spec whether the rejection message names the
  offending entry's position/id (more useful to the trainer) or stays generic like the existing
  Bean-Validation-record pattern's messages already are.

### 8. Logging

**F12 — Low.** No application code logs anything today (`grep -rln
"Slf4j\|LoggerFactory\|log\.\(info\|warn\|error\|debug\)" src/main/java` → no results outside
framework logging). This feature's R12 refusal — an explicit business rejection with a reason — is
exactly the kind of event dimension 8 flags as worth logging once a feature adds the first log
line. Not required by the PRD; optional improvement, not blocking.

### 9. Metrics and observability

Nothing found requiring action. Checked: no custom `MeterRegistry`/`@Timed`/`@Observed` exists
anywhere (`grep -rn "MeterRegistry\|@Timed\|@Observed" src/main/java` → no results); this feature
doesn't add a background job or external dependency that would need a new health signal. Optional:
a count of R12 refusals would be a reasonable first metric if/when this codebase adds metrics at
all, but that's a codebase-wide gap this feature doesn't need to be the one to close.

### 10. Testing

Covered in detail in §6 below. Nothing found beyond what's already flagged: this codebase has no
repository-level test slice today (dimension-10 baseline), and R12's correctness depends on a real
three-hop association (`ExerciseSet`→`WorkoutExercise`→`Workout` and `SetLog`→`WorkoutLog`) that a
Mockito service test can stub but not prove end-to-end — see F11 in §6.

### 11. Architecture fit and maintainability

Nothing found beyond F4/F5 (§1/§3). The feature belongs entirely inside `plan/workout` — no new
aggregate needed; `plan/session` (`SetLogRepository`) is read from, the same kind of
cross-aggregate reach `WorkoutFeedbackService` already does against `WorkoutLogRepository`
(explicitly called out as acceptable in dimensions.md §11), not a new coupling shape.

### 12. Operability and rollout

Nothing found. Checked: no migration, so no startup-ordering concern; whatever RPC shape §5
chooses is additive (new RPC, or new optional field with proto3's zero-value default), so the
current `vertice-bff` can keep working unmodified until it adopts the new call; no new
configuration or environment variable; nothing to roll back beyond the app version itself since no
schema changes ship with this feature.

## 5. Options

**A. Extend existing RPCs** — `WorkoutCreateRequest` gains an optional `repeated` nested-exercise
field; `UpdateWorkoutExerciseRequest`'s workout-level counterpart (or a new
`ReplaceWorkoutExercises` RPC) handles bulk-replace.
- Resolves: keeps one `CreateWorkout` entry point for both the empty and nested case (R1/R2 read as
  one rule with an optional field, matching how the PRD frames them). Minimal new proto surface.
- Triggers: `WorkoutController`/`WorkoutService` now own exercise/set creation logic that
  conceptually belongs to `WorkoutExerciseService`/`ExerciseSetService`'s domain — some
  cross-aggregate reach into `ExerciseRepository`/`WorkoutExerciseRepository` from `WorkoutService`
  either way.
- Cost: Low proto churn, moderate service-layer reorganization.

**B. New dedicated RPCs** — e.g. `CreateWorkoutWithExercises` alongside the existing
`CreateWorkout`, and `ReplaceWorkoutExercises` alongside the existing per-item
`UpdateWorkoutExercise`.
- Resolves: keeps every existing RPC's contract and behavior completely untouched (cleanest reading
  of R15 — "existing... actions continue to work exactly as they do today"); the new RPCs can have
  their own request/response shape without touching `WorkoutCreateRequest`.
- Triggers: two more RPCs to test end-to-end (dimension 10: two more controller-test ports), some
  duplication between `WorkoutCreateRequest` and the new nested-create request's top-level fields
  (name, training_plan_id, day_of_week).
- Cost: Slightly more proto/service surface, but the clearest match to CLAUDE.md's existing
  one-`*Controller`-per-aggregate, one-`*Service`-per-aggregate pattern and the safest for
  compatibility (nothing about an existing RPC's behavior changes at all).

**Recommendation: B.** R15's own wording is closest to "add, don't change" — a brand-new RPC keeps
that promise unambiguous, avoids growing `WorkoutCreateRequest` with fields most callers won't use,
and sidesteps any question of whether `CreateWorkout`'s existing validation
(`WorkoutController.java:44-50`) needs to become conditional on whether the nested list is present.
The cost (a second controller-test port, some field duplication) is small at this codebase's size.

## 6. Testing strategy

- **R1/E1** (empty or omitted list): `*ServiceTest` — a nested-create call with no exercises
  produces the same `Workout` row `WorkoutService.createWorkout` produces today.
- **R2/R3/E5/E6** (exercises with/without sets, duplicate exercise, one exercise with zero sets):
  `*ServiceTest` — assert the full graph persisted (workout + N exercises + per-exercise set
  counts) via the mocked repository's captured `save()` argument, the same assertion style
  `WorkoutServiceTest` presumably already uses for `cloneWorkout`.
- **R4/R5** (auto-numbered order/set_number): `*ServiceTest` — submit entries out of any explicit
  order field and assert persisted `order`/`setNumber` match submission position, 1-based.
- **R6** (default strategy): `*ServiceTest` — a set entry with `strategy` omitted persists as
  `STRAIGHT`; a `*ControllerTest` case round-tripping the same over the real gRPC channel to prove
  the new mapping (not the existing `requireStrategy`) is what's wired in.
- **R7/E5**: `*ServiceTest` — same `exercise_id` twice in one submission persists as two
  `WorkoutExercise` rows.
- **R8/R9/E2/E4** (caps): `*ControllerTest` — 21 exercises / an exercise with 11 sets both return
  `INVALID_ARGUMENT` and create nothing (assert no repository interaction, or assert via a
  follow-up `GetWorkout` that nothing landed, matching how `WorkoutControllerTest` presumably
  checks other reject-and-create-nothing cases today).
- **R10/E3** (invalid entry rejects the whole submission): `*ControllerTest` — one entry pointing
  at a nonexistent exercise id, others valid; assert the whole call fails and a follow-up `List`
  shows nothing was created.
- **R11/E7** (clean replace): `*ServiceTest` — submit a new list for a workout with an existing
  tree and no `SetLog`s; assert old rows gone, new rows present in submitted order.
- **R12/R13/E8** (blocked replace) — this is the rule dimension 10 flags as needing real DB
  behavior, not just a mocked repository: a `*ControllerTest` scenario that (1) creates a workout
  with an exercise/set via the real gRPC channel, (2) records a real `SetLog` against it via
  `WorkoutSessionService`'s existing RPCs (or a direct repository seed within the test), (3)
  attempts a bulk-replace that would drop that set, and (4) asserts the specific
  `FAILED_PRECONDITION`-style status, that the message names the blocking exercise/set, and that a
  follow-up read shows nothing changed. A `*ServiceTest` variant can additionally mock the new
  blocking-check repository method to prove the service-layer refusal path in isolation.
- **R14/E9** (opened-but-empty session doesn't block): `*ControllerTest` — start a `WorkoutLog` via
  `GetOrStartWorkoutLog` but never call `RecordSetLog`, then bulk-replace; assert it succeeds.
- **E10**: no test needed — PRD explicitly puts conflict detection out of scope; don't add
  optimistic-locking assertions that would imply behavior not being built.
- **R15/E11**: no new test needed — existing `WorkoutExerciseControllerTest`/`ExerciseSetControllerTest`
  suites already cover the one-at-a-time flows and are untouched by this feature (confirms nothing
  regresses, doesn't need new scenarios).
- **R16**: no test needed — no client-facing surface changes.

**F11 — Low.** No repository-level test slice exists in this codebase today (dimension-10
baseline: one `*ServiceTest` + one `*ControllerTest` per aggregate, 236 `@Test` methods total,
`grep -rc "@Test" src/test/java | awk -F: '{s+=$2} END {print s}'`). R12 is the rule most likely to
have a subtle bug if the new query doesn't materialize correctly across the
`ExerciseSet`→`WorkoutExercise`→`Workout` and `SetLog`→`WorkoutLog` associations; recommend the
`*ControllerTest` scenario above (real gRPC channel, real Postgres) rather than trusting a
`*ServiceTest` with a mocked repository alone to prove this rule, consistent with how
`*ControllerTest`s already exist specifically to exercise real wiring end-to-end (CLAUDE.md
testing-pattern section).

## 7. Rollout

No special handling: additive change only. No migration ships with this feature (§4, dimension 2),
so there's no startup-ordering concern. Whichever RPC shape Options §5 lands on is purely additive
at the proto level (new RPC, or a new optional field on an existing message) — `vertice-bff` keeps
working against the current API unmodified until it's updated to call the new surface, and rolling
the API back to the previous version leaves no schema debt since nothing changed on disk.

## 8. Effort and risk

Size: **M**. One aggregate (`plan/workout`), no migration, new proto surface (one or two RPCs per
Options §5), a genuinely new transactional pattern (read-current-state → check-then-refuse →
conditional multi-row write) this codebase hasn't done before, plus a new repository query and a
new exception type wired into both exception advices. Not S because it's more than "one more CRUD
endpoint" — the R12 check is real new logic, not boilerplate. Not L/XL because it's one aggregate,
no new tables, no breaking API change, and the caps (R8/R9) keep the data volume trivial.

Risks that could push this toward L: if the bulk-replace diff/delete/insert sequence is implemented
without the F2 pre-check (i.e., "just let `orphanRemoval` handle it and catch the exception"), the
resulting bug — an unmapped `UNKNOWN` status instead of R13's specific message, or worse, a
transaction that partially applies before the FK violation surfaces — would need to be caught in
review or testing rather than by the type system, since nothing in the entity mapping prevents it.
No dependency on `vertice-bff`/`vertice-web` changing at the same time (Rollout, §7).

## 9. Questions and assumptions

None. The PRD (§8 "Open questions: None") and this investigation together answer everything needed
to write the spec; the one genuine design choice left open — new RPCs vs. extending existing ones
(F4) — is a spec-author decision with a stated recommendation (Options §5), not something only the
product owner can resolve.

## 10. Inputs to the spec

`## 0. Scope decisions` must contain:

- [ ] How the nested path validates/defaults `SetStrategy` for an omitted value, and confirmation
  it does *not* reuse `ExerciseSetController.requireStrategy`/`ExerciseSetMapper.mapStrategy` as-is
  (F1).
- [ ] Exactly when the R12 blocking-check runs relative to the bulk-replace's delete/insert
  sequence, guaranteeing zero writes occur before it passes (F2).
- [ ] The new repository query shape for finding `SetLog`s under the ids a replacement would
  remove, and the new exception type + gRPC status (recommend `FAILED_PRECONDITION`) + message
  contract for R13 (F3).
- [ ] New RPCs vs. extending `CreateWorkout`/`WorkoutExercise` update, per Options §5
  (recommendation: new RPCs) (F4).
- [ ] The new nested-input proto message shapes (exercise entry embedding a repeated set entry),
  distinct from the existing `*CreateRequest` messages (F5).
- [ ] Explicit statement that no ownership/authorization check is added, inheriting the existing
  systemic gap, despite the larger per-call blast radius this feature introduces (F6).
- [ ] How bulk-replace loads the workout's current exercise/set tree (batched fetch vs. the lazy
  walk `cloneWorkout` uses) (F8).
- [ ] Confirmation that the check-then-act window for R12 races the same way every other
  read-then-write in this app does (no locking), consistent with E10's accepted last-write-wins
  (F9).
- [ ] Whether the rejection message for R10 (invalid entry / cap violation) names the specific
  offending entry or stays generic (F10).
