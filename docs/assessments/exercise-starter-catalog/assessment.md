# Technical assessment: Starter exercise catalog

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/prds/exercise-starter-catalog/prd.md`, `docs/specs/grpc-exercise-catalog/spec.md`,
`docs/specs/exercise-video-url/spec.md`, `docs/specs/workout-exercise-crud/spec.md`,
`docs/specs/clone-workout/spec.md`, `docs/specs/workout-session-logging/spec.md` (touched),
`docs/specs/training-plan-fields/spec.md` (touched), `docs/specs/user-unification/spec.md`
(touched), `docs/domain-model.md`
Spec: `docs/specs/exercise-starter-catalog/spec.md` (written after this assessment; its §0 resolves
the Blocker and High findings below)

## 1. Summary

Overall risk: **High**. The feature reshapes `Exercise`'s data model (single enum muscle group →
many-to-many against a new `muscle_groups` table, plus ownership), changes an existing proto
field's shape (first breaking proto change in this codebase), adds a one-way-door migration that
deletes exercises/workout entries/logged sets, and — the biggest piece — requires this codebase's
*first* per-caller identity/authorization enforcement, for which the token doesn't even cross the
`vertice-bff` → `vertice-api` boundary today. Recommended approach: build a single reusable
caller-identity resolver in `grpc/` (not exercise-specific), fix the BFF→API auth gap as part of
the same effort, model muscle groups as two new tables seeded by a plain Flyway migration (matches
the owner's own stated constraint), and treat the R51–R59 pre-starter cleanup as one reviewed,
one-off SQL migration rather than new job infrastructure.

| Id | Severity | One line |
|---|---|---|
| F12 | Blocker | No caller identity crosses `vertice-bff` → `vertice-api` today (insecure gRPC channel, no metadata) — R14–R25/R32/R36's ownership boundary can't be enforced, or trivially spoofed, until this is fixed |
| F1 | High | `Exercise.muscleGroup` is a single 7-value enum column; PRD needs a many-to-many 14-group model |
| F4 | High | No cascade (DB or JPA) exists from `Exercise` down through `workout_exercises`/`exercise_sets`/`set_logs` for R51–R54's removal |
| F5 | High | R57–R59 (keep + per-trainer copy + refile) has no existing duplication/reassignment logic to build on |
| F9 | High | `ExerciseRequest.muscle_group` must become repeated — first breaking proto field change, no prior `reserved` use |
| F10 | High | `ListExercises` returns everything unfiltered today; ownership-scoping it is a behavior break, not just additive fields |
| F15 | High | R34 (delete refused while in use) has zero guard; today it fails as a raw unmapped FK violation |
| F23 | High | R47–R50's join/ordering rules need the first repository-level test in this codebase; a Mockito service test can't prove them |
| F26 | High | The caller-identity resolver is cross-cutting; must be a shared `grpc/` component, not exercise-specific |
| F27 | High | `MuscleGroup` enum→table migration must land in the exact same deploy as the code (`ddl-auto=validate`) |
| F28 | High | No Flyway undo migrations; the R51–R59 deletion is a one-way door |
| F29 | High | `vertice-bff`'s gRPC client to vertice-api uses `grpc.credentials.createInsecure()` with no auth metadata — same root cause as F12, but it's a two-repo fix |

## 2. PRD coverage map

| Rule/Edge | Lands on | Notes |
|---|---|---|
| R1 | new: `plan/exercise` (`ListExercises` query + seed migration) | |
| R2 | existing: `exercises.name NOT NULL` (`V4__create_exercises_table.sql`) | schema already enforces it; content supplied by new seed data |
| R3 | new: join table (F1/F6/F7) | |
| R4 | new: migration seed content | no code enforcement; relies on R26/R28 to keep it that way |
| R5 | new: migration seed content | same as R4 |
| R6 | new: migration seed content | product/content judgment, not code |
| R7 | new: migration seed content | same |
| R8 | new: `muscle_groups` table seed | |
| R9 | new: `muscle_groups` table seed | content |
| R10 | new: migration seed content | "nothing found" re: enforcing a minimum — there is none to build |
| R11 | new: join table (F1/F6/F7) | |
| R12 | new: migration seed data (~199 rows, PRD §10) | |
| R13 | new: `ListExercises` query, ownership-scoped | today: `ExerciseService.java:20-25`, unfiltered `findAll()` |
| R14 | new: ownership column + `ListExercises`/`GetExercise` filtering | |
| R15 | new: same as R14 | |
| R16 | new: `GetExercise` ownership check (F12/F13) | |
| R17 | new: `WorkoutExerciseService.createWorkoutExercise` ownership check | today: `WorkoutExerciseService.java:36-40`, no check |
| R18 | new: `WorkoutExerciseService` ownership check via workout → training plan → trainer | |
| R19 | new: `WorkoutService.cloneWorkout` ownership check | today: `WorkoutService.java:83-86`, no check |
| R20 | existing: client-facing session responses reference `Exercise` regardless of ownership | `workout_session.proto:17-33`, `SetLogRepository` — verify new checks don't accidentally scope this path too |
| R21 | new: role check on `ListExercises` | |
| R22 | new: role + membership check on `GetExercise` for CLIENT | |
| R23 | new: role check on `CreateExercise` | |
| R24 | new: role check on `UpdateExercise`/`DeleteExercise` | |
| R25 | out of scope | PRD §6: no platform-team-facing surface exists in the product; nothing to build |
| R26 | new: `UpdateExercise` starter-immutability check | |
| R27 | new: `DeleteExercise` starter-immutability check | |
| R28 | downstream (`vertice-web-react`/`vertice-bff`) | app-level omission of the action, not vertice-api |
| R29 | existing (`ExerciseMapper.updateEntityFromRequest`) + new ownership scoping | |
| R30 | existing (fields already updatable) + new ownership scoping | |
| R31 | new: validation, mirrors R40 | |
| R32 | new: same ownership check as R16 | |
| R33 | existing (F2) | no denormalized exercise name anywhere in session responses |
| R34 | new: deletion guard (F15) | |
| R35 | existing base delete (`ExerciseService.java:43-46`) + new guard from R34 | |
| R36 | new: same ownership check as R32 | |
| R37 | new, by omission | no "promote to starter" RPC is ever exposed |
| R38 | new, by omission | no `MuscleGroup` CRUD RPC is ever exposed to trainers |
| R39 | new: `CreateExercise` multi-group support (F9) | |
| R40 | new: validation (F19) | |
| R41 | existing: description already optional/updatable | |
| R42 | existing: `ExerciseController.java:80` `@Pattern` | |
| R43 | new: proto/data model (F9, F1) | |
| R44 | existing/nothing found | no uniqueness constraint on `name` anywhere |
| R45 | new: `ListExercisesRequest` filter field + query | |
| R46 | new: `ListExercisesRequest` search field + query | |
| R47 | new: query joins (F6/F7) | |
| R48 | new: ordering in query (own-first) | |
| R49 | new: ordinal column + `ORDER BY` (F6) | |
| R50 | new: unordered secondary-group appearance | simplifies the query — no ordinal needed for this case |
| R51 | new: one-off migration (F4) | |
| R52 | new: cascading delete script (F4) | |
| R53 | new: cascading delete script (F4) | |
| R54 | existing, by construction | `workout_feedback`/`workout_logs` key only to `workout_log_id` (`V18`, `V19`) — untouched as long as F4's script stops at `workout_exercises`/`exercise_sets`/`set_logs` |
| R55 | new (one-off migration, not recurring logic) | guaranteed once R34 blocks future deletions |
| R56 | new: manual migration step (F5) | platform-team-curated keep-list |
| R57 | new: migration sets ownership column on kept rows (F5) | |
| R58 | new: migration duplication routine (F5) | |
| R59 | new: migration step, manual group reassignment (F5) | |
| E1 | new (R26/R28) | |
| E2 | new (R27/R28) | |
| E3 | existing/nothing found (R44/R14) | |
| E4 | existing/nothing found (R14/R44) | |
| E5 | new (R34, F15) | |
| E6 | existing (F2, R33) | |
| E7 | existing (R20) | |
| E8 | new (R11/R47) | |
| E9 | out of scope (PRD §6) — covered by existing R42 path | |
| E10 | new (R8) | |
| E11 | new (R10) — "nothing found" re: padding enforcement | |
| E12 | new (R52/R53/R54, F4) | |
| E13 | new (R40, F19) | |
| E14 | existing/nothing found (R15) — ties to F12 | |
| E15 | new (R46) | |
| E16 | new (R21/R22) | |
| E17 | new (R23/R24) | |
| E18 | new (R16/R17) — core adversarial case behind F12 | |
| E19 | new (R18) | |
| E20 | new (R19, F12) | |
| E21 | new (R32/R36) | |
| E22 | new (R31/R40, F19) | |
| E23 | new (R56/R57/R58, F5) | |

## 3. Current state

`plan/exercise/` is a small, complete-looking CRUD aggregate today: `Exercise` (`id`, `name`,
`description`, `videoUrl`, a single `muscleGroup` enum), `ExerciseRepository` (plain
`JpaRepository`, no derived queries), `ExerciseService` (`listExercises` = unfiltered `findAll()`;
`create`/`update`/`delete` with no ownership or usage checks), `ExerciseController` (manual Bean
Validation via `GrpcRequestValidator`, plus a hand-rolled zero-value check for the proto enum).
`exercise.proto` has no filter fields on `ListExercisesRequest` and a single non-repeated
`MuscleGroup muscle_group` field on `ExerciseRequest`/`ExerciseResponse`. `MuscleGroup` itself is
a 7-value enum (`CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CARDIO`) added by `V20` with a
name-substring backfill heuristic explicitly documented as throwaway local data.

`Exercise` is referenced from `WorkoutExercise` (`workout_exercises.exercise_id`, no `ON DELETE`),
which is referenced from `ExerciseSet` (`exercise_sets.workout_exercise_id`, no `ON DELETE`),
which is referenced from `SetLog` (`set_logs.exercise_set_id`, no `ON DELETE`). None of this chain
has JPA-side cascade either — `Exercise` has no `@OneToMany` back-reference at all. `WorkoutLog`
and `WorkoutFeedback` key only to `workout_id`/`client_id` and `workout_log_id` respectively —
they never reference an exercise, a workout-exercise, or a set directly (`V18`, `V19`).

Accepted gaps inherited from earlier specs, quoted verbatim:
- `docs/specs/training-plan-fields/spec.md` §0: *"No authorization enforcement added: ... this
  repo's gRPC layer has no per-caller identity/role resolution at all ... `client_id` here is a
  caller-supplied request field ... not derived from a JWT."*
- `docs/specs/user-unification/spec.md` §3.5: *"Role-based authorization is explicitly out of
  scope ... `role` on `User` is a data classification field only ... it does not gate any
  endpoint."*
- `docs/specs/workout-session-logging/spec.md` §0: *"No caller-identity/role enforcement — same
  accepted gap as every other spec in this plan."*

This PRD is the first one that cannot simply repeat that gap — R14–R25/R32/R36 are built directly
on top of it. See F12.

`vertice-bff` already has the client-side half of an ownership pattern this feature will need to
mirror: `src/lib/ownership.ts` (`assertOwnsPlan`, `assertOwnsWorkout`,
`assertOwnsWorkoutExercise`), driven by an `AuthUser` (`id`, `role`) the BFF itself decodes from a
JWT it mints (`src/lib/jwt.ts`). But `src/grpc/clients.ts` wires every gRPC client to vertice-api
with `grpc.credentials.createInsecure()` and attaches no token or metadata — nothing carries that
already-resolved identity across the BFF → API boundary today.

## 4. Findings by dimension

Each finding has an id, a severity, and the four parts What, Where, Why it matters and
Recommendation. Where a dimension (or part of one) turned up nothing, it reads "Nothing found.
Checked: …". Four such results keep the ids they had in the first draft (F14, F18, F22, F24),
because the spec cites them. They are not findings and carry no severity.

### PRD fit

**F1** [High]
- *What:* `Exercise.muscleGroup` is a single, non-repeatable enum column, but the PRD requires an
  exercise to carry one *or more* of 14 groups. This is not an incremental change to the existing
  field; it replaces it.
- *Where:* `Exercise.java:30-32`; `V20`; R3, R8, R11, R43.
- *Why it matters:* no amount of widening the enum gives an exercise two groups, so R11 (every
  group it trains) and R47 (narrowing finds an exercise through any of its groups) cannot hold
  on the current column.
- *Recommendation:* model groups as two new tables (`muscle_groups`, and a join table between it
  and `exercises`), per the owner's own stated constraint (PRD §9).

**F2** [Info]
- *What:* R33 (a rename appears in every workout, including completed ones) is already satisfied
  with zero code change.
- *Where:* `workout_session.proto:17-33` (`WorkoutLogResponse`/`SetLogResponse` carry only ids,
  never a denormalized exercise name); R33, E6.
- *Why it matters:* the spec author need not build or test anything for R33 beyond keeping it
  that way.
- *Recommendation:* none; the spec should not add a denormalized name anywhere.

**F3** [Info]
- *What:* R44/E3/E4 (duplicate names allowed, including matching the starter set) already hold.
- *Where:* `V4__create_exercises_table.sql` (`exercises.name` has no `UNIQUE` constraint); nothing
  else in the codebase derives identity from an exercise's name; R44, E3, E4.
- *Why it matters:* a uniqueness constraint added "for tidiness" would break R44.
- *Recommendation:* none; the spec should not add a name constraint.

### Data model and migrations

**F4** [High]
- *What:* no cascade path, DB or JPA, exists from `Exercise` through `workout_exercises` →
  `exercise_sets` → `set_logs` for the removal R51–R54 describe.
- *Where:* `fk_workout_exercises_exercise` (`V10`), `fk_exercise_sets_workout_exercise` (`V11`),
  `fk_set_logs_exercise_set` (`V18`), none with `ON DELETE CASCADE` (only `V21` uses `ON DELETE`
  anywhere); `Exercise` has no `@OneToMany` back-reference for JPA cascade to run through; R51–R54.
- *Why it matters:* a plain `DELETE FROM exercises` for a row still referenced by
  `workout_exercises` fails on the FK constraint today, and a cascade added carelessly could
  reach past the rows R54 says must survive.
- *Recommendation:* the removal migration deletes in explicit dependency order (`set_logs` →
  `exercise_sets` → `workout_exercises` → `exercises`) and stops exactly there:
  `workout_feedback`/`workout_logs` key only to `workout_log_id` (`V18`, `V19`), so R54 (session
  and its feedback untouched) holds automatically as long as the script never reaches past
  `workout_exercises`.

**F5** [High]
- *What:* R57–R59 (an exercise kept under R56 becomes private to the trainer whose workouts use
  it; when more than one trainer's workouts use it, each gets their own copy with their own
  workout entries repointed to it) has no existing building block.
- *Where:* new surface; nothing in the codebase duplicates an `Exercise` row or repoints a
  `WorkoutExercise.exercise` reference; R56–R59, E23.
- *Why it matters:* without a copy-and-repoint step, a kept exercise used by two trainers would
  either stay shared (breaking R14/R15) or lose one trainer's history (breaking R56).
- *Recommendation:* a one-off migration step, per kept exercise and per trainer using it, that
  (a) creates one exercise row per distinct trainer found via `workout_exercises → workouts →
  training_plans → trainer_id`, (b) repoints only that trainer's `workout_exercises` rows to
  their copy, (c) sets the new ownership column, (d) assigns the launch groups the platform team
  specifies (R59).

**F6** [Medium]
- *What:* R48/R49 (a trainer's own exercises first, then starter-set exercises in the exact
  order given in PRD §10) needs a stored ordinal to reproduce deterministically.
- *Where:* new surface; `WorkoutExercise.order` (`workout_exercises.exercise_order`) is a
  different concept (placement within one workout), and nothing else exists; R48, R49.
- *Why it matters:* without an ordinal, "most commonly prescribed first" can only be
  approximated by name or id, and ids stop matching PRD order the first time a row is re-seeded.
- *Recommendation:* an ordinal column on the join table (position within the group the exercise
  is primarily filed under), populated 1..N directly from each PRD §10 table's row order at
  migration time.

**F7** [Medium]
- *What:* the join table needs to distinguish the group an exercise is *filed under* (PRD §10's
  per-section listing, where R49's ordinal applies) from every *other* group it also carries
  (R50, explicitly unordered).
- *Where:* new surface (the join table of F1); R49, R50.
- *Why it matters:* with only an ordinal and no flag, an exercise listed under Costas and also
  carrying Lombar would take its Costas position into the Lombar list, which R50 says is
  unspecified and PRD §10 does not define.
- *Recommendation:* a boolean flag column on the join row (e.g. "primary"), set once at seed
  time.

**F8** [Info]
- *What:* `V20`'s muscle-group backfill (a name-substring heuristic, documented in its own
  comment as "throwaway local data") is superseded by R51 for any pre-starter row not kept.
- *Where:* `V20`; R51, R56, R59.
- *Why it matters:* the spec does not need to reconcile old heuristic values, except for the
  handful of R56-kept rows, which the platform team refiles by hand (R59).
- *Recommendation:* none; drop the old column rather than migrate its values.

### API contract and backward compatibility

**F9** [High]
- *What:* `ExerciseRequest.muscle_group` (field 4, a single `MuscleGroup`) must become
  multi-valued to satisfy R43. No `.proto` file in this codebase uses `reserved` yet, so this is
  the first field this codebase actually breaks.
- *Where:* `exercise.proto` (`ExerciseRequest` field 4, `ExerciseResponse` field 5); `grep -rn
  reserved src/main/proto` → no hits; R39, R43.
- *Why it matters:* retyping or renumbering field 4 in place would let an old client's bytes be
  read as the new type, and would set the convention for every later breaking change by accident.
- *Recommendation:* add a new repeated field for the group ids under a new field number, and
  mark the old field number and name `reserved` rather than retyping or renumbering it, so this
  sets the convention deliberately.

**F10** [High]
- *What:* `ListExercisesRequest` is an empty message today, and `ListExercises` returns every row
  unfiltered. Once ownership-scoped visibility ships, the *same* RPC called with the *same*
  request shape returns a caller-scoped subset instead of everything.
- *Where:* `ExerciseService.java:20-25` (plain `findAll()`); `exercise.proto`
  `ListExercisesRequest`; R13–R15.
- *Why it matters:* dimension 3 counts "a list that starts filtering" as a compatibility break
  for the BFF even with the same proto, independent of any new fields.
- *Recommendation:* confirm with the chained BFF assessment whether anything depends on today's
  unfiltered list, and treat this as a deploy-ordering concern (§7), not just an
  additive-fields one.

**F11** [Info]
- *What:* `GetExercise`/`CreateExercise`/`UpdateExercise`/`DeleteExercise` gain new refusal
  paths but no wire-shape changes beyond F9.
- *Where:* `exercise.proto` `ExerciseService`; R16, R23, R24, R26, R27, R32, R34, R36.
- *Why it matters:* no compatibility concern beyond F9 and F10; the new refusals are status
  codes, which F13 decides.
- *Recommendation:* none.

### Security and privacy

**F12** [Blocker]
- *What:* no per-caller identity is available anywhere in this stack today for vertice-api to
  enforce R14–R25/R32/R36.
- *Where:* three pieces of evidence, all verified this session:
  1. No business code in vertice-api reads the authenticated principal: `grep -rln
     "SecurityContext\|Authentication\|Principal" src/main/java` outside `grpc/` returns
     nothing; `GrpcSecurityConfig`'s own doc comment says "any authenticated caller may do
     anything, no role/scope differentiation", and every prior spec's `## 0. Scope decisions`
     repeats this as an accepted gap (§3).
  2. `vertice-bff` already has this pattern one layer up: `vertice-bff/src/lib/ownership.ts`
     derives an `AuthUser` (`id`, `role`) from a JWT it mints itself (`vertice-bff/src/lib/jwt.ts`)
     and enforces per-caller ownership before calling vertice-api for plans, workouts and
     workout-exercises.
  3. But `vertice-bff/src/grpc/clients.ts` wires every gRPC client to vertice-api with
     `grpc.credentials.createInsecure()` and attaches no token or metadata, so nothing carries
     the BFF's resolved identity across to vertice-api today.
  PRD rules: R14–R25, R32, R36, E14, E16–E21.
