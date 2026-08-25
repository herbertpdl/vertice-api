# Spec: TrainingPlan client assignment, dates, and level

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement), `docs/specs/grpc-training-plan/spec.md`
(the CRUD this extends), `docs/specs/user-unification/spec.md` (`User`/`Role` this reuses)

## 0. Scope decisions

`docs/requirements.md` asks for training plans to be "assigned" to a client and to carry
name/description/start date/end date/level, none of which exist today (`TrainingPlan` only has
`name`, `description`, `trainer`). Proceeding with the same "document the default, cheap to
revisit" approach this repo already uses (see `cpf-field/spec.md` §0):

- **`client_id` required, not nullable**: a training plan only makes sense once it's "assigned"
  to someone — requirements.md never describes an unassigned/draft plan. Same `NOT NULL` +
  role-checked-on-write treatment `trainer_id` already gets.
- **No uniqueness constraint on `client_id`**: requirements.md is explicit — "A client/costumer
  can have N training plans assigned to them, up to the personal trainer to decide."
- **`start_date`/`end_date` required, not nullable**: requirements.md lists them alongside
  name/description as things the plan "should have at least" — same required tier as those.
  `end_date` must be `>= start_date`, checked in the service (proto has no cross-field
  validation, same reason `assertCrefOnlyForTrainer`/date checks elsewhere are done in the
  service layer, not as Bean Validation annotations).
- **`level` is a required proto enum** (`BEGINNER`/`INTERMEDIATE`/`ADVANCED`), same
  unset-rejected-explicitly pattern already used for `Role` in `UserController#requireRole` —
  proto3 enums always default to a zero value, so "omitted" can't be a `@NotNull` Bean
  Validation annotation on a validation record.
