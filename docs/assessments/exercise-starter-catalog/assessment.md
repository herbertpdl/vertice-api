# Technical assessment: Starter exercise catalog

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/prds/exercise-starter-catalog/prd.md`, `docs/specs/grpc-exercise-catalog/spec.md`,
`docs/specs/exercise-video-url/spec.md`, `docs/specs/workout-exercise-crud/spec.md`,
`docs/specs/clone-workout/spec.md`, `docs/specs/workout-session-logging/spec.md` (touched),
`docs/specs/training-plan-fields/spec.md` (touched), `docs/specs/user-unification/spec.md`
(touched), `docs/domain-model.md`
Spec: not yet written (will be `docs/specs/exercise-starter-catalog/spec.md`)

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

### PRD fit

**F1** [High] — `Exercise.muscleGroup` is a single, non-repeatable enum column
(`Exercise.java:30-32`), but the PRD requires an exercise to carry one *or more* of 14 groups
(R3, R8, R11, R43). This isn't an incremental change to the existing field; it replaces it.
*Recommendation:* model as two new tables (`muscle_groups`, and a join table), per the owner's own
stated constraint (PRD §9).

**F2** [Info] — R33 (a rename appears in every workout, including completed ones) is already
satisfied with zero code change: `WorkoutLogResponse`/`SetLogResponse`
(`workout_session.proto:17-33`) carry only ids, never a denormalized exercise name. Nothing to
build for this rule.

**F3** [Info] — R44/E3/E4 (duplicate names allowed, including matching the starter set) already
hold: `exercises.name` has no `UNIQUE` constraint (`V4__create_exercises_table.sql`) and nothing
else in the codebase derives identity from an exercise's name.

### Data model and migrations

**F4** [High] — No cascade path, DB or JPA, exists from `Exercise` through
`workout_exercises` → `exercise_sets` → `set_logs` for R51–R54's removal. None of
`fk_workout_exercises_exercise` (`V10`), `fk_exercise_sets_workout_exercise` (`V11`), or
`fk_set_logs_exercise_set` (`V18`) declare `ON DELETE CASCADE` — only `V21` uses `ON DELETE`
anywhere in this codebase — and `Exercise` has no `@OneToMany` back-reference for JPA cascade to
run through either. A plain `DELETE FROM exercises` for a row still referenced by
`workout_exercises` fails on the FK constraint today. *Recommendation:* the removal migration must
delete in explicit dependency order — `set_logs` → `exercise_sets` → `workout_exercises` →
`exercises` — and must stop exactly there: `workout_feedback`/`workout_logs` key only to
`workout_log_id` (`V18`, `V19`), so R54 (session and its feedback untouched) holds automatically
as long as the script never reaches past `workout_exercises`.

**F5** [High] — R57–R59 (an exercise kept under R56 becomes private to the trainer whose workouts
use it; when more than one trainer's workouts use it, each gets their own copy with their own
workout entries repointed to it) has no existing building block. Nothing in this codebase
duplicates an `Exercise` row or repoints a `WorkoutExercise.exercise` reference from one row to
another. *Recommendation:* a one-off migration step (per-exercise, per-trainer-using-it) that (a)
creates one exercise row per distinct trainer found via
`workout_exercises → workouts → training_plans → trainer_id`, (b) repoints only that trainer's
`workout_exercises` rows to their copy, (c) sets the new ownership column, (d) assigns the launch
groups the platform team specifies (R59).

**F6** [Medium] — R48/R49 (a trainer's own exercises first, then starter-set exercises in the
exact order given in PRD §10, "most commonly prescribed first") needs a stored ordinal to
reproduce deterministically. `WorkoutExercise.order` (`workout_exercises.exercise_order`) is a
different concept — placement within one workout, not catalog ordering — and nothing else exists.
*Recommendation:* an ordinal column on the join table (position within the group the exercise is
primarily filed under), populated 1..N directly from each PRD §10 table's row order at migration
time.

**F7** [Medium] — The join table needs to distinguish the group an exercise is *filed under*
(PRD §10's per-section listing, where R49's ordinal applies) from every *other* group it also
carries (R50, explicitly unordered). *Recommendation:* a boolean/flag column on the join row (e.g.
"primary"), set once at seed time.

**F8** [Info] — `V20`'s muscle-group backfill (name-substring heuristic, documented in its own
comment as "throwaway local data") is superseded by R51 for any pre-starter row not explicitly
kept under R56; the spec doesn't need to reconcile old heuristic values except for the handful of
R56-kept rows, which the platform team refiles manually anyway (R59).

### API contract and backward compatibility

**F9** [High] — `ExerciseRequest.muscle_group` (field 4, singular `MuscleGroup`) must become
multi-valued to satisfy R43. No `.proto` file in this codebase uses `reserved` yet
(`grep -rn reserved src/main/proto` — no hits), so this is the first field this codebase actually
breaks. *Recommendation:* add a new field (e.g. `repeated int64 muscle_group_ids = 5`) and mark
field 4 `reserved` rather than silently retyping/renumbering it, so this sets the convention
cleanly instead of by accident.

**F10** [High] — `ListExercisesRequest` is an empty message today, and `ListExercises` returns
every row unfiltered (`ExerciseService.java:20-25`, plain `findAll()`). Once ownership-scoped
visibility (R13–R15) ships, the *same* RPC called with the *same* (unmodified) request shape now
returns a caller-scoped subset instead of everything — a behavior break per dimension 3's own
definition ("a list that starts filtering ... is a compatibility break for the BFF even with the
same proto"), independent of any new fields added. *Recommendation:* confirm with the chained BFF
assessment whether anything depends on today's unfiltered list; treat this as a deploy-ordering
concern (§7), not just an additive-fields one.

**F11** [Info] — `GetExercise`/`CreateExercise`/`UpdateExercise`/`DeleteExercise` gain new refusal
paths but no wire-shape changes beyond F9 — no additional compatibility concern.

### Security and privacy

**F12** [Blocker] — No per-caller identity is available anywhere in this stack today for
vertice-api to enforce R14–R25/R32/R36. Three independent pieces of evidence, all verified this
session:
1. No business code in vertice-api reads the authenticated principal —
   `grep -rln "SecurityContext\|Authentication\|Principal" src/main/java` outside `grpc/` returns
   nothing; `GrpcSecurityConfig`'s own doc comment says "any authenticated caller may do anything,
   no role/scope differentiation," and every prior spec's `## 0. Scope decisions` repeats this as
   an accepted gap (§3).
