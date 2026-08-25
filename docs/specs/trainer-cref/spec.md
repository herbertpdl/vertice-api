# Spec: CREF field on Trainer

Status: Deprecated — described `cref` on the standalone Trainer entity, which
`docs/specs/user-unification/spec.md` removed. The field itself survives on the unified `User`
entity (carried forward by that spec, restricted to `role = TRAINER`), but this spec's Trainer-
specific design no longer matches the codebase.
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`, `docs/specs/cpf-field/spec.md`

## 0. Revision note

This spec was originally written and implemented against the REST/OpenAPI-based Trainer (merged
to `main` as PR #14). Before that landed, `main` was discovered to be stale: PRs #5–#13 had been
stacking on each other rather than merging into `main`, and their tip (`feature/grpc-workout`)
had already converted Trainer/Student from REST to gRPC (removing the OpenAPI toolchain entirely,
PR #8) and added the mandatory `cpf` field (PR #9). This revision re-targets the same feature —
optional `cref` on Trainer — at the gRPC reality instead of the REST one. Section 2 and 4 below
are rewritten; the goal (section 1) and everything else is unchanged.

## 1. Goal

Trainer gains a `cref` field — the registration number issued by Brazil's Regional Council of
Physical Education (CREF), the professional body for personal trainers/coaches. It is **optional**:
a trainer can register without one and fill it in later (via `UpdateTrainer`).

## 2. Design decision: optional, not required

Trainer is served over gRPC (`docs/specs/grpc-trainer/spec.md`). `cref` is added as a plain
(non-`optional`-keyword) `string` field on the proto messages, the same style already used for
`name`/`email`/`cpf`. Practically:

- proto3 has no `required`/`optional` distinction for singular fields without the explicit
  `optional` keyword — every field has a default value (`""` for `string`) and callers simply don't
  set it. No separate "create" vs "update" schema split was needed for this (unlike the
  REST-era `TrainerCreateRequest`/`TrainerRequest` split, which existed for other reasons — password
  handling — not for `cref`).
- Because `cref` isn't required, `GrpcRequestValidator`'s `CreateValidation`/`UpdateValidation`
  records (in `TrainerController`) are **not** touched — no `@NotBlank`/format annotation is added
  for it, so omitting it (leaving it at the protobuf default `""`) never fails validation.
- `CreateTrainer` and `UpdateTrainer` accept `cref` when the caller sets it and simply persist
  whatever value comes through (including `""` when omitted — see section 5).

## 3. Data model

Column `cref` on the `trainers` table:

| Column | Type         | Constraints |
|--------|--------------|-------------|
| cref   | varchar(20)  | nullable    |

Migration `V13__add_cref_to_trainers.sql`. No backfill needed (new nullable column, no NOT NULL
step). `V13` is the correct next number on this branch (the gRPC stack's tip already occupies
`V1`–`V12`).

## 4. Schema changes (`trainer.proto`)

Add `string cref` as the next unused field number to:

- `TrainerResponse` (field 5, after `cpf` at 4)
- `TrainerRequest` (field 4, after `cpf` at 3)
- `TrainerCreateRequest` (field 5, after `cpf` at 4)

## 5. Validation rules

- `cref`: optional, no validator added to `TrainerController`'s validation records. `maxLength: 20`
  is enforced only at the JPA/DB column level (`@Column(length = 20)`, `VARCHAR(20)`), not via a
  gRPC-side validation annotation — a value longer than 20 chars would fail at persistence with a
  DB error rather than a clean `INVALID_ARGUMENT`. Accepted as a minor gap for this scope (matches
  how the REST-era spec treated it: generous max length, not expected to bind in practice since
  real CREF numbers are ~11 chars).
- No format/pattern validation: CREF has historical format variants across states/categories and
  there's no authoritative regex to validate against yet — enforcing one risks rejecting legitimate
  values. Revisit if/when we have a confirmed format source.
- proto3 cannot distinguish "not set" from "explicitly set to empty string" for a plain `string`
  field (no `optional` keyword used) — both arrive at the server as `""`. This means an omitted
  `cref` is stored as `""`, not `NULL`, despite the column being nullable. `cref` is the first
  optional string field on Trainer (`cpf` is required, so this ambiguity didn't previously come up).
  Accepted as a deliberate simplification for this scope; if a future optional field needs a real
  tri-state (unset/empty/value), use proto3's `optional` keyword there instead.

## 6. Mapping

No `TrainerMapper` changes needed: MapStruct auto-maps same-named properties (`cref` → `cref`,
via generated `getCref()`/`.setCref(...)`/builder `.setCref(...)`) in `toEntity`,
`updateEntityFromRequest`, and `toResponse` without an explicit `@Mapping` — same pattern already
relied on for `cpf`.

## 7. Out of scope

- CREF format/checksum validation.
- Uniqueness constraint on `cref` (unlike `email`/`cpf`, nothing here assumes CREF numbers are
  unique per trainer at the application level — if that turns out to matter, it's a separate spec).
- Distinguishing NULL from empty-string storage for `cref` (see section 5's proto3 note).
- Any UI/frontend work (vertice-web has no trainer registration screen yet).
- Making `cref` mandatory at any point (e.g. before a trainer can publish a workout) — explicitly
  decided against for this spec; today it's purely optional profile data.