- *Why it matters:* R16–R18/R32/R36 and E18/E21 are framed adversarially ("a trainer who
  *learns* another trainer's private exercise identifier fetches it *directly*"), which describes
  a caller reaching vertice-api's gRPC surface directly, bypassing `ownership.ts`. Enforcement
  therefore has to live in vertice-api itself, or those rules do not hold. A caller-supplied
  `trainer_id` would be trivially spoofed.
- *Recommendation:* build real per-caller identity resolution into vertice-api (decided this
  session with the owner, Q1), mirroring the shape `ownership.ts` established: (a) the chained
  `technical-assessment-bff` records how identity crosses the BFF → API boundary (e.g. the BFF
  forwards its own signed JWT as gRPC call metadata for vertice-api to validate), since it is a
  two-repo change; (b) build one reusable "current caller" resolver under `grpc/` (F26), not an
  inline check inside `ExerciseService`.

**F13** [Medium]
- *What:* the status code for a cross-trainer refusal is undecided.
- *Where:* `GrpcExceptionAdvice.java:24-27` (`ResourceNotFoundException` → `Status.NOT_FOUND`,
  echoing the id); the advice's own comment notes Spring gRPC's `SecurityGrpcExceptionHandler`
  already maps `AccessDeniedException` to `PERMISSION_DENIED`; R16–R18, R32, R36.
- *Why it matters:* the choice is whether "exists but isn't yours" is indistinguishable from
  "doesn't exist" (`NOT_FOUND` for both) or explicit (`PERMISSION_DENIED`). The PRD's "refused,
  not merely absent from their list" leans toward the caller knowing they were refused. Whichever
  is chosen sets the pattern for every future ownership check in this codebase.
- *Recommendation:* the spec decides explicitly; `PERMISSION_DENIED` for a real row the caller
  may not see, `NOT_FOUND` for a missing id, matches the PRD's wording.

**F14** — Nothing found for free-text bounds. Checked: the fields this feature touches. `name`
(`VARCHAR(255) NOT NULL`) and `description` (`VARCHAR(255)`) from `V4` and `video_url`
(`VARCHAR(500)`) from `V17` are already bounded, and `ExerciseController`'s existing `@Pattern`
(`ExerciseController.java:80`) already restricts `video_url` to blank-or-http(s). Nothing new is
needed for R41, R42 or R6.

### Data integrity and consistency

**F15** [High]
- *What:* R34 (delete refused while any workout uses the exercise) has zero enforcement today.
- *Where:* `ExerciseService.deleteExercise` (`ExerciseService.java:43-46`) deletes
  unconditionally; `GrpcExceptionAdvice` has no mapping for the FK violation; R34, R35, E5.
- *Why it matters:* deleting a referenced exercise fails at the database as a raw FK violation
  that falls through to `UNKNOWN`, not the described refusal R34 wants, and the BFF cannot tell
  it apart from a server fault.
- *Recommendation:* add an exception shaped like `WorkoutExerciseHasRecordedDataException` →
  `Status.FAILED_PRECONDITION` (`GrpcExceptionAdvice.java:37-40`, used for "can't replace,
  something depends on it"), checked via an `existsBy...` query on `WorkoutExerciseRepository`
  before the delete.

**F16** [Medium]
- *What:* no optimistic locking exists anywhere. This feature does not introduce the gap, but
  the R51–R59 migration is the first genuinely destructive bulk write in this codebase's history.
- *Where:* `grep -rn "@Version\|@Lock" src/main/java` → no hits; the new cleanup migration;
  R51–R59.
- *Why it matters:* if ordinary traffic touched the same rows while the cleanup ran, the last
  write would win silently, as everywhere else in this codebase.
- *Recommendation:* the spec may defer this with a reason. Running the cleanup as a Flyway
  migration at boot, before the application serves traffic, removes the concurrent writer; if
  the spec chooses another mechanism (Option B), it must decide how writes are held off during
  the run.

### Performance and scalability

**F17** [Medium]
- *What:* no RPC in this codebase paginates, and today's `ListExercises` is an unfiltered
  `findAll()`. R48/R49's ordering cannot be produced by sorting a fully loaded list in Java
  without the ordinal from F6.
- *Where:* `grep -rn "Pageable\|page_size\|page_token"` → no hits; `ExerciseService.java:20-25`;
  R45–R50; PRD §3 Flow 1.
- *Why it matters:* PRD Flow 1 makes this the "every workout-build" hot path. At ~200 starter
  rows plus a handful of private rows per trainer it is fine without pagination today, but an
  app-side filter would have to be replaced by a query later.
- *Recommendation:* build it as a real query from the start (join across exercise, join table
  and muscle group, ordered by own-first then the stored ordinal), so pagination can be added
  later without a rewrite. The spec may defer pagination itself with a reason.

**F18** — Nothing found for N+1 risk beyond the list query above. Checked:
`WorkoutExerciseService.createWorkoutExercise` (`WorkoutExerciseService.java:36-40`) and
`WorkoutService.cloneWorkout` (`WorkoutService.java:96`) already load `Exercise` via a simple
`findById` per reference and are unaffected by this feature.

### Error handling

**F19** [Medium]
- *What:* R40/R31/E13/E22 (create/update refused with zero groups) need an "empty list" check
  for the new multi-group field.
- *Where:* `ExerciseController#requireMuscleGroup` (`ExerciseController.java:72-76`) rejects the
  proto3 zero value for the single-enum field, the same way `WorkoutController#requireDayOfWeek`
  does; R31, R40, E13, E22.
- *Why it matters:* a repeated field has no zero value to reject, so the existing check stops
  protecting anything once F9 lands, and create and update could drift apart if each gets its
  own check.
- *Recommendation:* one "list is empty" check, reused for create (R40) and update (R31), with a
  message the BFF can show.

**F20** [Medium]
- *What:* new exception types for R26/R27 (starter-set immutability), the R14–R25 ownership
  family (F12/F13) and R34 (F15) must be added to **both** `GrpcExceptionAdvice` and
  `GlobalExceptionHandler`.
- *Where:* `grpc/GrpcExceptionAdvice.java`, `common/exception/GlobalExceptionHandler.java`;
  CLAUDE.md "Exception mapping"; R26, R27, R34.
- *Why it matters:* nothing enforces keeping the two in sync, so it is easy to add one and forget
  the other, and a missed gRPC mapping surfaces as `UNKNOWN`.
- *Recommendation:* the spec lists every new exception with its gRPC status and REST status side
  by side, and each gets a `GrpcExceptionMappingTest` scenario.

### Logging

**F21** [Info]
- *What:* no application code logs anything today.
- *Where:* `grep -rln "Slf4j\|LoggerFactory\|log\.\(info\|warn\|error\|debug\)" src/main/java` →
  no hits; R51–R54.
- *Why it matters:* the R51–R54 removal is the most consequential, irreversible write this
  codebase will have run. If it runs as anything other than plain SQL (§5), it is a reasonable
  place to set the first logging convention; as plain SQL, Flyway's history row is the record.
- *Recommendation:* none required; if the spec picks Option B (§5), decide the log format there.

### Metrics and observability

**F22** — Nothing found. Checked: no `MeterRegistry`, `@Timed` or `@Observed` anywhere,
consistent with the existing baseline. `ListExercises` becoming a hot, per-caller-filtered
endpoint (F17) is worth a latency or row-count signal eventually, but this feature does not need
to be the one that introduces metrics tooling.

### Testing

**F23** [High]
- *What:* this is the first feature whose correctness depends on real SQL ordering and joins
  across three tables (R47–R50), and no test flavor here can prove that.
- *Where:* this codebase has a Mockito `*ServiceTest` (mocked repository) and a wire-level
  `*ControllerTest`; `@DataJpaTest` → no hits anywhere in `src/test`; R47–R50, E8.
- *Why it matters:* a mocked repository returns whatever list the test hands it, so a wrong
  `ORDER BY` or join would pass every existing kind of test.
- *Recommendation:* add the first repository-level test, against real Postgres, proving R47–R50
  rather than trusting a mocked list.

**F24** — Nothing found beyond F23. Checked: `ExerciseControllerTest`/`ExerciseServiceTest` (15
and 7 `@Test` methods) are a reasonable template for the CRUD-shaped rules. Next free
`spring.grpc.server.port`: **19104** (19090–19103 confirmed in use across existing
`*ControllerTest` classes, one higher than this skill's recorded baseline of 19090–19102; noted
as baseline drift).

### Architecture fit and maintainability

**F25** [Medium]
- *What:* `MuscleGroup` moves from a Java enum to a JPA entity backed by a table (F1).
- *Where:* `plan/exercise/MuscleGroup.java`, `ExerciseMapper` (the `@ValueMapping` for the enum),
  `exercise.proto`; nothing outside `plan/exercise/` references `MuscleGroup` directly, and every
  other entity that touches an exercise (`WorkoutExercise`) only references `Exercise`; R8, R38.
- *Why it matters:* the change is contained, but it is a rename of a concept: keeping the name
  `MuscleGroup` for the new entity means every enum-based reference must go in the same change,
  or the build breaks halfway.
- *Recommendation:* the spec decides whether the entity reuses the name `MuscleGroup` (deleting
  the enum in the same PR) and lists the files that change, so the switch lands in one PR with
  the migration (F27).

**F26** [High]
- *What:* the caller-identity resolver F12 needs is inherently cross-cutting.
- *Where:* new surface under `grpc/`, this codebase's home for cross-cutting gRPC concerns
  (`GrpcRequestValidator`, `GrpcExceptionAdvice`, the `Proto*` helpers); R14–R25, R32, R36.
- *Why it matters:* every future spec adding ownership to trainer-owned data will need the same
  "who is calling, do they own this" shape; an inline check inside `ExerciseService` would be the
  first of many one-off copies.
- *Recommendation:* build it once under `grpc/`, not inside `ExerciseService`.

### Operability and rollout

**F27** [High]
- *What:* the `MuscleGroup` enum-to-table migration and the `Exercise.muscleGroup`
  column-to-join-table migration must land in the exact same deploy as the code.
- *Where:* `spring.jpa.hibernate.ddl-auto=validate` (`application.properties`); the new
  migration; R3, R8, R43.
- *Why it matters:* old code booting against the new schema (or the reverse) fails at startup,
  not at first use. There is no partial or rolling window here, unlike a normal additive column.
- *Recommendation:* ship the schema migration and the entity change in one PR, state in the
  spec that the PR is a same-deploy unit, and order later migrations after it by version.

**F28** [High]
- *What:* no Flyway undo migrations exist in this codebase (only forward `V*` files).
- *Where:* `src/main/resources/db/migration`; the R51–R59 cleanup (F4/F5).
- *Why it matters:* rolling the app back after the cleanup runs leaves the schema, and the
  already-deleted rows, ahead with no automatic path back. The owner confirmed this session that
  the affected rows are test data (Q2), which caps the practical risk.
- *Recommendation:* call it a one-way door explicitly in the spec's rollout and in the deploy
  runbook, and keep the destructive migration in its own PR so it is reviewed on its own.

**F29** [High]
- *What:* same root cause as F12, restated for rollout: `vertice-bff`'s gRPC client to
  vertice-api is wired insecure with zero auth metadata.
- *Where:* `vertice-bff/src/grpc/clients.ts`; R14–R25.
- *Why it matters:* whatever mechanism F12 settles on for identity to cross that boundary is a
  coordinated two-repo deploy, not something vertice-api can ship and validate alone. If
  vertice-api starts requiring identity before the BFF sends it, every BFF call fails.
- *Recommendation:* sequence it so the BFF's forwarding and vertice-api's verification ship
  first and are harmless alone (vertice-api accepts but does not yet require identity), and only
  then deploy the RPCs that require it; provision any shared credential identically on both sides.

### Effort, risk, and dependencies

**F30** [Info]
- *What:* the work splits into increments that each deploy on their own, which is what makes an
  XL feature (§8) shippable. The spec's delivery plan (`docs/specs/exercise-starter-catalog/spec.md`
  §10) takes this split: Increment 0 (identity verification and the resolver, two PRs, no
  behavior change); Increment 1 (six PRs: the muscle-group model, the cleanup migration, the
  seed, then authorization, the in-use guard and the list query), deployed api → bff → web in one
  window; Increment 2 (the R17–R19 workout-side guards, one PR).
- *Where:* spec §10; F12, F27, F28, F29; R17–R19, R51–R59.
- *Why it matters:* the order differs from §8's first suggestion ((a) → (c) → (b)). Identity
  goes first because it is harmless alone (F29), the cleanup must run after the groups table and
  before the seed (so every row it sees is pre-starter), and the RPCs that require identity can
  only deploy once the BFF forwards the token. The riskiest dependency is therefore on another
  repo: the BFF's Increment 0 must be deployed before Increment 1's authorization PR. Nine PRs
  across three repos also make the proto-sync step (the BFF's copy of `exercise.proto`) a
  recurring chance to drift.
- *Recommendation:* none beyond what the spec's delivery plan does; §8's risks still apply.

## 5. Options

Only one point in this feature has a real branch worth naming — how R51–R59's pre-starter
cleanup is executed.

**Option A — plain Flyway SQL migration** (e.g. a single reviewed `V22__...sql`), run
automatically at boot like every other schema change in this codebase. The R56 keep-list and R59
group reassignments are supplied as literal, reviewed SQL (a short list of ids), not built as
reusable tooling.
- Resolves: F4, F5, matches the owner's own stated constraint (PRD §9: "add this exercises in the
  database via a migration").
- Cost: low — no new infrastructure; Flyway's sequential, run-once model gives the ordering
  guarantee F4 needs for free.

**Option B — an app-triggered one-off script or admin RPC.**
- Resolves the same rules, with slightly more flexibility if the keep-list needs to be computed
  dynamically rather than hand-curated.
- Cost: meaningfully higher — this codebase has no precedent for any scheduled/one-off job
  infrastructure (dimension 9 baseline: none exists), for a one-time operation that doesn't need
  to be reusable.

**Recommendation: Option A.** It's what the owner already asked for, it's this codebase's only
existing migration mechanism, and R56's keep-list is a one-time platform-team judgment call, not a
recurring operation worth building tooling for.

## 6. Testing strategy

- **Service tests** (Mockito, per aggregate, this codebase's existing pattern): ownership refusals
  (R16–R18, R32, R36 — mock the resolved caller identity two ways, owner vs. not), starter-set
  immutability (R26, R27, E1, E2), delete-while-in-use (R34, E5) and delete-when-unused (R35),
  create/update validation (R39–R43, R31, R40, E13, E22), duplicate-name tolerance (R44, E3, E4).
- **Controller tests** (`@SpringBootTest`, real gRPC channel, `local` profile, next port 19104+):
  status-code mapping for every new refusal (F13's decision), CLIENT-role refusals (R21–R24,
  E16, E17), the zero-muscle-group validation error shape.
- **First repository/integration-level test (F23)**, against real Postgres, not mocked: proves
  R47–R50 — narrowing to a group returns every visible exercise carrying it (primary or
  secondary), own-exercises-first ordering, primary-group ordinal ordering matching PRD §10,
  and that a secondary-group appearance doesn't need a specific position.
- **`WorkoutExerciseService`/`WorkoutService` scenarios**: R17–R19, E18–E20 (cross-trainer add-to-
  workout and clone-workout refusals) — these sit in a different aggregate than `Exercise` itself
  and need their own test coverage, not just `ExerciseServiceTest`'s.
- **Migration verification** (F4/F5): a scenario proving that after the R51–R59 migration runs, a
  multi-trainer-used kept exercise produces one row per trainer, each trainer's `workout_exercises`
  point only at their own copy, and `workout_logs`/`workout_feedback` rows for the affected
  sessions are untouched (R54, E23) — likely a `@DataJpaTest`/integration-style check against a
  migrated schema rather than a Flyway-internal test, since this codebase has no precedent for
  testing migration content directly.

## 7. Rollout

- **Ordering**: the schema migration (muscle-group tables, ownership column, R51–R59 cleanup) and
  the code that depends on the new shape must deploy together — no partial window (F27).
- **Coexistence**: not clean. `ListExercises`/`ExerciseRequest`'s shape and behavior both change
  (F9, F10); anything currently calling the old shape breaks or gets silently different (scoped)
  results the moment the new code is live. Confirm with the chained BFF assessment whether
  anything live depends on today's shape before treating this as low-risk.
- **Rollback**: not possible past the R51–R59 migration — no Flyway undo (F28). The owner confirmed
  the affected rows are test data this session, which caps the blast radius, but this should still
  be called a one-way door in the runbook, not an "additive change" rollback story.
- **Cross-repo dependency (F12/F29)**: this feature cannot ship correctly in isolation. The
  BFF → API identity-forwarding mechanism has to land in the same window as vertice-api's
  ownership checks, or every trainer-owned-data call from the BFF starts failing (if vertice-api
  now requires identity it never receives) or the ownership checks are silently unenforceable (if
  vertice-api falls back to trusting a caller-supplied id). This is the single biggest rollout risk
  in the feature.

## 8. Effort and risk

**Size: XL.** This is simultaneously: (1) this codebase's first per-caller identity/authorization
layer, spanning two repos (F12/F26/F29); (2) a data model change to an existing, widely-referenced
entity (single enum → many-to-many, F1/F6/F7); (3) the first breaking proto field change (F9);
(4) a destructive, one-off multi-table migration with no existing cascade to build on (F4/F5);
(5) a new filtered/ordered list query where none existed (F10/F17); and (6) the first
repository-level test in the codebase (F23). Any one of these would be a normal-sized feature on
its own; here they land together because they all trace back to the same `Exercise` aggregate.

**Risks that could change the size:**
- The BFF→API identity mechanism (F12) is genuinely undecided — if it needs new
  infrastructure beyond "forward a JWT" (e.g. a new internal auth scheme), this grows further.
- R56–R59's keep-list is a manual platform-team review step outside the code; if it turns out to
  be larger or messier than "a few rows," F5's migration gets more involved.
- No dependency on other in-flight work was found in this session, but the chained BFF and web
  assessments may surface UI/route-level work large enough to justify shipping this in slices.

**Sequencing** (dimension 13 has the delivery-plan view, F30): the PRD gives no explicit
smaller-first-version signal, but a natural split exists:
(a) data model + starter-set seed + unauthenticated CRUD reshaping (F1, F6, F7, F9, F14, F19)
could ship and be exercised before (b) the ownership/identity layer (F12, F26, F29) and (c) the
R51–R59 cleanup migration (F4, F5) land — ship order (a) → (c) → (b), since (c) is safest run
before real ownership checks are live (less that can conflict with in-flight caller state) and
before the current already-shared-everything behavior changes.

## 9. Questions and assumptions

Two questions were asked and answered this session:

- **Q1 (answered): build real per-caller identity resolution into vertice-api, not a
  caller-supplied `trainer_id`.** Assumption for the spec: implement per F12/F26 — a shared
  resolver under `grpc/`, and coordinate the BFF→API identity-forwarding mechanism with the
  chained `technical-assessment-bff`.
- **Q2 (answered): all pre-starter `exercises`/`workout_exercises`/`exercise_sets`/`set_logs` rows
  are test data, safe to remove.** Assumption for the spec: R51's blanket removal is low
  operational risk; R56/R57 remain a rare, manually-curated exception, not something requiring a
  pre-migration data audit.

One open item remains, not blocking but worth the spec's explicit decision:

- **Q3: exact mechanism for identity crossing the BFF → API boundary** (F12/F29) — a forwarded
  signed JWT as gRPC metadata, a trusted internal header, or something else. Assumption until the
  spec (in coordination with the BFF spec) decides: the BFF forwards its own already-signed JWT as
  gRPC call metadata, and vertice-api validates it — the smallest change to both sides given the
  BFF already mints a JWT carrying `id`/`role` (`vertice-bff/src/lib/jwt.ts`).

Inherited from the PRD's own open questions (§8), unchanged by this assessment: whether a trainer
can change more than an exercise's name (R30/R31) and what becomes of a multi-trainer pre-starter
exercise (R57–R59) are both PRD-level questions the PRD author already told the spec author to
assume as written; this assessment's coverage map (§2) does the same.

## 10. Inputs to the spec

`## 0. Scope decisions` must resolve:

- [ ] How caller identity is resolved in vertice-api and how it crosses the BFF boundary (F12, F26, F29)
- [ ] The muscle-group data model: table shapes, the primary/ordinal columns for R48–R50 (F1, F6, F7)
- [ ] How `exercises`/`workout_exercises`/`exercise_sets`/`set_logs` get cascaded for R51–R54, in what order (F4)
- [ ] How R57–R59's keep/duplicate/refile step is executed and who supplies the keep-list and group reassignments (F5)
- [ ] Whether `ExerciseRequest.muscle_group` (field 4) is `reserved` and replaced, or handled another way (F9)
- [ ] Whether/how `ListExercises`'s behavior change (unfiltered → ownership-scoped) is coordinated with existing callers (F10)
- [ ] The status code (and exception shape) for a cross-trainer refusal — `NOT_FOUND` vs `PERMISSION_DENIED` (F13)
- [ ] The exception (and status mapping, in both `GrpcExceptionAdvice` and `GlobalExceptionHandler`) for delete-while-in-use (F15, F20)
- [ ] Whether this feature adds the first repository-level (`@DataJpaTest`-style) test, and what it covers (F23)
- [ ] Where the caller-identity resolver lives and how it's shared across controllers (F26)
- [ ] Deploy/rollback runbook acknowledging the R51–R59 migration as a one-way door (F27, F28)
- [ ] How the zero-group refusal is checked once for both create and update, and what message it carries (F19)
- [ ] Whether the new muscle-group entity reuses the `MuscleGroup` name, and which files change with it in one PR (F25)
- [ ] Whether the cleanup's bulk delete needs protection from concurrent writes, or is deferred with a reason (F16; the spec defers it)
- [ ] Whether `ListExercises` paginates now, or is built as a real query and pagination deferred with a reason (F17; the spec defers pagination)
