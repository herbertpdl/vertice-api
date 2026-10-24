# Technical assessment: Web Access for Trainers Only

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/prds/web-trainer-only-access/prd.md`, `docs/specs/password-lifecycle/spec.md`
(touched: password setup), `docs/specs/workout-session-lifecycle/spec.md` (touched: who may start
a workout log), `docs/specs/user-unification/spec.md` (§3.5 deferred role-based authorization;
since done, see §3)
Spec: not yet written (will be `docs/specs/web-trainer-only-access/spec.md`)

## 1. Summary

Overall risk: Low (for vertice-api). Most of the PRD lands outside this repo. Signing in on the
web (R1–R8) is decided by vertice-bff and vertice-web-react, because the API cannot tell which
app a call comes from and should not start trying. The server guarantee (R12–R16) mostly holds
already: every trainer-only RPC requires TRAINER (or ADMIN) and refuses a CLIENT before loading
anything. The API work is three gaps:
- Setting a password returns nothing, so no app can tell a client's link from a trainer's or
  fill in the e-mail (R10, R10a, R11).
- A trainer can still start a client's workout log, which conflicts with R15.
- Client refusals are tested for only some of the trainer RPCs (R17).

| Id | Severity | One line |
|---|---|---|
| F1 | High | `SetPasswordWithToken` returns `Empty`, and `buf breaking` forbids changing its response type, so R10/R10a/R11 need a new RPC |

## 2. PRD coverage map

| Rule | Lands on | Notes |
|---|---|---|
| R1 | vertice-bff / vertice-web-react | The API stays channel-agnostic (F4). `VerifyCredentials` already returns the role (contact view, CLAUDE.md "Personal data in responses") |
| R2 | vertice-bff / vertice-web-react | Same as R1 |
| R3 | vertice-web-react | Copy only |
| R4 | vertice-bff (existing order of checks) | `VerifyCredentials` reveals the role only after the password matches, so a wrong password never discloses it (F4) |
| R5 | vertice-bff / vertice-web-react | Same as R1, for ADMIN |
| R6 | vertice-web-react | Route guard; nothing in the API |
| R7 | vertice-bff / vertice-web-react | The API re-checks the role on every call (`grpc/CallerScopeInterceptor`), but a CLIENT token is valid for a CLIENT, so ending the web sign-in is a web decision |
| R8 | none | Unchanged |
| R9 | `user/PasswordSetupTokenService.java:67` | Unchanged; the token is role-agnostic |
| R10 | new: `user/` (F1) | Needs the role after the password is set |
| R10a | new: `user/` (F1) | Needs the e-mail after the password is set; the deep link itself is web plus mobile |
| R10b | vertice-web-react | Static links |
| R10c | vertice-web-react | QR code on a computer; nothing server-side |
| R10d | vertice-web-react | The QR's store-picking link |
| R10e | vertice-web-react | Placeholder links |
| R11 | new: `user/` (F1) | Same as R10 |
| R12 | `plan/PlanAccess.java:31-35`, `plan/TrainingPlanAuthorization.java:43`, `plan/exercise/ExerciseService.java:78,87,96` | Already refused; tests only partly cover it (F3) |
| R13 | `trainerclient/TrainerClientAuthorization`, `plan/session/WorkoutSessionAuthorization.java:90-93` | Already refused; the dashboard is composed in the BFF from these RPCs |
| R14 | `trainerclient/TrainerClientAuthorization` (`CreateClientForTrainer`: "never a CLIENT") | Already refused |
| R15 | `plan/session/WorkoutSessionAuthorization.java:29-32` must change (F2); `:51-59,84-87` already refuse | Recording sets, completing and feedback are client-only today; starting a log is not |
| R16 | `plan/PlanAccess.java:16-17` | Refusal happens "before the service loads anything", so nothing is written |
| R17 | `src/test/java/...` (F3) | Partial coverage today |
| E1 | vertice-bff / vertice-web-react | See R2 |
| E2 | vertice-bff | See R4 |
| E3 | vertice-web-react | See R6 |
| E4 | vertice-bff / vertice-web-react | See R7 |
| E5 | existing authorization classes | See R12–R16 and F3 |
| E6 | new: `user/` (F1) | See R10, R10a |
| E7 | new: `user/` (F1) | See R11 |
| E8 | vertice-bff / vertice-web-react | See R5 |
| E9 | `user/UserService` (unchanged) | Global unique e-mail, `Email already in use` |
| E10 | none | Unchanged |
| E11 | vertice-web-react / vertice-mobile | Fallback links; nothing in the API |
| E12 | vertice-mobile; e-mail from F1 | The mobile app declares the `verticemobile` scheme (`vertice-mobile/app.json:8`) and has a `/login` route (`src/app/login.tsx`) |
| E13 | vertice-bff | See R4 |
| E14 | vertice-web-react | See R10c |
| E15 | vertice-web-react | See R10e |
| E16 | vertice-web-react | See R10d |

## 3. Current state

**Role authorization exists and is enforced by the API.** The baseline that "any authenticated
caller may do anything" (`docs/specs/user-unification/spec.md` §3.5) no longer holds. Every call
resolves a `grpc/Caller`, re-reads the user's role from the database, and passes through one
authorization class per aggregate. CLAUDE.md's matrix lists, per RPC, who may call it. Every
plan-tree write, exercise write, roster call and the trainer's feedback list exclude CLIENT. The
BFF adds a second layer of `requireRole` on its routes (`vertice-bff/src/plugins/authenticate.ts:33-38`).
This matches the owner's finding (PRD §7): the client account only saw the trainer screens, and
its writes were refused.

**Login.** The BFF verifies credentials through `VerifyCredentials`, which returns the user's id,
name, role and e-mail (contact view), and issues its own token carrying the role
(`vertice-bff/src/modules/auth/service.ts:25`). Nothing on either side looks at which app is
signing in.

**Password setup.** `PasswordSetupTokenService.setPasswordWithToken`
(`user/PasswordSetupTokenService.java:67-77`) marks the token used, loads the token's user and
stores the password. It already holds the `User`, but the RPC returns `google.protobuf.Empty`
(`user.proto:75`), so the caller learns nothing about whose password was set. Setup tokens are
issued by `service:ops` (the P0-02 script issues them to CLIENTs only,
`vertice-bff/src/ops/issue-setup-tokens.ts`), by ADMIN for anyone, and by a TRAINER for their
active client.

**Starting a workout log.** `checkGetOrStartWorkoutLog` (`plan/session/WorkoutSessionAuthorization.java:29-32`)
lets the plan's trainer open, and so create, this week's log for their client. Its javadoc says
this exists for the BFF's trainer view (`GET /workouts/:workoutId/session`,
`vertice-bff/src/modules/workout-sessions/routes.ts:25-61`). Neither consumer calls that view as
a trainer: the web has no caller outside its generated schema, and the mobile app calls it only
from the client's `WorkoutSessionScreen`.

## 4. Findings by dimension

### 1. PRD fit

**F2 — Medium — A trainer can start their client's workout log (R15)**
- **What**: `GetOrStartWorkoutLog` allows the plan's trainer, and starting creates a log for the
  client. R15 says a trainer who asks to start a workout as a client is refused. The owner
  confirmed during this assessment that the path should be closed.
- **Where**: `plan/session/WorkoutSessionAuthorization.java:29-32`; the BFF route's trainer branch
  at `vertice-bff/src/modules/workout-sessions/routes.ts:50-60`; CLAUDE.md matrix row
  `GetOrStartWorkoutLog`; R15.
- **Why it matters**: A trainer token, or the BFF route, can create an empty in-progress log in a
  client's week. That log then shows up in the client's "done this week" and history as a session
  the client never started.
- **Recommendation**: Narrow `GetOrStartWorkoutLog` to the plan's client (as themself) and
  ADMIN, the same as `RestartWorkoutLog` (`:35-39`). Keep the trainer's read RPCs
  (`ListWorkoutLogs`, `GetLastSetLogs`, `GetWorkoutLog`). Update the CLAUDE.md matrix row and the
  class javadoc. The BFF drops the TRAINER branch of the session route in the same change (its
  assessment owns that); nothing calls it as a trainer today, so nobody is affected.

Nothing else found. Checked: every PRD rule against the CLAUDE.md matrix and the authorization
classes. No rule conflicts with an accepted scope decision: §3.5 of user-unification deferred
role-based authorization rather than refusing it, and it has since been built.

### 2. Data model and migrations

Nothing found. Checked: no rule needs a new or changed column. Role (`users.role`) and setup
tokens already exist, and F1 returns data the service already loads.

### 3. API contract and backward compatibility

**F1 — High — Password setup returns nothing, and its response type can't be changed (R10, R10a, R11, E6, E7)**
- **What**: To show the client's "use the app" screen with the e-mail filled in, and the
  trainer's unchanged screen, the web needs the role and e-mail of the user whose password was
  just set. `SetPasswordWithToken` returns `google.protobuf.Empty`. Changing it to a response
  message would decode fine for an old BFF (no fields to misread), but `buf breaking` with `FILE`
  rules (`buf.yaml`) includes `RPC_SAME_RESPONSE_TYPE` and fails the PR.
- **Where**: `src/main/proto/vertice/user/v1/user.proto:75`; `user/UserController.java:172-178`;
  `user/PasswordSetupTokenService.java:67-77` (the `User` is already loaded at `:72-74`).
- **Why it matters**: Without it the web cannot meet R10 and R11 together. It would have to show
  one screen to every link holder, which breaks R11 for a trainer whose link an admin issued, or
  breaks R10 for clients.
- **Recommendation**: See §5. Preferred is option A: a new RPC that sets the password and returns
  the user's role and e-mail. The BFF moves to it, and `SetPasswordWithToken` stays with a
  deprecation comment, since removing an RPC also fails `buf breaking`. The response follows the
  `contact` view (CLAUDE.md "Personal data in responses"): id, name, role, e-mail. The token holder
  just set the account's password, so this is no new exposure (see F5). An invalid or used token
  keeps today's error, and it never says whose token it was.

**F4 — Info — The API cannot, and should not, tell the web from the app (R1–R8)**
- **What**: Both apps reach the API only through the BFF, with a user token that carries id and
  role and nothing about the channel. "Only trainers on the web" is therefore a BFF/web rule. It
  does not belong in the API matrix, where a CLIENT token must keep working for the mobile app.
- **Where**: `grpc/CallerIdentityResolver`; `vertice-bff/src/modules/auth/service.ts:25`; R1–R8.
- **Why it matters**: A spec that tried to enforce R1 here would need a channel claim the API
  would have to trust from the BFF anyway, and would add nothing.
- **Recommendation**: The spec states that R1–R8 are out of the API's scope and points at the BFF
  and web specs. Note for the BFF: R4 holds as long as the role check runs after
  `VerifyCredentials` succeeds, which is the only place the role is learned.

### 4. Security and privacy

**F5 — Low — The e-mail travels further for R10a**
- **What**: Under F1 the e-mail goes back to the link holder, and then into a deep link to the
  mobile app. The API side is safe: whoever holds a valid token can already take over the account
  by setting its password, and a used token returns nothing. A URL that carries an e-mail can end
  up in the OS's or a browser's history and logs, though.
- **Where**: F1's new response; R10a; `vertice-mobile/app.json:8` (custom scheme).
- **Why it matters**: The e-mail is personal data under LGPD (`docs/data-inventory.md`), and a
  URL is a new place it is stored.
- **Recommendation**: The API returns it only from the successful set-password call, never from a
  lookup of an unused token. Whether it travels in the deep link URL or is typed again is the web
  and mobile specs' decision; flag it there.

Nothing else found. Checked: R12–R14 refusals answer `PERMISSION_DENIED` with no existence
oracle (`plan/PlanAccess.java:16-17`), and R4 cannot disclose the role (F4).

### 5. Data integrity and consistency

Nothing found. Checked: refusals happen before any load or write (`plan/PlanAccess.java:16-17`,
`caller.requireRole` at the top of each `ExerciseService` write), which satisfies R16. F1's new
RPC keeps the existing single-transaction mark-used-then-save (`PasswordSetupTokenService.java:69-76`).
F2 removes a write path and adds none.

### 6. Performance and scalability

Nothing found. Checked: no new lists or queries. The per-call role lookup is one primary-key
query (`UserRepository#findRoleById`), and F1 reads a user that is already loaded.

