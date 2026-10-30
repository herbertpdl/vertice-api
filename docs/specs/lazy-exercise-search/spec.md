# Spec: Lazy exercise search

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/exercise-starter-catalog/spec.md` (the ordering R47-R50 this pages over),
`docs/specs/grpc-exercise-catalog/spec.md` (the RPC it extends)

## 0. Scope decisions

Owner request (2026-10-30): "We should not load everything, never. Make the first database query
request only when the user types 3 or more letters on the search field OR if the user chooses one
of the possible categories."

- **D1 — Server-enforced paging.** `ListExercises` always returns one page (default 30, maximum
  50), whatever the filter. A client gate alone would still let a broad search (`"ada"`, a large
  muscle group, a trainer with hundreds of own exercises) return an unbounded list.
- **D2 — Offset tokens, not keyset.** The ordering (own rows by name; starter rows filed under the
  filtered group as primary in `catalog_order`; the rest by name) has no single monotonic key, so
  the token is the number of rows already returned. `e.id` is appended to the `ORDER BY` so name
  ties never make two pages overlap or skip a row. The catalog is small (199 starter rows plus a
  trainer's own), so `OFFSET` cost is negligible. A row created or deleted between two page
  requests can shift the rest by one; acceptable for a picker.
- **D3 — Same conventions as `ListUsers`.** `page_size` 0 = default, above the maximum is
  lowered to it, negative is `INVALID_ARGUMENT`; `page_token` is opaque, malformed is
  `INVALID_ARGUMENT` (`page_token: invalid`); `next_page_token` empty on the last page.
- **D4 — The 3-letter / category gate is a client rule** (web and mobile pickers and catalog
  screens). The RPC still answers an unfiltered request, one page at a time, because the student
  progress dropdowns (out of scope, see §6) list exercises without a filter.
- **D5 — A 1–2 letter search is not sent.** Below 3 letters the search text is dropped (also when a
  group is selected), so results don't jump on each keystroke.

## 1. Contract (`exercise.proto`)

```proto
message ListExercisesRequest {
  int64 muscle_group_id = 1;
  string search = 2;
  int32 page_size = 3;   // new
  string page_token = 4; // new
}

message ListExercisesResponse {
  repeated ExerciseResponse exercises = 1;
  string next_page_token = 2; // new
}
```

Additive: an old caller that sends neither field gets the first 30 rows and ignores the token.

## 2. Query

`ExerciseRepository.findVisible` gains `limit`/`offset` (`LIMIT :limit OFFSET :offset`) and the
`e.id ASC` tie-breaker. The service asks for `page_size + 1` rows; the extra row only tells
whether a next page exists (no `count(*)`), and the token is then `offset + page_size`.

## 3. vertice-bff

`GET /exercises?muscleGroupId&q&limit&cursor` → `{ exercises: Exercise[], nextCursor: string | null }`
(breaking for the response body: an array before). `limit` 1–50 and `cursor` are passed through as
`page_size`/`page_token`; upstream's `INVALID_ARGUMENT` for a bad cursor is the usual 400.

## 4. vertice-web-react and vertice-mobile

- The workout-editor pickers (web `AddExerciseDialog`, mobile `AddExerciseSheet`) and the exercise
  catalog screens (web `/exercicios`, mobile Exercícios tab) query only when the trimmed search has
  3 or more characters or a muscle group is selected. Before that, they show
  "Digite ao menos 3 letras ou escolha um grupo muscular" in their existing empty-state style.
  Choosing "Todos"/"Todos os grupos" returns to that state.
- Lists use `useInfiniteQuery` keyed by `["exercises", {muscleGroupId, q}]` (the existing prefix,
  so create/edit/delete invalidation is unchanged) and fetch the next page when the user scrolls
  near the end.

## 5. Merge and deploy order

vertice-api → vertice-bff (re-pin `protos/.api-ref`) → vertice-web-react and vertice-mobile
(re-pin `.bff-ref`). The BFF response-shape change must reach production with (or after) the web
and mobile builds that read it. Neither is deployed or published yet.

## 6. Out of scope

- The student progress tab exercise dropdowns (web `ProgressTab`, mobile `StudentDetailScreen`):
  they keep listing every exercise by walking the pages. Follow-up: list only the exercises the
  client has history for, or replace the dropdown with a searchable picker.
- Full-text / accent-insensitive search (`ILIKE` stays).
