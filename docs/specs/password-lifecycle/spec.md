# Spec: Password lifecycle — credential check, first-access tokens, case-insensitive emails

Status: Implemented (vertice-api slice of plan item P0-02)
Owner: hebertpdl@gmail.com
Related: Vertice health audit 2026-10-09, plan item P0-02 (findings SEC-BFF-001, PARITY-005,
REL-API-007); `docs/specs/user-unification/spec.md`

## 0. Scope decisions

- **The API checks passwords; the BFF issues tokens.** `VerifyCredentials` returns the user or
  `UNAUTHENTICATED`. Session tokens, rate limiting and the REST routes are the BFF's (P0-02 steps 3–4).
- **One failure for every bad login.** A wrong password, an unknown email and a user with no
  password all return `UNAUTHENTICATED` "Invalid email or password". The last two still run one
  bcrypt compare against a dummy hash made at startup with the same encoder, so the response time
  doesn't reveal whether the email exists. The login request itself only checks that both fields are
  present: a length rule on the password would answer differently from a wrong password.
- **A user can exist without a password** (`password_hash` NULL). `CreateClientForTrainer` may omit
  the password; that client gets a setup token instead of a random password nobody knows.
  `CreateUser` (self-registration) still requires one. Legacy `''` hashes from V5/V6 become NULL.
- **Setup tokens**: 32 random bytes, base64url (43 chars), returned once. Only the SHA-256 hex is
  stored: the token's entropy makes a salt or a slow hash pointless. Default lifetime 72h
  (`vertice.password-setup.token-ttl`, env `PASSWORD_SETUP_TOKEN_TTL`). Issuing a token deletes the
  user's unused ones. Using a token is one conditional `UPDATE … WHERE used_at IS NULL AND
  expires_at > now`, so two concurrent uses can't both win; the password is set in the same
  transaction. Unknown, expired and used tokens all return `UNAUTHENTICATED` "Invalid or expired
  password setup token" (a token is a bearer credential).
- **Password policy**: 8–72 characters on every RPC that sets a password (bcrypt reads only 72
  bytes). See the open point in §3.
- **Emails are trimmed and lower-cased on write**, matched with `lower(email) = ?`, and unique
  through an index on `lower(email)` instead of the case-sensitive `uq_users_email`. V25 refuses to
  run (listing only user ids) if existing rows collide once normalized; those must be fixed by hand.
- **`ListUsersWithoutPassword(role)`** lists users with a NULL hash, so ops/the BFF can issue setup
  tokens (P0-02 step 5). It's a separate RPC rather than a `ListUsers` flag, so access to it can
  be restricted on its own in P1-01.
- **No new authorization.** The four new RPCs are service-level, meant for vertice-bff only; the
  API doesn't authorize by role yet (P1-01).

## 1. Contract (`vertice.user.v1.UserService`)

| RPC | Request | Response | Errors |
|---|---|---|---|
| `VerifyCredentials` | `email`, `password` | `UserResponse` | `INVALID_ARGUMENT` blank email / empty password; `UNAUTHENTICATED` otherwise |
| `CreatePasswordSetupToken` | `user_id` | `token`, `expires_at` (ISO-8601) | `INVALID_ARGUMENT` id < 1; `NOT_FOUND` |
| `SetPasswordWithToken` | `token`, `password` | `Empty` | `INVALID_ARGUMENT` blank token / policy; `UNAUTHENTICATED` |
| `ListUsersWithoutPassword` | `ListUsersRequest` (`role` optional) | `ListUsersResponse` | — |

## 2. Data

- V25: collision check → normalize `users.email` → drop `uq_users_email`, add unique index
  `uq_users_email_lower` → `password_hash` nullable, `''` → NULL.
- V26: `password_setup_tokens(id, user_id → users ON DELETE CASCADE, token_hash UNIQUE,
  created_at, expires_at, used_at)` plus an index on `user_id`.

## 3. Open points / follow-ups

- Clients created before this change hold a random-UUID hash, not NULL, so
  `ListUsersWithoutPassword` doesn't list them. Step 5's migration has to target them some other
  way (for example issue tokens to every CLIENT via `ListUsers(role=CLIENT)`); that's for the BFF/ops PR.
- `@Size(max = 72)` counts characters, while bcrypt's limit is 72 *bytes*. A password of ≤72
  characters but >72 UTF-8 bytes makes `encode` throw (→ gRPC `UNKNOWN`). P1-09 (password policy)
  should check the byte length.