### 7. Error handling

Nothing found. Checked: refusals map through `GrpcExceptionAdvice.java:108-111` to
`PERMISSION_DENIED`, which the BFF maps to 403. F1 reuses `InvalidPasswordSetupTokenException`.
After F2, a trainer calling `GetOrStartWorkoutLog` gets the same `PERMISSION_DENIED` shape as
`RestartWorkoutLog`.

### 8. Logging

**F6 — Low — Refused calls leave no trace**
- **What**: `handlePermissionDenied` returns the status without logging, so a client attempting
  trainer actions (E5, the situation the owner hit) is invisible in the API's logs.
- **Where**: `grpc/GrpcExceptionAdvice.java:108-111`.
- **Why it matters**: Nobody can tell whether R12–R15 refusals happen in practice, or whether a
  consumer is still trying a path F2 closed.
- **Recommendation**: Optional: one WARN per refusal with caller id, role and RPC name, and no
  ids from the request or other PII, matching the account-deletion log line's style. Rate limiting
  is not needed at this scale.

### 9. Metrics and observability

Nothing found. Checked: no new background step or dependency. A refusal counter would follow F6
if wanted, but no exporter exists.

### 10. Testing

**F3 — Medium — Client refusals are tested for only some trainer RPCs (R17)**
- **What**: R17 requires every trainer action to be checked against a client account. Tests with
  a CLIENT caller exist for:
  - `CreateExercise`/`UpdateExercise`/`DeleteExercise`/`ListExercises` (`plan/exercise/ExerciseControllerTest`)
  - `CreateTrainingPlan`
  - `UpdateWorkout` and `CreateWorkoutExercise` (`plan/PlanAuthorizationIntegrationTest.java:273-282`)
  - `ListWorkoutFeedback` (`plan/session/WorkoutFeedbackControllerTest.java:163-168`)
  - the roster RPCs (`trainerclient/TrainerClientAuthorizationIntegrationTest.java:130`)

  No test calls these with a CLIENT token:
  - `UpdateTrainingPlan`, `DeleteTrainingPlan`
  - `CreateWorkout`, `CreateWorkoutWithExercises`, `DeleteWorkout`
  - `ReplaceWorkoutExercises`, `UpdateWorkoutExercise`, `DeleteWorkoutExercise`
  - `CreateExerciseSet`, `UpdateExerciseSet`, `DeleteExerciseSet`
  - `CloneWorkout`

  The code refuses all of them through `PlanAccess.requireTrainer`, but nothing pins that down.
  `grpc/RpcAuthorizationCoverageTest` covers no-token and service callers only.