2. `vertice-bff` already has this exact pattern one layer up:
   `vertice-bff/src/lib/ownership.ts` derives a real `AuthUser` (`id`, `role`) from a JWT it mints
   itself (`vertice-bff/src/lib/jwt.ts`) and enforces per-caller ownership before calling
   vertice-api for plans/workouts/workout-exercises.
3. But `vertice-bff/src/grpc/clients.ts` wires every gRPC client to vertice-api with
   `grpc.credentials.createInsecure()` and attaches no token or metadata at all — nothing carries
   the BFF's already-resolved identity across to vertice-api today, so even a JWT-verifying
   vertice-api would have nothing to check when called through the BFF as currently wired.

This matters concretely because R16–R18/R32/R36 and E18/E21 use adversarial framing ("a trainer
who *learns* another trainer's private exercise identifier fetches it *directly*") that describes
a caller reaching vertice-api's gRPC surface directly, bypassing `ownership.ts` entirely — so
enforcement has to live in vertice-api itself, not only in the BFF, for those rules to hold.
*Decision made this session:* build real per-caller identity resolution into vertice-api (not a
caller-supplied `trainer_id`), mirroring the shape `vertice-bff/src/lib/ownership.ts` already
established. *Recommendation:* (a) this session's chained `technical-assessment-bff` must record
how identity crosses the BFF → API boundary (e.g. the BFF forwards its own signed JWT as gRPC call
metadata for vertice-api to validate) — it's a two-repo change; (b) build one reusable
"current trainer" resolver under `grpc/` (F26), not an inline check inside `ExerciseService`.

**F13** [Medium] — The status code for a cross-trainer refusal (R16–R18, R32, R36) is undecided.
`ResourceNotFoundException` → `Status.NOT_FOUND` (`GrpcExceptionAdvice.java:24-27`) echoes the id
back in its message, but that's not new information to a caller who already supplied it — the
real question is whether "exists but isn't yours" should be indistinguishable from "doesn't exist"
(`NOT_FOUND` for both) or explicit (`PERMISSION_DENIED`, which `GrpcExceptionAdvice`'s own comment
notes Spring gRPC's `SecurityGrpcExceptionHandler` already maps for `AccessDeniedException`). PRD
language ("refused, not merely absent from their list") leans toward the caller knowing they were
refused. *Recommendation:* the spec decides explicitly — this sets the pattern for every future
ownership check in this codebase, not just this one.

**F14** [Nothing found] — Checked: free-text bounds for the fields this feature touches. `name`
(`VARCHAR(255) NOT NULL`) and `description` (`VARCHAR(255)`) from `V4`, `video_url`
(`VARCHAR(500)`) from `V17` are already bounded, and `ExerciseController`'s existing `@Pattern`
(`ExerciseController.java:80`) already restricts `video_url` to blank-or-http(s). Nothing new
needed for R41/R42/R6.

### Data integrity and consistency

**F15** [High] — R34 (delete refused while any workout uses the exercise) has zero enforcement
today: `ExerciseService.deleteExercise` (`ExerciseService.java:43-46`) deletes unconditionally.
Today, deleting a referenced exercise fails at the database as a raw FK-violation exception, which
`GrpcExceptionAdvice` doesn't map (falls through to `UNKNOWN`) — not the described refusal R34
wants. This codebase already has the right shape of exception for exactly this case:
`WorkoutExerciseHasRecordedDataException` → `Status.FAILED_PRECONDITION`
(`GrpcExceptionAdvice.java:37-40`), used for "can't replace, something depends on it."
*Recommendation:* add an equivalent exception, checked via an `existsBy...` query on
`WorkoutExerciseRepository`, before the delete.

**F16** [Medium] — No optimistic locking exists anywhere (`grep -rn "@Version\|@Lock"
src/main/java` — no hits). Not a new gap this feature introduces, but the R51–R59 migration is the
first genuinely destructive bulk write in this codebase's history, running once against
potentially-live tables; last-write-wins silently, same as everywhere else, if ordinary traffic
touches the same rows at the same moment.

### Performance and scalability

**F17** [Medium] — No RPC in this codebase paginates (`grep -rn "Pageable\|page_size\|page_token"`
— no hits), and today's `ListExercises` is an unfiltered `findAll()`. R48/R49's ordering can't be
produced by sorting a fully-loaded list in Java without the ordinal from F6 — it needs a real
query (join across exercise/join-table/muscle-group, ordered by own-first then stored ordinal),
not app-side filtering. Fine at ~200 starter rows plus a handful of private rows per trainer
today; worth building as a real query from the start rather than a later migration, since PRD Flow
1 makes this the "every workout-build" hot path.

**F18** [Nothing found] — Checked: N+1 risk beyond the list query above.
`WorkoutExerciseService.createWorkoutExercise` (`WorkoutExerciseService.java:36-40`) and
`WorkoutService.cloneWorkout` (`WorkoutService.java:96`) already load `Exercise` via simple
`findById` per reference — unaffected by this feature.

### Error handling

**F19** [Medium] — R40/R31/E13/E22 (create/update refused with zero groups) is a direct extension
of the existing pattern: `ExerciseController#requireMuscleGroup` (`ExerciseController.java:72-76`)
already rejects the proto3 zero-value case for the single-enum field the same way
`WorkoutController#requireDayOfWeek` does; the multi-group version needs the equivalent
"list is empty" check, reused for both create (R40) and update (R31).

**F20** [Medium] — New exception types for R26/R27 (starter-set immutability) and the R14–R25
ownership family (F12/F13) must be added to **both** `GrpcExceptionAdvice` and
`GlobalExceptionHandler`, per CLAUDE.md's explicit convention — nothing enforces keeping the two in
sync, so it's easy to add one and forget the other.

### Logging

**F21** [Info] — No application code logs anything today (`grep -rln "Slf4j\|LoggerFactory\|
log\.\(info\|warn\|error\|debug\)" src/main/java` — no hits). The R51–R54 removal is the most
consequential/irreversible write this codebase will have run; if it's executed as anything other
than plain SQL (§5), it's a reasonable place to set this codebase's first logging convention, but
nothing requires it.

### Metrics and observability

**F22** [Nothing found] — Checked: no `MeterRegistry`/`@Timed`/`@Observed` anywhere, consistent
with the existing baseline. `ListExercises` becoming a hot, per-caller-filtered endpoint (F17) is
worth a latency/row-count signal eventually, but this feature doesn't need to be the one that
introduces metrics tooling to this codebase.

### Testing

**F23** [High] — This is the first feature whose correctness depends on real SQL ordering/joins
across three tables (R47–R50). This codebase's only test flavors are a Mockito `*ServiceTest`
(mocked repository — can't prove a real `ORDER BY`/join produces the right sequence) and a
wire-level `*ControllerTest`. No repository-level test slice exists today
(`@DataJpaTest` — no hits anywhere in `src/test`). *Recommendation:* add the first one to prove
R47–R50 against real Postgres rather than trusting a mocked list.

**F24** [Nothing found beyond F23] — `ExerciseControllerTest`/`ExerciseServiceTest` (15 and 7
`@Test` methods) are a reasonable template for the CRUD-shaped rules. Next free
`spring.grpc.server.port`: **19104** (19090–19103 confirmed in use across existing
`*ControllerTest` classes — one higher than this skill's recorded baseline of 19090–19102; noted
as baseline drift).

### Architecture fit and maintainability

**F25** [Medium] — `MuscleGroup` moving from a Java enum to a JPA entity/table (F1) is contained
to `plan/exercise/` plus `ExerciseMapper`/`exercise.proto` — nothing outside that package
references `MuscleGroup` directly; every other entity that touches an exercise (`WorkoutExercise`)
only ever references `Exercise` itself.

**F26** [High] — The caller-identity resolver F12 needs is inherently cross-cutting: every future
spec adding ownership to trainer-owned data will need the same "who is calling, do they own this"
shape. *Recommendation:* build it once under `grpc/` (this codebase's existing home for
cross-cutting gRPC concerns — `GrpcRequestValidator`, `GrpcExceptionAdvice`, the `Proto*` helpers),
not inline inside `ExerciseService`, so this feature isn't the first of many one-off copies.

### Operability and rollout

**F27** [High] — The `MuscleGroup` enum→table migration and `Exercise.muscleGroup`
column→join-table migration must land in the exact same deploy as the code —
`spring.jpa.hibernate.ddl-auto=validate` means old code booting against the new schema (or the
reverse) fails at startup, not at first use. There's no partial/rolling window here, unlike a
normal additive column.

**F28** [High] — No Flyway undo migrations exist in this codebase (only forward `V*` files).
Combined with F4/F5's one-off, destructive R51–R59 migration, rolling the app back after it runs
leaves the schema — and the already-deleted rows — ahead with no automatic path back. The owner
confirmed this session that the affected rows are test data, capping the practical risk, but it's
still a one-way door worth calling out explicitly in the deploy runbook.

**F29** [High] — Same root cause as F12, restated for rollout purposes: `vertice-bff`'s gRPC
client to vertice-api is wired insecure with zero auth metadata
(`vertice-bff/src/grpc/clients.ts`). Whatever mechanism F12 settles on for identity to cross that
boundary is a coordinated two-repo deploy, not something vertice-api can ship and validate alone.

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

**Sequencing**: the PRD gives no explicit smaller-first-version signal, but a natural split exists:
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
