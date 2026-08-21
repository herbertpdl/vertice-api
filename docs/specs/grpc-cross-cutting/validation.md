# Validation checklist: gRPC cross-cutting concerns

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Auth (section 2)

- [x] `GrpcSecurityConfig` defines two `@Profile`-gated `AuthenticationProcessInterceptor` beans —
  `grpcAuthenticationInterceptor` (`!local`) and `localGrpcAuthenticationInterceptor` (`local`)
- [x] `!local`: unauthenticated call still returns `UNAUTHENTICATED` — `GrpcHealthCheckTest`
  (from `grpc-foundation`) passes unmodified
- [x] `local`: unauthenticated call to `Health/Check` succeeds (`SERVING`) —
  `GrpcHealthCheckLocalProfileTest#check_withoutAuth_returnsServing` + manual `grpcurl -plaintext
  localhost:9090 grpc.health.v1.Health/Check` under `local` returning `{"status": "SERVING"}`
- [x] Defining our own `AuthenticationProcessInterceptor` bean(s) causes
  `GrpcServerOAuth2ResourceServerAutoConfiguration`'s defaults to back off — confirmed via clean
  boot with no duplicate-bean errors once the bean method was renamed off `grpcSecurity` (which
  collided in name, not type, with the framework's own `GrpcSecurity`-builder bean method)

## Error mapping (section 3)

- [x] `GrpcExceptionAdvice` (`@GrpcAdvice`) maps `ResourceNotFoundException` → `NOT_FOUND` —
  `GrpcExceptionMappingTest#resourceNotFoundException_mapsToNotFound`
- [x] Maps `DuplicateEmailException` → `ALREADY_EXISTS` —
  `#duplicateEmailException_mapsToAlreadyExists`
- [x] Maps `ConstraintViolationException` → `INVALID_ARGUMENT` with `field: message` description —
  `#constraintViolationException_mapsToInvalidArgumentWithFieldDetail` (asserts description
  contains the field name)
- [x] All three verified via a real RPC call to a test-only service, not direct method invocation
  — caught a real bug this way: `@GrpcExceptionHandler` methods must be `public` (package-private
  methods on the `@GrpcAdvice` bean threw `IllegalAccessException` and silently fell back to
  `INTERNAL`); fixed by making `GrpcExceptionAdvice` and its handler methods `public`

## Validation (section 4)

- [x] `GrpcRequestValidator.validate(...)` no-ops for a valid object —
  `GrpcRequestValidatorTest#validate_withValidObject_doesNotThrow`
- [x] Throws `ConstraintViolationException` for an invalid object —
  `#validate_withInvalidObject_throwsConstraintViolationException`

## Out of scope (section 5) — confirm nothing crept in

- [x] No `.proto` file or Trainer/Student business RPC added (the test-only service in
  `GrpcExceptionMappingTest` is hand-built from `google.protobuf.Empty`/`StringValue`, test-scoped
  only, not registered in the main application context)
- [x] No role/scope-based authorization added — both security beans use `allRequests()`, no
  per-method rules
- [x] No structured error details added — plain `Status` descriptions only

## Verification (section 6)

- [x] `./gradlew test` passes — 61/61 tests green (54 pre-existing + 1 from `grpc-foundation` + 6
  new here)
- [x] `grpc-foundation`'s `GrpcHealthCheckTest` passes unmodified
- [x] New local-profile test: `Health/Check` without credentials returns `SERVING`
- [x] New exception-mapping tests against the test-only `BindableService` pass for all three cases
- [x] `GrpcRequestValidator` unit test passes
- [x] Manual: `bootRun` with `local` profile, `grpcurl -plaintext localhost:9090 list` now
  succeeds (lists `Health` and `ServerReflection`) and `Health/Check` returns `SERVING` — closes
  the known gap left open by `grpc-foundation`

## Sign-off

- [x] All boxes above checked
- [x] `./gradlew test` passes
- [x] Spec and code reviewed side by side for drift — no drift; the bean-naming collision and the
  `public`-visibility requirement were implementation details, not scope changes