- **Where**: `src/test/java/com/vertice/api/grpc/RpcAuthorizationCoverageTest.java`; the files
  above; R12, R16, R17.
- **Why it matters**: R17 is the PRD's done-criterion for the server guarantee. A future
  authorization class that forgets `requireTrainer` on one RPC would pass CI.
- **Recommendation**: Add one role-matrix integration test, in the style of
  `RpcAuthorizationCoverageTest`, that calls every RPC whose matrix row excludes CLIENT with a
  real CLIENT token. It should include the plan's own client, the most permissive case. Assert
  `PERMISSION_DENIED`, and that the plan tree is unchanged afterwards (R16). Make it
  self-enrolling like the coverage test, so a new trainer-only RPC is caught automatically. Add
  the mirror case for R15: a TRAINER on `GetOrStartWorkoutLog` (after F2), `RecordSetLog`,
  `CompleteWorkoutLog` and `SubmitWorkoutFeedback`.

### 11. Architecture fit and maintainability

Nothing found. Checked: F1 belongs in `user/` next to `SetPasswordWithToken`, with its rule in
`UserAuthorization` (`service:bff`, like `checkSetPasswordWithToken` at `:195`). F2 is a one-method
change in `WorkoutSessionAuthorization`. The test in F3 sits beside `RpcAuthorizationCoverageTest`
in `grpc/`.

