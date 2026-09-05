# Spec: Create Workout With Exercises

Status: Implemented
Owner: hebertpdl@gmail.com
Related: `docs/prds/create-workout-with-exercises/prd.md`,
`docs/assessments/create-workout-with-exercises/assessment.md`,
`docs/specs/workout-exercise-sets/spec.md` (entity/migration layer this builds on),
`docs/specs/workout-exercise-crud/spec.md` and `docs/specs/exercise-set-crud/spec.md` (the
one-at-a-time RPCs this feature adds a nested alternative to, and leaves unchanged per R15),
`docs/specs/clone-workout/spec.md` (nearest existing precedent for building a
`Workout`→`WorkoutExercise`→`ExerciseSet` tree in one transaction), `docs/specs/grpc-workout/spec.md`
(the CRUD pattern the new RPCs follow), `docs/specs/workout-session-logging/spec.md` and
`docs/specs/exercise-progress/spec.md` (the `SetLog`/`WorkoutLog` model R12–R14 depend on)

## 0. Scope decisions

Every decision below was a genuine choice with more than one reasonable answer; each was put to
the product owner rather than assumed. The assessment's finding id is noted where one exists.

- **New dedicated RPCs, not extensions of existing ones (F4).** `CreateWorkoutWithExercises` and
  `ReplaceWorkoutExercises` are new RPCs on `WorkoutService`, alongside — not replacing or
  reshaping — `CreateWorkout` and `UpdateWorkoutExercise`. Chosen over adding an optional
  `repeated` field to `WorkoutCreateRequest`/`UpdateWorkoutExerciseRequest` because R15 ("existing
  actions continue to work exactly as they do today") reads cleanest when no existing request
  message changes shape at all, and it matches CLAUDE.md's one-`*Controller`/one-`*Service`-per-
  aggregate pattern without blurring `WorkoutService`'s existing contract. Cost accepted: a second
  controller-test port, some field duplication between `WorkoutCreateRequest` and
  `CreateWorkoutWithExercisesRequest` (`name`, `training_plan_id`, `day_of_week`).
- **`SetStrategy` defaulting is a nested-path-only concern (F1).** R6 ("a set entry that omits
  strategy defaults to a plain working set") is the literal opposite of the existing
  single-set-create rule (`ExerciseSetController#requireStrategy` rejects
  `SET_STRATEGY_UNSPECIFIED`). Rather than making that shared validation/mapping conditional on
  caller, the nested path gets its own mapping step that treats `SET_STRATEGY_UNSPECIFIED` as
  `STRAIGHT`; `ExerciseSetController`/`ExerciseSetMapper` are untouched. Recorded in `prd.md` §7's
  Decisions table so the PRD itself states the two rules coexist rather than one superseding the
  other.
- **No ownership/authorization check is added (F6).** This feature inherits the codebase-wide gap
  ("any authenticated caller may do anything," `grpc/GrpcSecurityConfig`) unchanged, same as every
  other RPC — despite this feature raising the single-call blast radius (up to 200 rows created,
  or an entire workout's authored content wiped via bulk-replace, per one call from any
  authenticated caller). This is a deliberate choice to keep the gap consistent codebase-wide
  rather than closing it piecemeal per feature; it is not a silent omission.
- **Bulk-replace's current tree is loaded with one batched fetch, not a lazy walk (F8).**
  `WorkoutService#loadCurrentTree` (or equivalent) uses a single `JOIN FETCH`/`@EntityGraph`-backed
  query returning the workout's `WorkoutExercise` rows with their `ExerciseSet` collections
  populated, rather than repeating `cloneWorkout`'s plain lazy-association walk. This is a
  deliberate departure from the one existing precedent: unlike `cloneWorkout` (a read-only-shaped
  copy), this path uses the loaded tree as a write-path decision input (§4), so avoiding an N+1 is
  worth the one-off batched query.
- **R12 refusal maps to `FAILED_PRECONDITION`, via a new exception type (F3).** A new
  `WorkoutExerciseHasRecordedDataException` (in `common/exception/`) is added to both
  `GrpcExceptionAdvice` (→ `Status.FAILED_PRECONDITION`) and `GlobalExceptionHandler` (→
  `HttpStatus.PRECONDITION_FAILED`), per CLAUDE.md's two-parallel-handlers convention. Chosen over
  reusing `ConstraintViolationException`/`INVALID_ARGUMENT` because the request itself is
  well-formed — it's the current state (existing recorded data) that blocks it, a different
  semantic than malformed input.
- **The R12 check-then-act window is accepted as racy, not locked (F9).** Between reading current
  `SetLog` state and issuing the delete/insert, a concurrent write (another bulk-replace, or a
  client's `RecordSetLog`) could change what "has recorded data" means. No `@Version`/optimistic
  locking or pessimistic lock is added for this path — it races the same way every other
  read-then-write in this app does today (no locking exists anywhere in the codebase), consistent
  with E10's explicit "last write wins, no conflict detection."
- **Rejection messages for R10 (cap violations, nonexistent catalog exercise) are generic, not
  entry-specific (F10).** `INVALID_ARGUMENT`/`ConstraintViolationException` messages state what
  rule was violated (e.g. "exercises: must not exceed 20 entries", "exercise_id: one or more
  referenced exercises do not exist") without naming which list position or id triggered it. This
  is a deliberate simplification over `ResourceNotFoundException`'s usual per-id message — the
  whole submission is rejected either way (R10), so the specific offender isn't surfaced.
- **Bulk-replace (R11) treats the entire current exercise/set tree as removed, not diffed by
  content.** Submitted `WorkoutExerciseEntry`/`ExerciseSetEntry` messages carry no reference to
  existing row ids (same shape as create), so the server cannot match a resubmitted entry back to
  a specific existing row. Given the PRD's own "full replace, not diff/merge" decision (§7),
  bulk-replace is implemented as: every existing `WorkoutExercise`/`ExerciseSet` row under the
  workout is removed and an entirely new tree is built from the submission (new ids throughout,
  same pattern `cloneWorkout` already uses to build-then-save-once). Consequently, the R12 check is
  "does this workout have *any* recorded `SetLog` anywhere under it" — not "does the specific
  content being dropped have one" — because there is no way to determine that a resubmitted entry
  "is" an existing row rather than a new one that happens to look the same. This means once a
  client has recorded anything against a workout, every future bulk-replace of that workout is
  refused; the trainer falls back to the existing one-at-a-time `WorkoutExercise`/`ExerciseSet`
  RPCs (R15, still available) for further edits. No content-matching/merge heuristic is introduced
  to narrow this — the PRD's "simpler mental model" framing for full replace and the lack of any
  id-carrying field in the submission both point away from building one.

## 1. Goal

Two new RPCs on `WorkoutService`:

- `CreateWorkoutWithExercises` — create a `Workout` together with its full `WorkoutExercise`/
  `ExerciseSet` tree in one call (R1–R10).
- `ReplaceWorkoutExercises` — replace an existing `Workout`'s entire exercise/set tree in one call,
  refusing the whole operation if it would drop any client-recorded data (R11–R14).

Both are purely additive: `CreateWorkout`, `UpdateWorkout`, `WorkoutExerciseController`, and
`ExerciseSetController` are untouched (R15, R16).

## 2. Contract (`workout.proto`)

```proto
service WorkoutService {
  // ... existing RPCs unchanged ...
  rpc CreateWorkoutWithExercises(CreateWorkoutWithExercisesRequest) returns (WorkoutResponse);
  rpc ReplaceWorkoutExercises(ReplaceWorkoutExercisesRequest) returns (WorkoutResponse);
}

message CreateWorkoutWithExercisesRequest {
  string name = 1;
  int64 training_plan_id = 2;
  DayOfWeek day_of_week = 3;
  repeated WorkoutExerciseEntry exercises = 4;
}

message ReplaceWorkoutExercisesRequest {
  int64 workout_id = 1;
  repeated WorkoutExerciseEntry exercises = 2;
}

message WorkoutExerciseEntry {
  int64 exercise_id = 1;
  int32 rest_seconds_between_sets = 2;
  string notes = 3;
  repeated ExerciseSetEntry sets = 4;
}

message ExerciseSetEntry {
  int32 reps = 1;
  int32 duration_seconds = 2;
  string weight = 3;           // decimal-as-string, same convention as ExerciseSetCreateRequest
  string load_percentage = 4;  // decimal-as-string, same convention as ExerciseSetCreateRequest
  SetStrategy strategy = 5;    // SET_STRATEGY_UNSPECIFIED defaults to STRAIGHT — see §0, §3
  int32 rest_seconds = 6;
  string notes = 7;
}
```

Both RPCs return `WorkoutResponse` (§0/F5 — no existing `*CreateRequest`/`*Request` message is
reused unmodified; these are new, purpose-built nested messages). `WorkoutExerciseEntry`/
`ExerciseSetEntry` deliberately omit:
- `id` — always a create.
- `workout_id`/`exercise_id` parent FK for sets, and `workout_id` for exercises — implied by
  nesting.
- `order`/`set_number` — derived from list position (R4, R5).

`ExerciseSetEntry.exercise_id` doesn't exist as a field (it's `WorkoutExerciseEntry.exercise_id`
that ties an entry to a catalog `Exercise`); each `WorkoutExerciseEntry` embeds its own
`repeated ExerciseSetEntry sets`.

## 3. Validation rules

Both RPCs validate the whole request before any write (R10) — matching `cloneWorkout`'s
build-then-save-once shape, just with a validation pass first.

**`CreateWorkoutWithExercises`:**
- `name`: `@NotBlank`, same as `CreateWorkout`.
- `day_of_week`: `DAY_OF_WEEK_UNSPECIFIED` rejected, same `requireDayOfWeek` check
  `CreateWorkout`/`UpdateWorkout` already use.
- `training_plan_id`: must resolve or `NOT_FOUND` ("TrainingPlan"), same as `CreateWorkout`.
- `exercises`: at most 20 entries (R8, E2) — `INVALID_ARGUMENT`, generic message (§0/F10).
- Every `exercises[].exercise_id` must resolve to an existing catalog `Exercise` (E3) — checked as
  one batch query (`ExerciseRepository#findAllById` over the full requested id set, diffed against
  what came back) rather than one `findById` per entry; any miss rejects the whole request with a
  generic `INVALID_ARGUMENT` message (§0/F10), not `ResourceNotFoundException` (which would name
  the specific id — deliberately not used here per §0).
- Duplicate `exercise_id` values across entries are allowed (R7, E5) — no uniqueness check.
- `exercises[].sets`: at most 10 entries per exercise (R9, E4) — `INVALID_ARGUMENT`, generic
  message. Zero sets is allowed (E6).
- Each `sets[].reps`/`duration_seconds`/`rest_seconds`: `@Min(0)`, same reasoning
  `exercise-set-crud/spec.md` §0 gives (zero doubles as "omitted").
- Each `sets[].weight`/`load_percentage`: parsed via `ProtoDecimals#stringToDecimal` (rejects
  malformed/negative; blank → unset), same as `ExerciseSetCreateRequest`.
- Each `sets[].strategy`: **not** rejected when `SET_STRATEGY_UNSPECIFIED` (§0/F1) — mapped to
  `STRAIGHT` instead, via new mapping logic distinct from `ExerciseSetMapper#mapStrategy`.
- R10: if any of the above fails for any entry, the entire request is rejected and nothing is
  persisted — no partial creation.

**`ReplaceWorkoutExercises`:**
- `workout_id`: must resolve or `NOT_FOUND` ("Workout").
- `exercises`/`sets`: identical shape, caps, and rejection rules as
  `CreateWorkoutWithExercises` above (R8–R10, E2–E4, E6).
- No `training_plan_id`/`name`/`day_of_week` fields — those belong to the existing `Workout` and
  are untouched by this RPC (only its exercise/set tree changes).

## 4. Behavior

### `CreateWorkoutWithExercises`

1. Validate the full request (§3). Any failure: no writes, request rejected.
2. Resolve `training_plan_id` (`NOT_FOUND` if missing).
3. Build the whole `Workout` → `WorkoutExercise` → `ExerciseSet` graph in memory — one
   `WorkoutExercise` per `exercises[]` entry (`order` = 1-based list position, R4), one
   `ExerciseSet` per nested `sets[]` entry (`setNumber` = 1-based list position within that
   exercise, R5), `strategy` defaulted per §0/F1.
4. Save once. `Workout#workoutExercises`/`WorkoutExercise#exerciseSets`'s existing
   `cascade = CascadeType.ALL` (already relied on by `cloneWorkout`) persists the whole tree
   transitively.
5. Return the new `Workout` as `WorkoutResponse` (same shape `CreateWorkout`/`CloneWorkout`
   return — the client discovers the nested exercises/sets via the existing
   `ListWorkoutExercises`/`ListExerciseSets` RPCs, same as after a `CloneWorkout` call).

An empty or omitted `exercises` list produces the same result as today's `CreateWorkout` (R1, E1)
— this RPC is additive, not a replacement for `CreateWorkout`.

### `ReplaceWorkoutExercises`

1. Validate the full request (§3). Any failure: no writes, request rejected.
2. Resolve the target `Workout` (`NOT_FOUND` if missing).
3. Load the workout's current `WorkoutExercise`/`ExerciseSet` tree with one batched fetch (§0/F8)
   — not a lazy per-association walk.
4. **R12 check, before any delete or insert:** query whether any `SetLog` exists for any
   `ExerciseSet` currently under this workout (new `SetLogRepository` query — see §5). If at least
   one exists:
   - Throw `WorkoutExerciseHasRecordedDataException`, naming the specific blocking exercise/set
     (R13) — e.g. which `WorkoutExercise`'s `Exercise` and which `ExerciseSet`'s `setNumber`.
     (Note: this is the R13 message, distinct from §0/F10's generic R10 rejection — R13 explicitly
     requires naming the blocker.)
   - Zero writes occur — the check runs and fully resolves before anything is deleted or inserted,
     within the same `@Transactional` boundary as steps 5–6 so a failure here can't leave a partial
     state.
5. If the check passes: remove every existing `WorkoutExercise` under the workout (`orphanRemoval`
   cascades to their `ExerciseSet` rows — safe now, since step 4 already proved none of them have a
   `SetLog`).
6. Build and attach a brand-new `WorkoutExercise`/`ExerciseSet` tree from the submitted list,
   exactly as `CreateWorkoutWithExercises` step 3 does, and save.
7. Return the updated `Workout` as `WorkoutResponse`.

E9 (an opened-but-empty `WorkoutLog` with no `SetLog` rows) does not block step 4 — the check is
keyed on `SetLog` existence, not `WorkoutLog` existence, so a started-but-empty session passes
through untouched, matching how `WorkoutSessionService#getOrStartWorkoutLog`/`recordSetLog`
already separate "session opened" from "something recorded."

E10 (concurrent conflicting bulk edits) is not addressed — §0/F9.

## 5. New repository query and exception

- `SetLogRepository`: new query, e.g.
  ```java
  @Query("SELECT sl FROM SetLog sl "
          + "WHERE sl.exerciseSet.workoutExercise.workout.id = :workoutId")
  List<SetLog> findByWorkoutId(@Param("workoutId") Long workoutId);
  ```
  following the same chained-predicate `@Query` style `findCompletedSetLogsForClientAndExercise`
  already uses (dimension-2 baseline in the assessment). A non-empty result means R12 blocks the
  replace; the first row's `exerciseSet`/`workoutExercise` supplies the R13 "which exercise/set"
  detail.
- New exception `WorkoutExerciseHasRecordedDataException` (`common/exception/`), carrying the
  blocking `Exercise` name/id and `ExerciseSet` set number so the message can name it (R13).
  Mapped in `GrpcExceptionAdvice` → `Status.FAILED_PRECONDITION` and `GlobalExceptionHandler` →
  `HttpStatus.PRECONDITION_FAILED` (§0/F3).

## 6. Mapping

New mapper (or new methods added to a nested-specific mapper — not `ExerciseSetMapper`) covering
`WorkoutExerciseEntry` → `WorkoutExercise` and `ExerciseSetEntry` → `ExerciseSet`:
- `order`/`setNumber` set by the service from list index (1-based), not mapped from the proto
  message (neither field exists on the entry messages).
- `weight`/`loadPercentage` via the existing `ProtoDecimals` `@Named` methods.
- `notes` via the existing `ProtoStrings#nullToEmpty`/blank-to-null convention.
- `strategy`: `SET_STRATEGY_UNSPECIFIED` → `STRAIGHT` (§0/F1) — a new `@ValueMapping` distinct from
  `ExerciseSetMapper#mapStrategy`'s `SET_STRATEGY_UNSPECIFIED` → `null`.

## 7. Testing strategy

- **R1/E1**: `WorkoutServiceTest` — `CreateWorkoutWithExercises` with no/empty `exercises`
  produces the same `Workout` row `createWorkout` produces today.
- **R2/R3/E5/E6**: `WorkoutServiceTest` — full graph persisted (workout + N exercises + per-exercise
  set counts) via the mocked repository's captured `save()` argument; duplicate `exercise_id`
  across two entries persists as two `WorkoutExercise` rows.
- **R4/R5**: `WorkoutServiceTest` — persisted `order`/`setNumber` match submission position,
  1-based, regardless of any other field values.
- **R6**: `WorkoutServiceTest` — an entry with `strategy` omitted persists as `STRAIGHT`; plus a
  `WorkoutControllerTest` case round-tripping the same over the real gRPC channel to prove the new
  mapping (not `ExerciseSetMapper#mapStrategy`) is what's wired into this path.
- **R7/E5**: covered above.
- **R8/R9/E2/E4**: `WorkoutControllerTest` — 21 exercises / an exercise with 11 sets both return
  `INVALID_ARGUMENT`; assert no repository write occurred.
- **R10/E3**: `WorkoutControllerTest` — one entry pointing at a nonexistent exercise id, others
  valid; assert the whole call fails `INVALID_ARGUMENT` and nothing was created.
- **R11/E7**: `WorkoutServiceTest` — `ReplaceWorkoutExercises` on a workout with an existing tree
  and no `SetLog`s; old rows gone, new rows present in submitted order.
- **R12/R13/E8**: `WorkoutControllerTest` against the real gRPC channel and real Postgres (per the
  assessment's F11 — this rule needs real association behavior, not a mocked repository): create a
  workout with an exercise/set, record a real `SetLog` against it via the existing session RPCs (or
  a direct repository seed), attempt `ReplaceWorkoutExercises`, and assert `FAILED_PRECONDITION`,
  that the message names the blocking exercise/set, and that a follow-up read shows nothing
  changed. A `WorkoutServiceTest` variant mocks `SetLogRepository#findByWorkoutId` to prove the
  service-layer refusal path in isolation.
- **R14/E9**: `WorkoutControllerTest` — start a `WorkoutLog` via `GetOrStartWorkoutLog` but never
  call `RecordSetLog`, then `ReplaceWorkoutExercises`; assert it succeeds.
- **E10**: no test — out of scope (§0/F9), don't imply behavior not being built.
- **R15/E11**: no new test — existing `WorkoutExerciseControllerTest`/`ExerciseSetControllerTest`
  suites already cover the one-at-a-time flows and are untouched by this feature.
- **R16**: no test — no client-facing surface changes.

## 8. Out of scope

- Nesting one level higher (`TrainingPlan` with `Workout`s, and their exercises, all in one call)
  — not asked for (PRD §6).
- Any change to `CloneWorkout`, or to how a client views/interacts with a workout.
- Conflict detection for concurrent edits (E10) — matches existing app-wide behavior.
- Ownership/authorization enforcement — inherited gap, unchanged (§0/F6).
- A content-matching/merge heuristic for bulk-replace that would narrow R12's blast radius below
  "any recorded data anywhere in the workout" (§0) — not built; not asked for.
