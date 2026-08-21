# Validation checklist: gRPC server foundation

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Library decision (section 2)

- [x] `org.springframework.boot:spring-boot-starter-grpc-server` added, no `net.devh`/other
  community starter pulled in — `build.gradle`
- [x] `com.google.protobuf` Gradle plugin added; `protoc`/`protoc-gen-grpc-java` versions are
  auto-configured by the Spring Boot Gradle plugin (no manual version pin needed) — confirmed via
  successful `generateProto`/`compileJava` with no explicit `protoc`/grpc plugin artifact version
  set in `protobuf { }`
- [x] `spring-boot-starter-grpc-server-test` added as `testImplementation`
- [x] Build succeeds with no custom `.proto` files present yet — `./gradlew build` green
- [x] One placeholder `BindableService` bean (`GrpcHealthConfig`) needed to satisfy Boot's
  `@ConditionalOnBean(BindableService.class)` gate on the server/reflection autoconfiguration —
  documented as a discovery in spec section 2, to be deleted once `grpc-trainer` adds a real one

## Configuration (section 3)

- [x] `spring.grpc.server.port=9090` set explicitly in `application.properties`
- [x] `spring.grpc.server.reflection.enabled=false` by default (main `application.properties`)
- [x] `spring.grpc.server.reflection.enabled=true` under `local` (`application-local.properties`)
- [x] Health service (`grpc.health.v1.Health`) registers in all profiles — confirmed via `bootRun`
  log line `Registered gRPC service: grpc.health.v1.Health`
- [x] Known gap recorded: gRPC auth is *not* bypassed under `local` (unlike REST) — confirmed via
  manual `grpcurl -plaintext localhost:9090 list` under `local` returning `Unauthenticated`

## Out of scope (section 4) — confirm nothing crept in

- [x] No custom `.proto`/service/RPC added (the placeholder `BindableService` is hand-written
  Java, not proto-generated, and carries zero RPCs)
- [x] No auth interceptor or exception mapping added — the `UNAUTHENTICATED` behavior verified
  here is 100% Spring Boot's own default once the resource-server starter is on the classpath, no
  code written for it in this PR
- [x] `SecurityConfig`, `GlobalExceptionHandler`, `openapi/api.yaml`, REST controllers untouched

## Verification (section 5)

- [x] `./gradlew test` passes, including existing REST test suites unchanged — 57/57 tests green
  (56 previous + 1 new)
- [x] `GrpcHealthCheckTest#check_withoutAuth_returnsUnauthenticated` boots the real Netty gRPC
  server on a fixed test port and asserts `Status.Code.UNAUTHENTICATED` calling `Health/Check`
  without credentials
- [x] Manual: `bootRun` with `local` profile — log confirms `gRPC Server started, listening on
  address: ... port: 9090` and all three services registered (placeholder, reflection, health);
  `grpcurl -plaintext localhost:9090 list` returns `Unauthenticated` as expected per the known gap

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
- [x] Spec and code reviewed side by side for drift — spec.md updated in place with the two
  real-world discoveries (BindableService gate, gRPC auth auto-activation) rather than left to
  drift from what was actually built
