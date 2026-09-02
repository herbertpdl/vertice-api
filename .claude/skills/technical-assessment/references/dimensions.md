# Assessment dimensions

Work through these in order for section 4 of the assessment. Each dimension has:

- **Look for**: what to check for the feature under assessment.
- **Baseline**: what this codebase did when this file was written, with the command that
  re-verifies it. Baselines drift. Run the command in the session and cite the code you saw,
  never this file. A baseline that has changed is worth a line in section 3 (Current state)
  of the assessment so the spec author knows too.

Findings go under a `###` heading per dimension, in this order, using the format and severity
scale in `SKILL.md`. "Nothing found. Checked: ..." is the right entry when a dimension is
clean; leaving a dimension out is not.

## 1. PRD fit

Look for:
- Rules the PRD states that the codebase already violates today (existing gap the feature
  inherits or must fix).
- Rules that are ambiguous once you try to place them in the code ("the client sees their
  weight from last time": last completed session, or last session including abandoned ones?).
  These are questions back to the owner (section 9), not decisions for you to make.
- Rules that conflict with an accepted scope decision in an existing spec.
- Out-of-scope items in the PRD that the code cannot cleanly leave out (a deletion rule that
  has to be decided because of an existing cascade, for instance).

Baseline: PRDs live in `docs/prds/`, specs in `docs/specs/`; spec `## 0. Scope decisions`
sections carry the accepted gaps. `grep -rn "accepted gap\|not asked for\|out of scope" docs/specs`.

## 2. Data model and migrations

Look for:
- New tables or columns and how existing rows get a value: nullable, default, or backfill.
  A `NOT NULL` column without a default on a table with production rows fails the migration.
- Foreign keys and what deletion does to them (see also dimension 5).
- Uniqueness the PRD implies (per plan, per client, per week) and whether a database
  constraint or application check enforces it. Application-only checks race (dimension 5).
- Indexes for the queries the feature adds; a filtered list on an unindexed column is fine
  at small scale and a cliff later. Note it with the expected growth from the PRD.
- Entity/migration drift: the app starts with `ddl-auto=validate`, so an entity mapping that
  disagrees with the migration fails at boot, not at first use.
- Migration numbering: sequential, not zero-padded; check the highest existing number.

Baseline:
- 21 migrations, `V1`..`V21`. `ls src/main/resources/db/migration | sort -V | tail -1`.
- Only `V21` (trainer_clients) declares an index or `ON DELETE CASCADE`; every other
  parent/child relation relies on JPA-side cascade. `grep -rln "CREATE INDEX\|ON DELETE" src/main/resources/db/migration`.
- `spring.jpa.hibernate.ddl-auto=validate` in `application.properties`.
- Decimal quantities (weights, load percentage) are `NUMERIC` in SQL and `BigDecimal` in
  Java, crossing gRPC as strings (`grpc/ProtoDecimals`).

## 3. API contract and backward compatibility

Look for:
- Changes to existing proto messages: a renamed or renumbered field, a removed field whose
  number is not reserved, a changed type, or a changed meaning of an existing field, all
  break the consumer silently (proto3 does not fail, it misreads). Additive fields are safe.
- New enum values: consumers on the old proto see them as unknown. Enum zero value must stay
  `*_UNSPECIFIED` and map to Java `null` (CLAUDE.md pattern).
- Changes to existing RPC behavior: a list that starts filtering, a create that starts
  rejecting input it accepted before, a response that omits something it used to carry. Each
  is a compatibility break for the BFF even with the same proto.
- Whether the consumer can be deployed together with the API or must tolerate both versions.
  Ask the owner (section 9) if it matters for this feature.
- Deprecation: what the old path does once the new one exists (kept, deprecated with a
  proto comment, or removed with a `reserved`).

Baseline:
- Consumers: `vertice-bff` calls this API over gRPC; `vertice-web` sits behind the BFF
  (README "Full stack" section). No mobile client is documented; confirm with the owner if
  the feature is client-facing.
- No proto file uses `reserved` yet, so nothing has been removed before; the first removal
  sets the convention. `grep -rn reserved src/main/proto`.
- Proto packages `vertice.<area>.v1`; there is no `v2` anywhere. A breaking change would be
  the first to need one.

## 4. Security and privacy

Look for:
- Authorization: can the caller act on data that is not theirs? Every request today is
  trusted for any id it sends. A feature that exposes client data to trainers or vice versa
  makes that gap bigger; say by how much (what a caller could now read or change with a
  guessed id).
- Input handling: sizes (free text without a max is a storage and abuse vector), URLs the
  app stores and later hands to a browser (video links), anything rendered elsewhere.
- Enumeration: sequential ids plus `NOT_FOUND` messages that echo the id let a caller probe
  what exists. Note when the feature adds a new lookup by id.
- PII: name, email, CPF, password hash live on `users`; the product is Brazilian, so LGPD
  applies to how personal data is stored, exposed, and deleted. Flag any new field that is
  personal data or health-related (weights, injuries, feedback text can be) and any response
  that carries user PII to a caller who did not have it before.
- Secrets and config: anything the feature needs from the environment, and whether the
  `local` profile bypass still behaves.

Baseline:
- Auth is JWT resource server, flat: any authenticated caller may call any RPC with any id.
  No role or scope check, no ownership check; business code never reads the caller identity.
  `grep -rn "SecurityContext\|Authentication\|Principal" src/main/java --include=*.java | grep -v config/`.
  Earlier specs accept this explicitly (`docs/specs/training-plan-fields/spec.md` §0 "No
  authorization enforcement added", and the session-logging and feedback specs that cite it).
- `local` profile disables auth for both REST and gRPC (`config/LocalSecurityConfig`,
  `grpc/GrpcSecurityConfig`); reflection is on under `local` only.
- Error descriptions are built from exception messages (`grpc/GrpcExceptionAdvice`), and
  `ResourceNotFoundException` messages include the id.
- Free-text columns have lengths in SQL (`workout_feedback.text VARCHAR(2000)`); check the
  new ones do too.

## 5. Data integrity and consistency

Look for:
- Transactions: does the feature write more than one aggregate in one operation, and is the
  boundary one `@Transactional` service method? Partial writes on failure are a PRD question
  ("partial success") before they are a technical one; check the PRD answered it.
- Concurrency: two callers touching the same row (trainer edits a workout while the client
  logs it). There is no optimistic locking; last write wins silently. Say whether the feature
  makes that visible to users.
- Idempotency and duplicates: a retried request that creates twice; a uniqueness rule from
  the PRD enforced only in Java that two concurrent requests can both pass.
- Cascades and orphans: what deleting a parent does to the feature's new rows, and what
  deleting the feature's rows does to existing ones. JPA cascade only runs through the entity
  graph the code loads; a `deleteById` on a parent whose collection is not mapped leaves
  orphans or fails on the FK.
- Derived data (progress graphs, "done this week") computed from logs: what happens when the
  source rows are edited or deleted afterwards.

Baseline:
- No `@Version` or explicit locks anywhere. `grep -rn "@Version\|@Lock" src/main/java`.
- Services are class-level `@Transactional` with `readOnly = true` on reads.
- Parent→child deletion relies on `cascade = CascadeType.ALL, orphanRemoval = true` on the
  parent entity collections (`plan/TrainingPlan`, `plan/workout/Workout`,
  `plan/workout/WorkoutExercise`); SQL-level cascade exists only in `V21`.

## 6. Performance and scalability

Look for:
- List operations the feature adds or changes: bounded how? Every existing list returns
  everything matching; a feature that lists across a trainer's whole history (all feedback,
  all logs) grows without bound.
- N+1: a derived query returning entities whose lazy associations the mapper then touches
  one row at a time (`findBy...` then `getWorkout().getTrainingPlan()...` in the mapper). Look
  at what the response embeds and where each embedded value comes from.
- Deep graphs: cloning or loading a whole plan tree in one request; fine at tens of rows,
  note the cliff.
- Aggregations (progress over weeks): computed in SQL, or by loading rows and reducing in
  Java? Say which the data volume from the PRD justifies.
- Hot paths: anything a client app calls on every screen open.

Baseline:
- No pagination on any RPC; no `Pageable` in any repository.
  `grep -rn "Pageable\|page_size\|page_token" src/main/proto src/main/java`.
- Associations are `FetchType.LAZY`; repositories use derived queries; no `@EntityGraph` or
  `JOIN FETCH` anywhere. `grep -rn "EntityGraph\|JOIN FETCH" src/main/java`.
- Hikari pool is capped at 3 in tests only; production uses the default.

## 7. Error handling

Look for:
- New failure modes and which gRPC status each maps to; whether an existing exception type
  fits or a new one is needed, and if new, that it is added to both `GrpcExceptionAdvice` and
  `GlobalExceptionHandler` (CLAUDE.md).
- Rules the PRD states as "refused" that today would surface as a database error
  (constraint violation → `UNKNOWN`) instead of a meaningful status.
- Precondition failures thrown as `ConstraintViolationException` with a hand-written message
  and an empty violation set (the existing pattern for "workout not completed"); it works but
  the message format differs from real validation failures. Note when the feature adds more.
- What the consumer can act on: does the status plus description let the BFF show the user
  something useful without parsing text?

Baseline:
- Four exception types mapped: `ResourceNotFoundException` → `NOT_FOUND`,
  `DuplicateEmailException`/`DuplicateCpfException` → `ALREADY_EXISTS`,
  `ConstraintViolationException` → `INVALID_ARGUMENT`. Anything else → `UNKNOWN` by default.
  `cat src/main/java/com/vertice/api/grpc/GrpcExceptionAdvice.java`.
- Request validation is manual: a private record with Bean Validation annotations, checked
  by `GrpcRequestValidator` in each controller.

## 8. Logging

Look for:
- What the feature needs logged to be operated: creation and deletion of durable data,
  refusals with the reason, anything irreversible. Reads normally need nothing.
- What must never be logged: PII (dimension 4), request bodies with free text, tokens.
- Correlation: with no request id today, a log line cannot be tied to a call. Say whether
  the feature is the one that makes that painful (multi-step operations, async work).
- Consistency: if the feature adds the first log lines, it sets the convention (logger per
  class via Lombok `@Slf4j`, level policy, key=value vs prose). Recommend one in the finding
  rather than leaving each PR to choose.

Baseline:
- No application code logs anything: no `@Slf4j`, no `LoggerFactory`, no `log.` calls in
  `src/main/java`. Only framework logging (Spring, Hibernate with `show-sql` under `local`).
  `grep -rln "Slf4j\|LoggerFactory\|log\.\(info\|warn\|error\|debug\)" src/main/java`.
- No request-id or MDC setup; no gRPC interceptor for logging.

## 9. Metrics and observability

Look for:
- Signals someone would want after shipping: count of the new operation, failure rate by
  status, latency of anything that touches a lot of rows, size of unbounded lists.
- Whether the feature introduces a background or scheduled step (none exist today) that
  needs its own success/failure signal.
- Health: does the feature add an external dependency (video host, notification channel)
  that health or readiness should reflect?
- Tracing: not present; say if the feature's call chain is deep enough to want it.

Baseline:
- Actuator exposes `health` and `info` only (`management.endpoints.web.exposure.include`).
  Micrometer is on the classpath through the actuator starter, but no custom meter, `@Timed`,
  or `@Observed` exists. `grep -rn "MeterRegistry\|@Timed\|@Observed" src/main/java`.
- gRPC health service is wired (`grpc/GrpcHealthConfig`).
- Nothing exports metrics anywhere; a first metric needs an exporter decision too.

## 10. Testing

Look for:
- Each PRD rule and edge case mapped to a test scenario at the right layer: service test for
  business rules, controller test for validation and status mapping over the wire.
- A new controller test needs its own gRPC port; list the ones in use.
- Rules that need real database behavior (unique constraints, cascades, derived queries
  across three associations) and are not covered by a Mockito service test. There is no
  repository-level test slice today; say whether the feature needs the first one.
- Data-dependent rules (week boundaries, "last time") that need a fixed clock to be testable;
  the code uses `Instant.now()` directly.

Baseline:
- One `*ServiceTest` (Mockito, real MapStruct mapper) and one `*ControllerTest`
  (`@SpringBootTest`, real gRPC server, `@MockitoBean` service, `local` profile) per
  aggregate. 236 `@Test` methods. `grep -rc "@Test" src/test/java | awk -F: '{s+=$2} END {print s}'`.
- Controller test ports in use: 19090–19102.
  `grep -rhn "spring.grpc.server.port" src/test/java | grep -o "1909[0-9]\|191[0-9][0-9]" | sort -u`.
- Time is read via `Instant.now()` in services with no injectable `Clock`.
  `grep -rn "\.now()" src/main/java`.
- CI runs `./gradlew build` against Postgres 16.

## 11. Architecture fit and maintainability

Look for:
- Which aggregate package the feature belongs to, and whether it needs a new one. A feature
  that spans two aggregates (feedback on a session inside a plan) needs a home; say which and
  why, following the vertical-slice layout in CLAUDE.md.
- Reuse versus duplication: helpers in `grpc/Proto*`, existing mappers, existing derived
  queries. Note when the feature would be the third copy of something.
- Cross-aggregate coupling: a service reaching into another aggregate's repository (already
  done: `WorkoutFeedbackService` uses `WorkoutLogRepository`). Acceptable here; note when it
  would create a cycle.
