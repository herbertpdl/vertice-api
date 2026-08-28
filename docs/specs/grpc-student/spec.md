# Spec: Student gRPC conversion

Status: Deprecated — superseded by `docs/specs/user-unification/spec.md`, which removed the
`Student` entity/`StudentService` entirely in favor of a unified `User` entity + `role`. No
`student.proto`/`StudentService` remains in the codebase.
Owner: hebertpdl@gmail.com
Related: `docs/specs/grpc-trainer/spec.md` (identical shape, mirrored here — same relationship
`student-crud/spec.md` has to `trainer-crud/spec.md`)

## 1. Goal

Exact mirror of `grpc-trainer` for Student: replace the Student REST surface with a gRPC
equivalent in this PR, not additive. `StudentController` (REST) is deleted, `student.proto`
becomes the contract, `StudentService`/`StudentMapper` retarget to proto types. After this PR,
`openapi/api.yaml` has zero paths left — `TrainingPlan`/`Exercise` never had any.

## 2. Contract: `student.proto`

`src/main/proto/vertice/student/v1/student.proto`, `option java_package =
"com.vertice.api.generated.grpc.student.v1"`. Same 6 RPCs, same naming choices as
`trainer.proto` (`StudentResponse` not bare `Student`, for the same same-package-shadowing reason
documented in `grpc-trainer/spec.md` §2 — `com.vertice.api.student.Student` is the JPA entity):

| RPC | Request | Response | REST equivalent |
|---|---|---|---|
| `ListStudents` | `ListStudentsRequest` (empty) | `ListStudentsResponse` (`repeated StudentResponse`) | `GET /api/students` |
| `GetStudent` | `GetStudentRequest{id}` | `StudentResponse` | `GET /api/students/{id}` |
| `CreateStudent` | `StudentCreateRequest{name,email,password}` | `StudentResponse` | `POST /api/students` |
| `UpdateStudent` | `UpdateStudentRequest{id, StudentRequest student}` | `StudentResponse` | `PUT /api/students/{id}` |
| `DeleteStudent` | `DeleteStudentRequest{id}` | `google.protobuf.Empty` | `DELETE /api/students/{id}` |
| `SetStudentPassword` | `SetStudentPasswordRequest{id,password}` | `google.protobuf.Empty` | `PUT /api/students/{id}/password` |

## 3. Business logic, controller, validation

Identical approach to `grpc-trainer` (see that spec's §3–5 for the full rationale, not repeated
here): `StudentService`/`StudentMapper` keep their exact signatures, only imports retargeted from
`com.vertice.api.generated.model.*` to `com.vertice.api.generated.grpc.student.v1.*`.
`StudentController` replaces the REST version in place, `extends
StudentServiceGrpc.StudentServiceImplBase`, `@GrpcService`. Validation via the same kind of small
private records `TrainerController` uses (name/email/password rules identical to the old REST
`StudentRequest`/`StudentCreateRequest`/`SetPasswordRequest` schemas). `Student` entity,
`StudentRepository`, migrations: untouched.

## 4. `api.yaml` — now empty of paths

Delete the remaining `/api/students` paths and `StudentRequest`/`StudentCreateRequest`/
`StudentResponse`/`SetPasswordRequest` schemas. `ProblemDetail` can go too — nothing references it
once both REST controllers are gone. After this PR, `api.yaml` has an empty `paths:` map and only
whatever's left in `components.schemas` (should be none). Whether to delete the file and the
`org.openapi.generator` toolchain entirely is `grpc-cleanup`'s call, not this one's — this spec
just leaves `api.yaml` in the emptied state that PR produces.

## 5. Testing

Same shape as `grpc-trainer`: `StudentServiceTest` retargeted with proto builder-style
construction, new gRPC `StudentControllerTest` mirroring the old REST test's coverage via real RPC
calls (`StudentServiceGrpc.StudentServiceBlockingStub`, `StudentService` mocked, `local` profile).

## 6. Out of scope

- `TrainingPlan`/`Exercise` — still nothing to convert.
- Deleting `api.yaml`/openapi-generator toolchain — `grpc-cleanup`.