### 12. Operability and rollout

Nothing found beyond §7. Checked: no migration and no new configuration. The owner confirmed the
product is not in production (PRD §7), so there are no live sessions or consumers to bridge.

### 13. Effort, risk, and dependencies

See §8.

## 5. Options

For F1 (who the set-password link was for):

- **A. New RPC that sets the password and returns the user (contact view).** Resolves F1. Costs
  one RPC, one authorization rule and tests, plus a BFF switch. It is additive and passes
  `buf breaking`. The e-mail is returned only after a successful set (F5).
- **B. A read-only "who is this link for" RPC called before the form.** Resolves F1 and lets the
  web tailor the page before submitting. Costs one RPC, but it discloses the e-mail and role of an
  *unused* token to anyone holding the link. That raises F5 and makes the token a lookup oracle
  for as long as it is valid.
- **C. No API change: the web treats every setup link as a client's.** No cost here. It breaks
  R11 whenever an admin issues a trainer's link, and gives no e-mail for R10a.

Recommendation: **A**. It is the smallest change that meets R10, R10a and R11, it keeps the
e-mail behind the successful use of the token, and it leaves the old RPC in place for the
transition.

## 6. Testing strategy

- F1: controller test for the new RPC: `service:bff` succeeds and returns the role and e-mail for
  a CLIENT's token and for a TRAINER's token. A user caller or `service:ops` is refused. A used or
  unknown token is refused and returns no user. It enrolls in `RpcAuthorizationCoverageTest`'s
  `ALLOWED` for `service:bff`. Service test: the response is the token's user, and nothing is
  returned when `markUsed` affects zero rows.
