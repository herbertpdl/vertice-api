# Spec: Web Access for Trainers Only (vertice-api)

Status: Implemented
Owner: hebertpdl@gmail.com
Related: `docs/prds/web-trainer-only-access/prd.md` (R10–R17, E5–E7),
`docs/assessments/web-trainer-only-access/assessment.md` (F1–F6),
`docs/specs/password-lifecycle/spec.md` (the setup token this extends),
`docs/specs/workout-session-lifecycle/spec.md` (who may start a workout log)

## 0. Scope decisions

- **Signing in on the web is not the API's rule (assessment F4).** R1–R8 are enforced by
  vertice-web-react (and the BFF decides not to add a channel notion; see its spec). The API never
  learns which app a call comes from, and a CLIENT token must keep working for the mobile app.
- **A new RPC, `RedeemPasswordSetupToken`, instead of changing `SetPasswordWithToken`'s response
  (F1, option A).** The web must tell a client's first access from a trainer's (R10, R11), and fill
  in the client's e-mail (R10a). `buf breaking` (`FILE`) refuses a changed response type, so the
  new RPC sets the password exactly like the old one and returns the token's user in the
  **contact** view (id, name, role, email), the same view `VerifyCredentials` gives the BFF at
  login. `SetPasswordWithToken` stays, marked deprecated in the proto comment; removing an RPC is
  also a breaking change, so it waits until nothing calls it.
- **The user is returned only after the token is consumed (F5).** There is no read-only "whose
  link is this" lookup (option B): that would disclose the e-mail and role of an unused token to
  anyone holding the link, for as long as it is valid. An unknown, expired or used token fails
  exactly like `SetPasswordWithToken` (`UNAUTHENTICATED` "Invalid or expired password setup
  token") and returns nothing.
- **`GetOrStartWorkoutLog` is the client's own (F2, R15; owner confirmed).** It now follows
  `RestartWorkoutLog`'s rule: the plan's client as themself, or ADMIN. A TRAINER gets
  `PERMISSION_DENIED`, so a trainer can no longer create an empty in-progress log in a client's
  week. The trainer keeps every read (`ListWorkoutLogs`, `GetLastSetLogs`, `GetWorkoutLog`,
  `ListSetLogs`). No client used the trainer path (web: none; mobile: only the client screen).
- **R12–R17 are proved by one self-enrolling test, not one test per RPC (F3).**
  `grpc/ClientRefusalCoverageTest` calls every bound RPC outside a `CLIENT_ALLOWED` list as the
  client of a real plan (the most permissive client), with every id field, nested ones included,
  filled from that plan's tree, and the other validated fields given valid values. Each call must
  be `PERMISSION_DENIED`, and afterwards the row counts of every table a refused call could write,
  and the full content of the seeded rows (an in-place update keeps the counts), must be unchanged
  (R16). User ids are filled with the client's own id, the most permissive one. A new RPC is covered the moment it is bound; one a client may call
  has to be added to `CLIENT_ALLOWED` on purpose.
- **No refusal logging (F6, Low): deferred.** Not asked for, and the BFF already logs upstream
  refusals on the request's logger.

## 1. Changes

### `user/v1/user.proto`

- `rpc RedeemPasswordSetupToken(RedeemPasswordSetupTokenRequest) returns (UserResponse)`, with
  `RedeemPasswordSetupTokenRequest { string token = 1; string password = 2; }`. Additive.
- `SetPasswordWithToken`'s comment says it is deprecated in favour of the new RPC.

### `user/`

- `PasswordSetupTokenService#setPasswordWithToken` returns the saved user as a `UserResponse`
  (same transaction: mark the token used, set the hash, save). `SetPasswordWithToken` ignores the
  result.
- `UserController#redeemPasswordSetupToken`: same validation (`TokenPasswordValidation`) and the
  same authorization (`UserAuthorization#checkSetPasswordWithToken`, `service:bff` only) as
  `setPasswordWithToken`, then `UserView.CONTACT.restrict(...)`.

### `plan/session/WorkoutSessionAuthorization`

- `checkGetOrStartWorkoutLog` uses `PlanAccess.requireClient` (action "start workout sessions")
  instead of `requireTrainerOrClient`. The `client_id` rule (`PlanAccess.ownId`) is unchanged.
- `session/v1/workout_session.proto`: `GetOrStartWorkoutLog`'s comment states the new rule.

### Docs

- CLAUDE.md: the matrix rows for `RedeemPasswordSetupToken`, `SetPasswordWithToken` and
  `GetOrStartWorkoutLog`; the contact-view row; the `ClientRefusalCoverageTest` paragraph.

## 2. Tests

- `UserControllerTest`: the new RPC returns the contact view only (no CPF or contacts) for
  `service:bff`; refuses a client, admin, `service:ops` and no token; maps an invalid token to the
  same `UNAUTHENTICATED` and message; refuses a blank token with `INVALID_ARGUMENT`.
- `PasswordSetupTokenIntegrationTest`: redeeming a client's token returns that client's id, role
  and e-mail.
- `RpcAuthorizationCoverageTest`: the new RPC is in `ALLOWED` for `service:bff`, with a valid probe
  request.
- `WorkoutSessionControllerTest`: `GetOrStartWorkoutLog` follows the client rule (client and ADMIN
  allowed; the plan's trainer, another trainer and another client denied).
- `OwnershipInvariantsIntegrationTest`: the "workout not assigned to this client" refusal is now
  reached as ADMIN (a trainer is refused earlier, by role).
- `ClientRefusalCoverageTest` (new, port 19127): as above. It was checked against a deliberately
  loosened rule (a workout write opened to the plan's client): the test failed on the two RPCs and
  on the unchanged-rows check.

## 3. Merge and deploy order

1. **vertice-api** (this change). Additive for the BFF: the new RPC is unused until the BFF calls
   it. Narrowing `GetOrStartWorkoutLog` only affects the BFF's TRAINER branch of
   `GET /workouts/:workoutId/session`, which no app calls.
2. **vertice-bff**: syncs the proto, switches `POST /auth/password/setup` to the new RPC, and
   makes the session route CLIENT-only.
3. **vertice-web-react** and **vertice-mobile**: the trainer-only web and the client's success
   screen; the mobile login reads the e-mail from the deep link.

Rollback: redeploy the previous image. There is no migration, and the new RPC is additive.
