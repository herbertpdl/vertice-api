# Spec: Remove the now-unused REST/OpenAPI toolchain

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/grpc-trainer/spec.md` §8, `docs/specs/grpc-student/spec.md` §4/§6 (both
flagged this as a follow-up, not decided until now)

## 0. Scope decision — reconfirmed before acting

`grpc-student` left `openapi/api.yaml` with an empty `paths: {}` map: Trainer and Student (the
only two resources that ever had REST endpoints) are both gRPC-only now, and `TrainingPlan`/
`Exercise` never had any REST endpoints to begin with. There is no remaining REST consumer of
`org.openapi.generator`'s output anywhere in the codebase (confirmed via a full-repo search for
`com.vertice.api.generated.api`/`com.vertice.api.generated.model`/`TrainersApi`/`StudentsApi` —
zero hits). This PR removes the now-dead tooling. As before: **if there's an intent to give
`TrainingPlan`/`Exercise` a REST-first implementation before they too move to gRPC, this PR should
be reverted** — everything it removes would need to come back.

## 1. What stays

- `spring-boot-starter-webmvc`/`spring-boot-starter-webmvc-test`: **not removed**. Two things
  still need Spring MVC on the classpath: `GlobalExceptionHandler` (`@RestControllerAdvice`,
  still relevant if `TrainingPlan`/`Exercise` ever get a REST-shaped feature, and its exception
  classes are also used by the gRPC `@GrpcAdvice` layer) and `TrainingPlanController` — an empty
  `@RestController` stub, no endpoints yet, but still a real annotated class requiring the
  dependency to compile. Actuator's HTTP endpoints (`/actuator/health`, permitted without auth in
  `SecurityConfig`/`LocalSecurityConfig`) also need a web stack present — removing this dependency
  would silently kill that too, which is out of scope here.

## 2. What goes

- `openapi/api.yaml` — deleted, zero paths left in it.
- `org.openapi.generator` Gradle plugin (`build.gradle` `plugins {}` block) and the `openApiGenerate`
  task configuration.
- The `sourceSets { main { java { srcDir "${layout.buildDirectory...}/generated/src/main/java" } } }`
  block and `compileJava.dependsOn tasks.openApiGenerate` — both existed purely to wire the
  openapi-generator output into compilation.
- `io.swagger.core.v3:swagger-annotations` dependency — only ever used by the (now generated
  and gone) REST model/interface code, nothing hand-written references it.

## 3. Verification approach

- `./gradlew build` succeeds with the plugin/dependency gone — proves nothing in the compiled
  output actually needed it.
- `./gradlew test` stays green — the gRPC test suite (Trainer, Student, cross-cutting, foundation)
  is entirely unaffected by this change, since none of it ever depended on openapi-generated code.
- Manual: `bootRun` still starts cleanly, `/actuator/health` still reachable, both gRPC services
  still register and respond via `grpcurl`.
