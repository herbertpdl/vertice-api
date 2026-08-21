# Spec: gRPC cross-cutting concerns (auth + error/validation mapping)

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/specs/grpc-foundation/spec.md` (discoveries this spec builds on)

## 0. Scope decision

`grpc-foundation` discovered that Spring Boot's gRPC starter already auto-activates JWT
authentication for every RPC as soon as `spring-boot-starter-security-oauth2-resource-server` is
on the classpath — no code required. So this spec is **not** "build a gRPC auth interceptor from
scratch"; it's two narrower things: (1) give gRPC the same `local`-profile bypass REST already has
via `LocalSecurityConfig`, which the auto-configured default doesn't know about, and (2) map our
own domain exceptions (`ResourceNotFoundException`, `DuplicateEmailException`, Bean Validation
failures) to gRPC `Status` codes, the equivalent of `GlobalExceptionHandler` for REST. No Trainer/
Student business RPCs yet — `grpc-trainer`/`grpc-student` are the first callers of the validation
helper built here.

## 1. Goal

Any future gRPC service (`grpc-trainer`, `grpc-student`, ...) gets auth and error handling for
free by doing nothing beyond throwing the same exceptions the REST layer already throws, and
calling a shared validator before delegating to the business service — exactly mirroring how
`TrainerController`/`TrainerService` don't do anything special today for `GlobalExceptionHandler`
or `@Valid` to kick in.

## 2. Auth: local-profile bypass

**Discovery** (see `grpc-foundation` spec §2): Boot's default gRPC security wiring is
`GrpcServerOAuth2ResourceServerAutoConfiguration`, which is itself
`@ConditionalOnMissingBean(AuthenticationProcessInterceptor.class)` — defining our own bean of
that type makes the default back off entirely, letting us fully control it via the same
`GrpcSecurity` builder the default uses internally.

Two `@Profile`-gated beans in a new `GrpcSecurityConfig` (`com.vertice.api.grpc`), mirroring
`SecurityConfig`/`LocalSecurityConfig` exactly:

- `@Profile("!local")`: `grpc.authorizeRequests(r -> r.allRequests().authenticated())
  .oauth2ResourceServer(rs -> rs.jwt(withDefaults()))` — functionally identical to today's
  default, just now explicit and ours to extend later (no role/scope differentiation, per
  `trainer-crud`/`student-crud` §7 — "any authenticated caller may perform any operation").
- `@Profile("local")`: `grpc.authorizeRequests(r -> r.allRequests().permitAll())` — no JWT
  needed, mirroring `LocalSecurityConfig`'s `.anyRequest().permitAll()` for REST.

No role/method-level differentiation in either case — same flat model REST uses today.

**Discovered while implementing**: the `@Bean` method name matters, not just the type — naming a
bean method `grpcSecurity()` collides with the framework's own `GrpcNativeSecurityConfigurerConfiguration.
grpcSecurity()` bean (which produces the `GrpcSecurity` builder, a different type, but Spring
resolves bean names per method name regardless of type), throwing `BeanDefinitionOverrideException`
at boot. Renamed ours to `grpcAuthenticationInterceptor`/`localGrpcAuthenticationInterceptor`.

## 3. Error mapping

A `@GrpcAdvice`-annotated `GrpcExceptionAdvice` (`com.vertice.api.grpc`) with
`@GrpcExceptionHandler`-annotated methods — the gRPC-native equivalent of
`@RestControllerAdvice`/`@ExceptionHandler`, reusing the **same** exception classes
`GlobalExceptionHandler` already handles for REST (`com.vertice.api.common.exception`, unchanged):

| Exception | REST (existing) | gRPC (this spec) |
|---|---|---|
| `ResourceNotFoundException` | 404 `ProblemDetail` | `Status.NOT_FOUND` |
| `DuplicateEmailException` | 409 `ProblemDetail` | `Status.ALREADY_EXISTS` |
| `jakarta.validation.ConstraintViolationException` | (n/a — REST gets `MethodArgumentNotValidException` from `@Valid`, 422) | `Status.INVALID_ARGUMENT`, description lists `field: message` per violation, same shape as the REST `errors[]` array |

`Status.Code.UNAUTHENTICATED`/`PERMISSION_DENIED` for Spring Security exceptions are already
handled by the framework's own `SecurityGrpcExceptionHandler` (discovered while implementing —
this is why `grpc-foundation`'s smoke test already got a clean `UNAUTHENTICATED` with zero code
written). Nothing to add there.

**Discovered while implementing**: `@GrpcExceptionHandler`-annotated methods on the `@GrpcAdvice`
bean must be `public` — package-private methods compile fine but throw `IllegalAccessException` at
dispatch time (caught internally, silently falling back to a generic `INTERNAL` instead of the
mapped status), only visible by reading the server log. `GrpcExceptionAdvice` and its three handler
methods are `public` for this reason.

## 4. Validation

REST gets `@Valid`-triggered Bean Validation for free because Spring MVC validates
`@RequestBody`-annotated generated model params automatically; gRPC handler methods build their
request objects by hand from proto fields, so nothing validates them unless we do it explicitly.

A `GrpcRequestValidator` bean (`com.vertice.api.grpc`) wraps the existing `jakarta.validation.
Validator` (already on the classpath via `spring-boot-starter-validation`, same bean REST's
`@Valid` uses under the hood) with one method: `validate(T request)`, throwing
`ConstraintViolationException` if any constraint is violated. Future gRPC controllers
(`grpc-trainer`, `grpc-student`) call this once per RPC on the mapped request object, immediately
before delegating to `TrainerService`/`StudentService` — same spot `@Valid` would run for REST.

## 5. Out of scope

- Any Trainer/Student business RPC or `.proto` file — first one lands in `grpc-trainer`.
- Role/scope-based authorization (matches REST's current flat model).
- Structured error details (e.g. `google.rpc.BadRequest`) beyond a plain description string —
  can revisit if a client needs machine-parseable field errors later.

## 6. Verification approach

- `./gradlew test` stays green (existing REST + `grpc-foundation` tests untouched).
- `GrpcHealthCheckTest` (from `grpc-foundation`) still passes unmodified — proves the new explicit
  `!local` bean behaves identically to the default it replaces.
- A new test under `@ActiveProfiles("local")` calls `Health/Check` with no credentials and asserts
  `SERVING` — finally exercises the "does the server actually serve traffic" case `grpc-foundation`
  couldn't reach yet.
- A small test-only `BindableService` (registered via `@TestConfiguration`, no `.proto` needed —
  built directly from `com.google.protobuf.StringValue`/`Empty`, already on the classpath) throws
  each of the three exceptions above on demand; tests call it under `local` and assert the
  resulting `Status.Code` and description match section 3's table.
- `GrpcRequestValidator` gets a direct unit test (valid object passes, invalid throws
  `ConstraintViolationException`).
