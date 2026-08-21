# Validation checklist: Remove the now-unused REST/OpenAPI toolchain

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## What stays (section 1)

- [x] `spring-boot-starter-webmvc`/`-test` still present in `build.gradle`
- [x] `TrainingPlanController` still compiles (`@RestController` still resolves)
- [x] `GlobalExceptionHandler` still compiles (`@RestControllerAdvice` still resolves)
- [x] `/actuator/health` still reachable without auth after the change — manual `curl` returns 200

## What goes (section 2)

- [x] `openapi/api.yaml` deleted (and the now-empty `resources/openapi/` directory)
- [x] `org.openapi.generator` plugin removed from `build.gradle`
- [x] `openApiGenerate` task config removed
- [x] `sourceSets`/`compileJava.dependsOn` openapi wiring removed
- [x] `io.swagger.core.v3:swagger-annotations` dependency removed
- [x] No leftover references anywhere in the repo to `com.vertice.api.generated.api`/
  `com.vertice.api.generated.model` — confirmed via repo-wide grep before starting (zero hits)

## Verification (section 3)

- [x] `./gradlew clean build` succeeds
- [x] `./gradlew test` passes, full suite unaffected — 59/59, same as before this PR
- [x] Manual: `bootRun` starts cleanly under `local`, `/actuator/health` returns 200,
  `grpcurl -plaintext localhost:9090 list` still shows both `vertice.trainer.v1.TrainerService`
  and `vertice.student.v1.StudentService`

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
- [x] Spec and code reviewed side by side for drift — no drift
