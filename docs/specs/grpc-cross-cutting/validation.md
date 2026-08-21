# Validation checklist: gRPC cross-cutting concerns

Run through this after implementation, against `spec.md` in this folder. Check off only items
that are actually verified (test run, manual grpcurl, or code read) — not assumed.

## Auth (section 2)

- [ ] `GrpcSecurityConfig` defines two `@Profile`-gated `AuthenticationProcessInterceptor` beans
- [ ] `!local`: unauthenticated call still returns `UNAUTHENTICATED` (existing `GrpcHealthCheckTest`
  passes unmodified)
- [ ] `local`: unauthenticated call to `Health/Check` succeeds (`SERVING`)
- [ ] Defining our own `AuthenticationProcessInterceptor` bean(s) causes
  `GrpcServerOAuth2ResourceServerAutoConfiguration`'s defaults to back off (no duplicate-bean or
  duplicate-interceptor errors at boot)

## Error mapping (section 3)

- [ ] `GrpcExceptionAdvice` (`@GrpcAdvice`) maps `ResourceNotFoundException` → `NOT_FOUND`
- [ ] Maps `DuplicateEmailException` → `ALREADY_EXISTS`
- [ ] Maps `ConstraintViolationException` → `INVALID_ARGUMENT` with `field: message` description
- [ ] All three verified via a real RPC call to a test-only service (not just direct method
  invocation) — proves the `@GrpcAdvice` dispatch mechanism itself works, not just the handler
  method bodies

## Validation (section 4)

- [ ] `GrpcRequestValidator.validate(...)` no-ops for a valid object
- [ ] Throws `ConstraintViolationException` for an invalid object

## Out of scope (section 5) — confirm nothing crept in

- [ ] No `.proto` file or Trainer/Student business RPC added
- [ ] No role/scope-based authorization added
- [ ] No structured error details (`google.rpc.BadRequest` etc.) added

## Verification (section 6)

- [ ] `./gradlew test` passes, including `grpc-foundation`'s `GrpcHealthCheckTest` unmodified
- [ ] New local-profile test: `Health/Check` without credentials returns `SERVING`
- [ ] New exception-mapping tests against the test-only `BindableService` pass for all three cases
- [ ] `GrpcRequestValidator` unit test passes

## Sign-off

- [ ] All boxes above checked
- [ ] `./gradlew test` passes
- [ ] Spec and code reviewed side by side for drift
