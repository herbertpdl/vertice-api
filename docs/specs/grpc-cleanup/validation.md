# Validation checklist: Remove the now-unused REST/OpenAPI toolchain

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## What stays (section 1)

- [ ] `spring-boot-starter-webmvc`/`-test` still present in `build.gradle`
- [ ] `TrainingPlanController` still compiles (`@RestController` still resolves)
- [ ] `GlobalExceptionHandler` still compiles (`@RestControllerAdvice` still resolves)
- [ ] `/actuator/health` still reachable without auth after the change

## What goes (section 2)

- [ ] `openapi/api.yaml` deleted
- [ ] `org.openapi.generator` plugin removed from `build.gradle`
- [ ] `openApiGenerate` task config removed
- [ ] `sourceSets`/`compileJava.dependsOn` openapi wiring removed
- [ ] `io.swagger.core.v3:swagger-annotations` dependency removed
- [ ] No leftover references anywhere in the repo to `com.vertice.api.generated.api`/
  `com.vertice.api.generated.model`

## Verification (section 3)

- [ ] `./gradlew build` succeeds
- [ ] `./gradlew test` passes, full suite unaffected
- [ ] Manual: `bootRun` starts cleanly, `/actuator/health` returns 200, `grpcurl -plaintext
  localhost:9090 list` still shows both `TrainerService` and `StudentService`

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
- [ ] Spec and code reviewed side by side for drift
