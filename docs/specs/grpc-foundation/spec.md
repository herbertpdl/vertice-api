# Spec: gRPC server foundation

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`, `docs/specs/student-crud/spec.md` (the REST endpoints
this and later specs replace)

## 0. Scope decision

`vertice-bff` is moving to consume `vertice-api` over gRPC instead of REST. Since `vertice-api`
isn't in production yet, this is a straight replacement of the Trainer/Student REST surface, not a
dual-stack rollout — REST for those two resources is deleted as each gets its gRPC equivalent in
later PRs (`grpc-trainer`, `grpc-student`). `TrainingPlan`/`Exercise` are untouched: `openapi/
api.yaml` has no paths for them yet, so there's nothing to convert there.

This spec covers **only** the server foundation: getting a gRPC server running inside `vertice-api`
with health/reflection support, so later specs can add real business RPCs on top of a proven base.
No Trainer/Student business logic changes here.

## 1. Goal

`vertice-api` boots a gRPC server alongside its existing REST server, exposing the standard gRPC
health and reflection services, so `grpcurl` (or any gRPC client) can be pointed at it and confirm
the server is alive — with zero custom `.proto` files yet.

## 2. Library decision

Spring Boot 4.1 (this project is already on `4.1.0`) ships first-class gRPC server support as a
core starter — no third-party starter needed:

- `org.springframework.boot:spring-boot-starter-grpc-server` — auto-configures a Netty-based gRPC
  server (default port `9090`), the standard health service (`grpc.health.v1.Health`), and the
  reflection service, and auto-registers any bean implementing `BindableService`.
- `com.google.protobuf` Gradle plugin — generates Java/gRPC code from `.proto` files under
  `src/main/proto`. When the Spring Boot Gradle plugin detects it, it auto-configures the `protoc`
  and `protoc-gen-grpc-java` versions, so no manual version pinning for those two (mirrors how
  `compileJava.dependsOn tasks.openApiGenerate` needs no manual protoc-equivalent step for REST).
- `org.springframework.boot:spring-boot-starter-grpc-server-test` (test scope) — provides
  `@AutoConfigureTestGrpcTransport` + `@ImportGrpcClients`, an in-process test transport, the
  closest gRPC equivalent to `@WebMvcTest` for REST controller slice tests.
- Spring Security integration (`GrpcSecurity`, JWT bearer-token support) comes from the same
  starter's `spring-grpc-core` dependency and activates once `spring-security-config` is on the
  classpath — already true here via `spring-boot-starter-security` +
  `spring-boot-starter-security-oauth2-resource-server`. This is what `grpc-cross-cutting` (the
  next spec) will build the auth interceptor on top of, reusing the exact same
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` config REST already uses — no new JWT
  validation logic anywhere in this migration.

No `net.devh`/community starter needed — the official Boot 4.1 starter covers everything this
project needs.

## 3. Configuration

- `spring.grpc.server.port=9090` — explicit in `application.properties`, mirroring the explicit
  `server.port=8080` already there for REST.
- `spring.grpc.server.reflection.enabled=false` by default (main `application.properties`) —
  reflection exposes the full service/message schema to anyone who can reach the port, so it's
  off unless deliberately enabled, same instinct as `management.endpoint.health.show-details=
  when-authorized` restricting REST's actuator endpoint.
- `spring.grpc.server.reflection.enabled=true` under the `local` profile
  (`application-local.properties`, new file) — so `grpcurl -plaintext localhost:9090 list` works
  for manual testing without a running JWT issuer, mirroring `LocalSecurityConfig`'s existing
  "relax everything under `local`" pattern for REST.
- Health service stays default-enabled in all profiles (no reason to hide liveness), matching
  `/actuator/health` staying `permitAll()` in `SecurityConfig` today.

## 4. Out of scope

- Any custom `.proto` file, service, or RPC — added in `grpc-trainer`/`grpc-student`.
- The auth interceptor and exception/validation-to-`Status` mapping — added in
  `grpc-cross-cutting`.
- Any change to the REST server, `SecurityConfig`, `GlobalExceptionHandler`, or the
  `org.openapi.generator` toolchain.

## 5. Verification approach

- `./gradlew test` stays green (existing REST tests untouched).
- A new test boots the Spring context with the in-process gRPC test transport and calls the
  standard `grpc.health.v1.Health/Check` RPC, asserting `SERVING`.
- Manual: `./gradlew bootRun --args='--spring.profiles.active=local'`, then
  `grpcurl -plaintext localhost:9090 list` and
  `grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check` return successfully.
