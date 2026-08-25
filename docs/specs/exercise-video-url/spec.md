# Spec: Exercise video URL

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "The excercise should also render a video in
the ap for the client to see how to execute the exercise, the video should be a youtube video or
any external link for a video"), `docs/specs/grpc-exercise-catalog/spec.md` (the CRUD this
extends)

## 0. Scope decisions

- **Plain validated URL string, no embedding/upload.** requirements.md explicitly says "a youtube
  video or any external link" — this is a link the client app opens/embeds itself, not a file
  this API stores or proxies. Optional (many exercises won't have one yet).
- **Validated with `@Pattern`, not `@URL`.** Hibernate Validator's `@URL` is on the classpath
  (via `spring-boot-starter-validation`) but its `protocol` attribute only pins one exact scheme;
  restricting to "`http` or `https`, or blank" is simpler as a single `@Pattern` — also keeps
  every validation record in this codebase using only `jakarta.validation.constraints.*`, no
  Hibernate-specific annotations, consistent with what's there today.

## 1. Goal

`Exercise` gains an optional `video_url`.

## 2. Data model

`exercises` has 3 pre-existing rows locally (confirmed live) but the column is nullable with no
backfill needed — unlike `training-plan-fields`'s `V15`, existing rows just get `NULL`, which is
already the correct "no video yet" state.

```sql
ALTER TABLE exercises ADD COLUMN video_url VARCHAR(500);
```

Migration `V17`.

## 3. Contract (`exercise.proto`)

`ExerciseResponse`/`ExerciseRequest` gain `string video_url` (next unused field number, 4 and 3
respectively).

## 4. Validation rules

`video_url`: blank or `^https?://\S+$`, `@Pattern(regexp = "^$|^https?://\\S+$")` on the existing
`ExerciseValidation` record. Malformed (wrong scheme, spaces, no host) → `INVALID_ARGUMENT`.

## 5. Mapping

`ExerciseMapper` gains `@Mapping(target = "videoUrl", qualifiedByName = "nullToEmpty")` on
`toResponse`, same as the existing `description` mapping.

## 6. Out of scope

- Validating the URL actually points to a reachable video (YouTube oEmbed lookup, HTTP HEAD,
  etc.) — format validation only.
- Multiple videos per exercise.
