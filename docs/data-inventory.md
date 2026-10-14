# Personal data inventory (vertice-api)

**Status:** Draft, **pending legal review** · **Owner:** Herbert · **Related:** audit 2026-10-09
P1-04 (COMP-API-002, COMP-API-003, COMP-WEB-001), P1-03 (per-RPC user views), P1-05 (deletion)

This is every column vertice-api stores that identifies a person or describes them, why it is
kept, on what legal basis, for how long, and who can read it through the API. The product and
legal choices in section 1 are defaults the team picked without a lawyer. They are not final legal
text. Each one is marked **pending legal review** and must be confirmed (or changed here and in
the code) before a public launch.

## 1. Decisions (all pending legal review)

| # | Decision | Where it shows in the code |
| --- | --- | --- |
| D1 | **CPF is optional for a CLIENT** (data minimisation, LGPD art. 6, III) and **required for a TRAINER** (identity, CREF checks and invoicing). **Pending legal review.** | `UserService#assertCpfRequiredForTrainer`, V27 `ck_users_trainer_cpf`, partial unique index `uq_users_cpf` |
| D2 | **Roles.** For a client's data (profile, plans, logs, feedback), the **trainer is the controller** and the **platform is the operator** that processes it on the trainer's behalf. For a trainer's own account data, the **platform is the controller**. **Pending legal review.** | Access rules: a client's data is readable by the client, their trainer and ADMIN only (CLAUDE.md matrix) |
| D3 | **Legal basis for account data:** performance of a contract (LGPD art. 7, V). **Pending legal review.** | `CreateUser` stores `accepted_terms_version` + `terms_accepted_at` |
| D4 | **Legal basis for health-related data** (loads, session logs, workout feedback): specific consent (LGPD art. 11, I), recorded per user at first access. **Pending legal review.** | `RecordConsent(HEALTH_DATA, version)` → `health_data_consent_version` + `health_data_consent_at` |
| D5 | **Retention:** account data is kept while the account is active. When the account is deleted, its personal data is deleted or anonymised (implemented by P1-05). **Pending legal review.** | Today `DeleteUser` deletes the row; P1-05 adds the full delete/anonymise path |
| D6 | **Server logs are kept 30 days.** This is an assumption about the hosting setup, not something vertice-api enforces. **Pending legal review.** | Log retention is set where the logs are shipped, not in this repo |

Consent is stored as the latest record only: a later `RecordConsent` replaces the earlier version
and time. A consent history (and withdrawal, art. 8 §5) is a follow-up, see section 4.

## 2. Who can see what

Every RPC that returns a user picks one view (`user/UserView`, P1-03). The full RPC matrix is in
`CLAUDE.md`.

| View | Fields | Who gets it |
| --- | --- | --- |
| self | everything, consent state included | the user themself (`GetUser` on their own id) |
| detail | everything except the consent state | ADMIN, the client's active trainer (`GetUser`), `UpdateUser`, `CreateClientForTrainer` |
| contact | id, name, role, email | `ListUsers` (ADMIN, `service:ops`), `CreateUser` and `VerifyCredentials` (`service:bff`) |
| summary | id, name, role | rosters and lists: `ListClientsForTrainer`, `ListUsersWithoutPassword`, `GetUser` by a former trainer |

vertice-bff narrows this further (for example it masks the CPF unless the trainer asks to reveal
it, and logs each reveal). See vertice-bff for its own rules.

## 3. Inventory by table

"Account retention" means D5: kept while the account is active, deleted or anonymised when it is
deleted (P1-05).

### `users`