- Generated code: new protos land under `com.vertice.api.generated.grpc.*`; nothing
  hand-written goes there.

Baseline: layout and conventions as in `CLAUDE.md` (Controller = `@GrpcService`, manual
validation, MapStruct with `@Named` conversions, enum `_UNSPECIFIED` → `null`).

## 12. Operability and rollout

Look for:
- Ordering: migration runs at boot before the new code serves traffic (Flyway on startup),
  so a column the old code does not know about must be nullable or defaulted for the window
  where the old version might restart.
- Coexistence: can the current BFF keep working against the new API until it is updated?
  (Dimension 3 decides; state the deploy consequence here.)
- Rollback: a migration cannot be undone by Flyway here (no undo migrations); rolling back
  the app leaves the schema ahead, which is fine only if the change was additive.
- Configuration: new properties, environment variables, or profile-specific behavior.
- Data fixes: does shipping need a one-off script (backfill, dedupe) and who runs it?

Baseline:
- Flyway runs on startup; no undo migrations; `ddl-auto=validate`.
- Configuration is `application.properties` plus `application-local.properties`; database
  credentials and the JWT issuer come from environment variables.
- Docker image built from `Dockerfile`; local full stack via the sibling `vertice-local`.

## 13. Effort, risk, and dependencies

Look for:
- Size the work by the aggregates touched, the migrations, the proto surface, and the tests
  it needs; S is one aggregate and no migration, XL is a new aggregate plus a breaking API
  change plus a backfill.
- Risks that could change the size: an open PRD question, a baseline gap the feature cannot
  live with (first pagination, first ownership check, first log line), a dependency on the
  BFF or web changing at the same time.
- Sequencing: what could ship first as a smaller, useful slice, if the PRD marks a smaller
  first version.
