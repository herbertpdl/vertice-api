# Spec: Trainer gRPC conversion

Status: Deprecated — superseded by `docs/specs/user-unification/spec.md`, which removed the
`Trainer` entity/`TrainerService` entirely in favor of a unified `User` entity + `role`. No
`trainer.proto`/`TrainerService` remains in the codebase.
Owner: hebertpdl@gmail.com
Related: `docs/specs/trainer-crud/spec.md`, `docs/specs/password-storage/spec.md` (the REST surface
this spec replaces), `docs/specs/grpc-foundation/spec.md`, `docs/specs/grpc-cross-cutting/spec.md`
(the infra this builds on)

## 1. Goal

Replace every Trainer REST endpoint with a gRPC equivalent, in the same PR — not additive. `vertice-api`
isn't in production, so there's no dual-stack transition period for this resource: `TrainerController`
(REST) is deleted, `trainer.proto` becomes the contract, and `TrainerService`/`TrainerMapper` are
retargeted to the new proto message types instead of the openapi-generated REST models.

## 2. Contract: `trainer.proto`

`src/main/proto/vertice/trainer/v1/trainer.proto`, `option java_package =
"com.vertice.api.generated.grpc.trainer.v1"`. One `TrainerService` with all 6 RPCs mirroring
today's REST surface exactly:

| RPC | Request | Response | REST equivalent |
|---|---|---|---|
| `ListTrainers` | `ListTrainersRequest` (empty) | `ListTrainersResponse` (`repeated TrainerResponse`) | `GET /api/trainers` |
| `GetTrainer` | `GetTrainerRequest{id}` | `TrainerResponse` | `GET /api/trainers/{id}` |
| `CreateTrainer` | `TrainerCreateRequest{name,email,password}` | `TrainerResponse` | `POST /api/trainers` |
| `UpdateTrainer` | `UpdateTrainerRequest{id, TrainerRequest trainer}` | `TrainerResponse` | `PUT /api/trainers/{id}` |
| `DeleteTrainer` | `DeleteTrainerRequest{id}` | `google.protobuf.Empty` | `DELETE /api/trainers/{id}` |
| `SetTrainerPassword` | `SetTrainerPasswordRequest{id,password}` | `google.protobuf.Empty` | `PUT /api/trainers/{id}/password` |

`TrainerResponse{id,name,email}` and `TrainerRequest{name,email}` mirror the REST schemas of the
same name exactly (never a password/hash field, same as REST). `UpdateTrainerRequest` nests
`TrainerRequest` rather than flattening its fields, so `TrainerService.updateTrainer(Long id,
TrainerRequest request)` keeps taking exactly the two parameters it takes today — id from the
wrapper, body from the nested message — no service signature change beyond the import.

Naming note: the message is `TrainerResponse`, not bare `Trainer` — the JPA entity `Trainer`
already occupies that simple name in the `com.vertice.api.trainer` package where the mapper/service
live; naming the proto message the same as the REST response schema avoids a shadowing import
(single-type imports silently shadow same-package types with the same simple name in Java, so
`import ...generated.grpc.trainer.v1.Trainer` would make every unqualified `Trainer` in the file
refer to the proto message, not the entity).

## 3. Business logic: unchanged, just retargeted

`TrainerService` (`assertEmailAvailable`, `findByIdOrThrow`, password hashing via the existing
`PasswordEncoder`) and `TrainerMapper` (MapStruct, entity ↔ request/response) keep their exact
method signatures and bodies — only the imports change, from
`com.vertice.api.generated.model.*` to `com.vertice.api.generated.grpc.trainer.v1.*`. MapStruct
1.6.3 (already a dependency) generates builder-pattern code
(`TrainerResponse.newBuilder().setId(...).build()`) automatically for the entity→proto direction,
since protobuf messages have no public constructor — no extra MapStruct config needed for this.

`Trainer` entity, `TrainerRepository`, and the `V1__create_trainers_table.sql`/
`V5__add_password_hash_to_trainers.sql` migrations: untouched.

## 4. Controller: `TrainerController` (gRPC)

Replaces the REST controller in place (same file, same package, same class name) — no more
`implements TrainersApi`, now `extends TrainerServiceGrpc.TrainerServiceImplBase`, annotated
`@GrpcService` (`org.springframework.grpc.server.service.GrpcService`, auto-registers as a
`BindableService`, discovered while implementing `grpc-foundation`). Each RPC method: validate
(section 5) where the RPC has a body → delegate to the *same* `TrainerService` → wrap the response
in a `StreamObserver.onNext()`/`onCompleted()` call. Errors (`ResourceNotFoundException`,
`DuplicateEmailException`, validation failures) are handled by `grpc-cross-cutting`'s
`GrpcExceptionAdvice` — nothing service- or Trainer-specific to add there.

## 5. Validation: proto messages can't carry Bean Validation annotations

**Correction to `grpc-cross-cutting`'s assumption**: that spec described calling
`GrpcRequestValidator.validate(request)` directly on the mapped proto request, but protobuf-
generated classes are generated code — there's nowhere to put `@NotBlank`/`@Email`/`@Size`
annotations on them. `GrpcRequestValidator` itself is still correct as built (it just wraps a real
`jakarta.validation.Validator`, proven by its own test using a plain annotated record); the gap is
that no caller before this one had proto fields to validate.

Fix, discovered here: `TrainerController` defines small private validation records next to each
RPC that needs one, carrying the exact same rules the REST `TrainerRequest`/`TrainerCreateRequest`/
`SetPasswordRequest` schemas had in `api.yaml` (`name`: `@NotBlank`; `email`: `@NotBlank @Email`;
`password`: `@NotBlank @Size(min = 8)`), constructed from the proto request's fields immediately
before calling `GrpcRequestValidator.validate(...)`:

```java
private record CreateValidation(@NotBlank String name, @NotBlank @Email String email,
                                 @NotBlank @Size(min = 8) String password) { }
```

This is per-controller, not a shared framework addition — `grpc-student` will define its own
identically-shaped records, same as `TrainerCreateRequest`/`StudentCreateRequest` were separately
declared (not shared) in `api.yaml` today.

## 6. `api.yaml` / REST removal

Delete the `/api/trainers` paths, `TrainerRequest`/`TrainerCreateRequest`/`TrainerResponse`
schemas, and `TrainersApi`-implementing `TrainerController` (REST version). Leave
`SetPasswordRequest`/`ProblemDetail` in `api.yaml` — Student still uses them until `grpc-student`
lands. Delete `TrainerControllerTest` (MockMvc-based, no longer applicable).

## 7. Testing

- `TrainerServiceTest`: same assertions, imports retargeted to the new proto types (already proven
  workable — proto request objects support the same all-args-style construction the openapi models
  did, just via `TrainerCreateRequest.newBuilder().setName(...).build()` instead of a constructor).
- New `TrainerControllerTest` (gRPC): mirrors the REST version's coverage using a real in-process-
  style RPC call (matching `grpc-cross-cutting`'s testing approach) — `local` profile to skip auth,
  covering all 6 RPCs' success + not-found/duplicate/validation failure paths.

## 8. Out of scope

- `TrainingPlan`/`Exercise` — no REST endpoints exist for them yet, nothing to convert.
- Retiring `openapi/api.yaml`/the openapi-generator toolchain entirely — that's `grpc-cleanup`,
  after `grpc-student` empties the file completely.