| Column | Personal data | Purpose | Legal basis | Retention | Who can read it |
| --- | --- | --- | --- | --- | --- |
| `id` | identifier | links every record to the person | D3 | account | everyone who can see the user in any view |
| `name` | yes | identify the person to their trainer or clients | D3 | account | every view |
| `email` | yes | login, contact, first-access links | D3 | account | self, detail, contact |
| `cpf` | yes, national ID | TRAINER: identity, CREF checks, invoicing. CLIENT: optional, only if given (D1) | D3 | account | self, detail (masked by the BFF by default) |
| `cref` | yes, professional registration | show the trainer is a registered professional | D3 | account | self, detail |
| `password_hash` | credential (bcrypt) | authentication | D3 | account | never returned by any RPC |
| `role` | yes | authorisation | D3 | account | every view |
| `accepted_terms_version`, `terms_accepted_at` | consent record | prove which Terms / Privacy Policy version the user accepted, and when | D3 (evidence of the contract) | account | self only |
| `health_data_consent_version`, `health_data_consent_at` | consent record | prove the art. 11 consent for health-related data | D4 (evidence of the consent) | account | self only |
| `created_at`, `updated_at` | metadata | record lifecycle, audits, retention | D3 | account | not returned |

### `trainer_clients`

| Column | Personal data | Purpose | Legal basis | Retention | Who can read it |
| --- | --- | --- | --- | --- | --- |
| `trainer_id`, `client_id`, `started_at`, `ended_at` | yes: who trains whom, and since when | the trainer's roster, and the access rules built on it | D3 | account (the row cascades when the client is deleted) | the trainer (roster), ADMIN |

### `training_plans`, `workouts`, `workout_exercises`, `exercise_sets`

| Column | Personal data | Purpose | Legal basis | Retention | Who can read it |
| --- | --- | --- | --- | --- | --- |
| `training_plans.client_id`, `trainer_id` | yes: links the plan to two people | deliver the service the trainer sells | D3 | account | the plan's trainer and client, ADMIN |
| `training_plans.name`, `description`, `start_date`, `end_date`, `level` | about the client's training | same | D3 | account | same |
| `workout_exercises.notes`, `exercise_sets.notes` (free text) | can mention the client's condition | trainer's instructions | D3; D4 if they describe health | account | same |
| loads (`exercise_sets.weight`, `load_percentage`, `reps`, `duration_seconds`, `rest_seconds`) | health-related (physical capacity) | the prescribed training | D4 | account | same |

### `workout_logs`, `set_logs`, `workout_feedback`

| Column | Personal data | Purpose | Legal basis | Retention | Who can read it |
| --- | --- | --- | --- | --- | --- |
| `workout_logs.client_id`, `week_start_date`, `started_at`, `completed_at` | yes: when the client trained | progress tracking | D4 | account | the log's client, the plan's trainer, ADMIN |
| `set_logs.weight`, `reps`, `recorded_at` | health-related (performance) | progress tracking (`GetExerciseProgress`) | D4 | account | same |
| `workout_feedback.text` (free text, up to 2000 characters) | health-related: pain, injury, effort | the trainer adjusts the plan | D4 | account | the client who wrote it, their trainer (`ListWorkoutFeedback`), ADMIN |

### `exercises`

| Column | Personal data | Purpose | Legal basis | Retention | Who can read it |
| --- | --- | --- | --- | --- | --- |
| `owner_id` | links a custom exercise to its trainer | private exercise catalogue | D3 | account | the owning trainer; a client sees exercises in their own workouts |

### `password_setup_tokens`

| Column | Personal data | Purpose | Legal basis | Retention | Who can read it |
| --- | --- | --- | --- | --- | --- |
| `user_id`, `token_hash` (SHA-256), `created_at`, `expires_at`, `used_at` | yes: a first-access credential | let a user created by their trainer set a password | D3 | until replaced by a new token or the user is deleted (cascade). Expired and used rows aren't purged yet (follow-up) | never returned; the raw token is shown once to the issuer |

### Logs

vertice-api writes no personal data in its own log statements. Framework logs can carry user ids
and request metadata. Retention is D6 (30 days, assumption).

## 4. Follow-ups

- **CreateUser requires the Terms version** once vertice-bff sends it:
  `vertice.consent.terms-required-on-registration` is `false` until then, then turned on for good
  and the switch removed.
- **Consent history and withdrawal** (art. 8 §5, art. 18 IX): today only the latest consent per
  type is kept, and there is no "withdraw" RPC.
- **Deletion and anonymisation** of every table above, and data export (P1-05).
- **Purge** expired and used `password_setup_tokens` rows.
- **Legal review** of D1 to D6, and of the Terms, Privacy Policy and health-data notice texts
  whose versions are recorded here.
