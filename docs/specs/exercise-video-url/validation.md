# Validation checklist: Exercise video URL

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Data model (section 2)

- [x] `V17` migration is a plain nullable add, no backfill needed — confirmed 3 pre-existing
  local `exercises` rows just get `NULL` (already the correct "no video" state), verified by
  `./gradlew test` running the real migration
- [x] `Exercise` entity gains `videoUrl` (nullable `String`) — code read

## Contract (section 3)

- [x] `ExerciseResponse`/`ExerciseRequest` both gain `video_url`

## Validation rules (section 4)

- [x] Blank `video_url` allowed — `ExerciseControllerTest#createExercise_withBlankVideoUrl_isAllowed`
- [x] Valid `http(s)` URL accepted —
  `ExerciseControllerTest#createExercise_withValidVideoUrl_returnsCreated`
- [x] Malformed URL → `INVALID_ARGUMENT` —
  `#createExercise_withMalformedVideoUrl_throwsInvalidArgument`
- [x] Non-`http(s)` scheme (e.g. `ftp://`) → `INVALID_ARGUMENT` —
  `#createExercise_withNonHttpVideoUrl_throwsInvalidArgument`

## Mapping (section 5)

- [x] `ExerciseMapper#toResponse` maps `videoUrl` via `ProtoStrings#nullToEmpty` —
  `ExerciseServiceTest#getExercise_withNullVideoUrl_returnsEmptyStringNotNull`

## Verification

- [x] `./gradlew test` passes — 167/167 (162 before this PR + 5 new: 2 service + 3 controller,
  plus 2 existing tests extended in place to also assert `video_url` round-trips)
- [x] Spec and code reviewed side by side for drift — no drift

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