- F2: controller and integration tests showing that a TRAINER on `GetOrStartWorkoutLog` gets
  `PERMISSION_DENIED` and no log is created. The plan's client and ADMIN still succeed. Update
  existing tests that start a log as a trainer: `OwnershipInvariantsIntegrationTest.java:179`
  calls `startLog(trainerSession, ...)`.
- F3: the role-matrix test described in F3, covering R12, R13, R14, R16, and R15's mirror.

## 7. Rollout

- F1 is additive: ship the API first, then the BFF switches to the new RPC. The old RPC stays,
  deprecated.
- F2 removes a path. Ship the BFF change that drops the session route's TRAINER branch in the
  same release, or before the API. Neither app uses it as a trainer, and the product is not in
  production, so ordering risk is minimal.
- No migration, no configuration change. Rollback is redeploying the previous image.

## 8. Effort and risk

Size: **S**. One new RPC in `user/`, a one-rule narrowing in `WorkoutSessionAuthorization`, and
one table-driven test, with no schema change. The main risk is the role-matrix test (F3)
surfacing an RPC that does not refuse a CLIENT. The current code reading says it will not, but
that is what R17 asks to prove. Dependency: the BFF and web assessments, for R1–R8, R10a and
R10b, the session route, and the mobile deep link (E12).

## 9. Questions and assumptions

None. Resolved during this assessment: the owner chose to close the trainer's ability to start a
client's workout log (F2). From the PRD: not in production, so no transition is needed.

## 10. Inputs to the spec

- [ ] F1: how the BFF learns the role and e-mail of the user whose password was just set (new RPC
  per §5 option A, or a stated alternative), and what happens to `SetPasswordWithToken`.
- [ ] F2: narrowing `GetOrStartWorkoutLog` to the plan's client and ADMIN, and the matching BFF
  route change.
- [ ] F3: the role-matrix test that proves R12–R17, and whether it self-enrolls new RPCs.
- [ ] F4: a scope statement that R1–R8 are enforced by the BFF and web, not the API.
