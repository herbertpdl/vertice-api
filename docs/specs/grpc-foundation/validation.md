# Validation checklist: gRPC server foundation

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Library decision (section 2)

- [ ] `org.springframework.boot:spring-boot-starter-grpc-server` added, no `net.devh`/other
  community starter pulled in
- [ ] `com.google.protobuf` Gradle plugin added; `protoc`/`protoc-gen-grpc-java` versions are
  auto-configured by the Spring Boot Gradle plugin (no manual version pin needed)
- [ ] `spring-boot-starter-grpc-server-test` added as `testImplementation`
- [ ] Build succeeds with no custom `.proto` files present yet

## Configuration (section 3)

- [ ] `spring.grpc.server.port=9090` set explicitly in `application.properties`
- [ ] `spring.grpc.server.reflection.enabled=false` by default (main `application.properties`)
- [ ] `spring.grpc.server.reflection.enabled=true` under `local` (`application-local.properties`)
- [ ] Health service (`grpc.health.v1.Health`) responds `SERVING` in all profiles

## Out of scope (section 4) — confirm nothing crept in

- [ ] No custom `.proto`/service/RPC added
- [ ] No auth interceptor or exception mapping added
- [ ] `SecurityConfig`, `GlobalExceptionHandler`, `openapi/api.yaml`, REST controllers untouched

## Verification (section 5)

- [ ] `./gradlew test` passes, including existing REST test suites unchanged
- [ ] New test using the in-process gRPC test transport calls `Health/Check` and asserts `SERVING`
- [ ] Manual: `bootRun` with `local` profile, `grpcurl -plaintext localhost:9090 list` and
  `grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check` both succeed

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
- [ ] Spec and code reviewed side by side for drift
