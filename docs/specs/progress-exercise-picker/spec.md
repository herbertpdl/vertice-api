# Spec: Progress exercise picker

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/lazy-exercise-search/spec.md` §6 (the follow-up this closes),
`docs/specs/exercise-progress/spec.md` (the `GetExerciseProgress` series the picker feeds)

## 0. Analysis (2026-10-30)

The student detail "Progresso" tab (web `ProgressTab`, mobile `StudentDetailScreen`) offered every
exercise in the trainer's catalog. After lazy-exercise-search that meant walking every page of
`GET /exercises` on each visit:

1. **Load:** 199 starter rows plus all of the trainer's own exercises, in 4+ requests, every time
   the tab opened, growing with the trainer's catalog. This is the "load everything" the owner
   ruled out.
2. **Mostly useless options:** a student trains a few dozen exercises at most, so nearly every
   option drew an empty chart, and a 200-entry dropdown with no search is hard to use.
3. **Wrong set of exercises:** the catalog is the *trainer's* view. What the chart can show is the
   *student's* history.

## 1. Decisions

- **D1 — List what has points.** New `ListProgressExercises(client_id)` returns exactly the
  exercises `GetExerciseProgress` has a point for: at least one set log with a weight, in a
  `COMPLETED` session (in-progress, superseded and weightless sets don't count, as for the graph).
- **D2 — Most recently trained first**, then name and id, so the default pick is what the trainer
  most likely wants.
- **D3 — Not paged.** The list is bounded by what one student has trained (each workout holds at
  most 20 exercises), not by the catalog, so it is never "everything". `{id, name}` only.
- **D4 — Visibility unchanged.** Same callers as `GetExerciseProgress` (the client, their active
  trainer, ADMIN). A trainer or ADMIN gets only exercises the catalog shows them (starter set and
  their own), as the dropdown did before, so another trainer's private exercise names (a client's
  earlier trainer) never reach them. The client gets their whole history.

## 2. Contract

`workout_session.proto`: `rpc ListProgressExercises(ListProgressExercisesRequest) returns
(ListProgressExercisesResponse)`; `ListProgressExercisesRequest { int64 client_id = 1; }`,
`ProgressExercise { int64 id = 1; string name = 2; }`,
`ListProgressExercisesResponse { repeated ProgressExercise exercises = 1; }`. `client_id` `@Min(1)`.

vertice-bff: `GET /exercises/with-progress?clientId=` (TRAINER, CLIENT; like
`GET /exercises/:id/progress`, a CLIENT's `clientId` is ignored and a TRAINER must pass one of
their clients) → `ProgressExercise[]`.

## 3. Clients

Web `ProgressTab` and mobile `StudentDetailScreen` fill the dropdown from that route and preselect
nothing; with no options they say the student has no recorded loads yet. `fetchAllExercises` (the
page walker) is removed: nothing lists the whole catalog any more.

## 4. Merge and deploy order

vertice-api → vertice-bff (re-pin `protos/.api-ref`) → vertice-web-react and vertice-mobile
(re-pin `.bff-ref`), merged one after the other once all four are approved and green. Additive:
no existing route or field changes, so any deploy order works.