- **No authorization enforcement added**: per the plan-mode decision in
  `/Users/herbertlago/.claude/plans/cached-hatching-dove.md`, this repo's gRPC layer has no
  per-caller identity/role resolution at all (`GrpcSecurityConfig`'s own comment: "any
  authenticated caller may do anything, no role/scope differentiation"). `client_id` here is a
  caller-supplied request field, exactly like `trainer_id` already is — not derived from a JWT.

## 1. Goal

`TrainingPlan` gains: a required `client` (`User`, role `CLIENT`) it's assigned to, a required
`startDate`/`endDate`, and a required `level`. `ListTrainingPlans` gains an optional `client_id`
filter (alongside the existing `trainer_id` one) so a client-facing caller can list its own plans
— the concrete mechanism behind requirements.md's "clients access their training plans on the
app whenever they want."

## 2. Data model

`ALTER TABLE training_plans`: add `client_id BIGINT NOT NULL REFERENCES users(id)`, `start_date
DATE NOT NULL`, `end_date DATE NOT NULL`, `level VARCHAR(20) NOT NULL`. Migration `V15`. The local
dev DB has one pre-existing `training_plans` row (confirmed live against the running
`vertice-postgres` container), so this follows the same nullable-add → backfill → `NOT NULL`
shape `V5`–`V8` already use, not a plain `NOT NULL` add: backfills `client_id` to the first
existing `CLIENT`-role user and `start_date`/`end_date` to `CURRENT_DATE`/`level` to `BEGINNER` as
placeholders — there's no way to recover a "real" answer for a row that predates these columns,
and this is throwaway local data, same reasoning `cpf-field/spec.md` §0 used. Accepted gap: if a
target environment ever has `training_plans` rows but zero `CLIENT`-role `users` rows, the
backfill subquery returns `NULL` and the final `SET NOT NULL` step fails loudly — acceptable since
that combination can't occur in practice (a plan row implies a client existed to assign it to).

```sql
ALTER TABLE training_plans ADD COLUMN client_id BIGINT REFERENCES users (id);
ALTER TABLE training_plans ADD COLUMN start_date DATE;
ALTER TABLE training_plans ADD COLUMN end_date DATE;
ALTER TABLE training_plans ADD COLUMN level VARCHAR(20);

UPDATE training_plans
SET client_id = (SELECT id FROM users WHERE role = 'CLIENT' ORDER BY id LIMIT 1),
    start_date = CURRENT_DATE,
    end_date = CURRENT_DATE,
    level = 'BEGINNER'
WHERE client_id IS NULL;

ALTER TABLE training_plans ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE training_plans ALTER COLUMN start_date SET NOT NULL;
ALTER TABLE training_plans ALTER COLUMN end_date SET NOT NULL;
ALTER TABLE training_plans ALTER COLUMN level SET NOT NULL;
```

`TrainingPlan` entity: `@ManyToOne` `client` (mirrors the existing `trainer` association exactly),
`LocalDate startDate`/`endDate`, `@Enumerated(EnumType.STRING) PlanLevel level` — new Java enum
`com.vertice.api.plan.PlanLevel { BEGINNER, INTERMEDIATE, ADVANCED }`, mirrored into proto the same
way `SetStrategy`/`Role` already are.

`TrainingPlanRepository` gains `findByClientId(Long clientId)`, mirroring `findByTrainerId`.

## 3. Contract (`training_plan.proto`)

New top-level enum:

```proto
enum PlanLevel {
  PLAN_LEVEL_UNSPECIFIED = 0;
  BEGINNER = 1;
  INTERMEDIATE = 2;
  ADVANCED = 3;
}
```

`TrainingPlanResponse` gains `int64 client_id = 5`, `string start_date = 6`, `string end_date = 7`
(ISO-8601 `yyyy-MM-dd`, same plain-string-over-the-wire choice already made for `cpf`/other
canonical-form fields — no dedicated date/timestamp proto type in use anywhere else in this repo),
`PlanLevel level = 8`. `TrainingPlanRequest` (update body) and `TrainingPlanCreateRequest` both
gain `client_id`, `start_date`, `end_date`, `level` alongside `name`/`description` — a plan's
assignment/dates/level are as editable as its name (same "person's own record" reasoning
`trainer-cref/spec.md` §2 used for making `cref` part of the update body).

`ListTrainingPlansRequest` gains `int64 client_id = 2` (field 1 remains `trainer_id`). Both are
optional filters — supplying neither returns everything, matching how `ListUsersRequest`'s `role`
filter already behaves when left at its zero value.

## 4. Validation rules

- `name`: unchanged, `@NotBlank`.
- `client_id`: must resolve to a `User` with `role == CLIENT`, else `ResourceNotFoundException`
  — identical shape to `TrainingPlanService`'s existing trainer-role check.
- `start_date`/`end_date`: parsed as `LocalDate`; unparsable or blank → `INVALID_ARGUMENT`
  (`ConstraintViolationException`, same class every other manual validation in this codebase
  throws); `end_date` before `start_date` → `INVALID_ARGUMENT` with a dedicated message.
- `level`: `PLAN_LEVEL_UNSPECIFIED` rejected the same way `UserController#requireRole` rejects
  `ROLE_UNSPECIFIED`.

## 5. Mapping

`TrainingPlanMapper` gains `@Mapping(target = "clientId", source = "client.id")` (mirrors the
existing `trainerId`/`trainer.id` mapping) plus straightforward `startDate`/`endDate`/`level`
passthroughs; dates cross the proto `string` ↔ Java `LocalDate` boundary via two small
`@Named` helpers on a new `ProtoDates` util (mirrors `ProtoStrings`), not inline in the service,
so both `toEntity`/`updateEntityFromRequest` and `toResponse` share the same parsing/formatting.

## 6. Out of scope

- Any authorization enforcement restricting who may set/change `client_id` (see §0).
- Historical/audit trail of plan reassignment.
- Overlapping-date validation across a client's multiple plans (requirements.md explicitly allows
  N plans per client with no stated constraint between them).
