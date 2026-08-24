# Spec: CREF field on Trainer

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`

## 1. Goal

Trainer gains a `cref` field — the registration number issued by Brazil's Regional Council of
Physical Education (CREF), the professional body for personal trainers/coaches. It is **optional**:
a trainer can register without one and fill it in later (e.g. via `PUT /api/trainers/{id}`).

## 2. Design decision: optional, not required

`cref` is added as a nullable column and an optional property on the request/response schemas —
not part of the `required` array on `TrainerCreateRequest` or `TrainerRequest`. This means:

- The existing all-required-args constructor generated for `TrainerCreateRequest`
  (`name, email, password`) and `TrainerRequest` (`name, email`) is unchanged — adding an optional
  property does not add it to that constructor (openapi-generator only includes `required`
  properties there). No existing caller of those constructors breaks.
- `POST /api/trainers` and `PUT /api/trainers/{id}` accept `cref` when present and simply leave the
  column `NULL` when it's omitted.

## 3. Data model

Column `cref` on the `trainers` table:

| Column | Type         | Constraints |
|--------|--------------|-------------|
| cref   | varchar(20)  | nullable    |

Migration `V13__add_cref_to_trainers.sql`. No backfill needed (new nullable column, no NOT NULL
step).

Numbered `V13`, not the next-looking `V7`: several unmerged branches (`feature/cpf-field`,
`feature/workout-exercise-sets`, the `feature/grpc-*` series) already occupy `V7`–`V12` on this
machine's shared local dev Postgres instance (confirmed via `flyway_schema_history`). `V7` here
would checksum-collide with `feature/cpf-field`'s `V7__add_cpf_to_trainers.sql` (an unrelated,
unmerged field). This number will likely need to be revisited at merge time depending on which of
those branches lands first — see the PR description.

## 4. Schema changes (`api.yaml`)

Add `cref: { type: string, maxLength: 20 }` (not in `required`) to:

- `TrainerCreateRequest`
- `TrainerRequest`
- `TrainerResponse`

## 5. Validation rules

- `cref`: optional. When present, `maxLength: 20` (generous — Brazilian CREF numbers look like
  `123456-G/SP`, well under 20 chars). No format/pattern validation in this scope: CREF has
  historical format variants across states/categories and we don't have an authoritative regex to
  validate against yet — enforcing one risks rejecting legitimate values. Revisit if/when we have a
  confirmed format source.
- An empty string (`""`) is accepted as-is (same treatment as any other optional string field in
  this codebase — no existing precedent here for coercing `""` to `null`, so this spec doesn't
  introduce one).

## 6. Mapping

No `TrainerMapper` changes needed: MapStruct auto-maps same-named properties (`cref` → `cref`) in
`toEntity`, `updateEntityFromRequest`, and `toResponse` without an explicit `@Mapping`.

## 7. Out of scope

- CREF format/checksum validation.
- Uniqueness constraint on `cref` (unlike `email`, nothing here assumes CREF numbers are unique
  per trainer at the application level — if that turns out to matter, it's a separate spec).
- Any UI/frontend work (vertice-web has no trainer registration screen yet).
- Making `cref` mandatory at any point (e.g. before a trainer can publish a workout) — explicitly
  decided against for this spec; today it's purely optional profile data.
