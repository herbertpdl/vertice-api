-- P2-02 (REL-API-008, XREPO-006): every column mapped to a Java `Instant` becomes TIMESTAMPTZ, so
-- a point in time is stored as a point in time instead of a bare wall-clock reading whose zone is
-- a convention nothing enforces.
--
-- Why this rather than pinning the JVM/Hibernate zone: Hibernate already binds an `Instant` as UTC
-- wall clock regardless of the JVM zone (verified in BusinessTimeZoneIntegrationTest), so
-- `hibernate.jdbc.time_zone=UTC` would change nothing. What *is* zone-dependent is the two DB-side
-- `DEFAULT now()` clauses below: `now()` is TIMESTAMPTZ and casting it into TIMESTAMP uses the
-- session's TimeZone, which pgjdbc sets from the JVM's. On a JVM in America/Sao_Paulo those
-- defaults wrote local wall clock into a column every reader interprets as UTC — a silent 3-hour
-- error. `-Duser.timezone=UTC` would only hide that on a correctly-configured host; TIMESTAMPTZ
-- removes the ambiguity outright, and keeps it removed for raw SQL, psql sessions and future
-- defaults.
--
-- Existing rows are reinterpreted, not rewritten: `USING col AT TIME ZONE 'UTC'` labels each
-- stored wall-clock reading with the zone it already meant, so every instant keeps its value. The
-- `AT TIME ZONE 'UTC'` clause is load-bearing — a bare `ALTER COLUMN ... TYPE TIMESTAMPTZ` uses the
-- *session's* TimeZone, which would shift every historic row by the migrating client's offset.
-- Date-only business columns (`training_plans.start_date`/`end_date`, `workout_logs.week_start_date`)
-- stay DATE: they are local calendar days, not instants, and DATE carries no zone to get wrong.
--
-- DEPLOY IMPACT — read before running this against production.
--   * Each ALTER COLUMN ... TYPE takes ACCESS EXCLUSIVE on its table and fully rewrites it (the
--     relfilenode changes); no read or write of that table proceeds meanwhile.
--   * Flyway runs this file in a single transaction, so the locks are NOT released table by table:
--     every one is held until the last rewrite commits. The window therefore covers `users`,
--     `workout_logs`, `set_logs`, `workout_feedback`, `password_setup_tokens` and
--     `trainer_clients` at once — in practice the whole API is down for the duration.
--   * Size the window first. `set_logs` and `workout_logs` are the tables that grow without bound:
--     check their row counts and `pg_total_relation_size` before deploying, and schedule
--     accordingly. At current volumes this is seconds; it does not stay that way for ever.
--   * Set `lock_timeout` on the deploying session so this queues behind, rather than blocks, live
--     traffic: a pending ACCESS EXCLUSIVE also stalls every later reader of the same table.
--   * NOT idempotent if re-run by hand. A second pass over an already-converted column computes
--     `timestamptz AT TIME ZONE 'UTC'` → bare timestamp → back to timestamptz via the *session's*
--     zone, shifting every instant by that offset. Flyway's checksum makes this impossible through
--     normal channels; an incident re-run applying the file manually is the real risk.

ALTER TABLE workout_logs
    ALTER COLUMN started_at   TYPE TIMESTAMPTZ USING started_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE set_logs
    ALTER COLUMN recorded_at TYPE TIMESTAMPTZ USING recorded_at AT TIME ZONE 'UTC';

ALTER TABLE workout_feedback
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE password_setup_tokens
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN used_at    TYPE TIMESTAMPTZ USING used_at    AT TIME ZONE 'UTC';

-- The partial indexes on `ended_at IS NULL` (uq_trainer_clients_active_client,
-- idx_trainer_clients_trainer_active) are rebuilt by ALTER TYPE; the predicate is a null check, so
-- it stays valid.
ALTER TABLE trainer_clients
    ALTER COLUMN started_at TYPE TIMESTAMPTZ USING started_at AT TIME ZONE 'UTC',
    ALTER COLUMN ended_at   TYPE TIMESTAMPTZ USING ended_at   AT TIME ZONE 'UTC';

ALTER TABLE users
    ALTER COLUMN terms_accepted_at      TYPE TIMESTAMPTZ USING terms_accepted_at      AT TIME ZONE 'UTC',
    ALTER COLUMN health_data_consent_at TYPE TIMESTAMPTZ USING health_data_consent_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at             TYPE TIMESTAMPTZ USING created_at             AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at             TYPE TIMESTAMPTZ USING updated_at             AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at             TYPE TIMESTAMPTZ USING deleted_at             AT TIME ZONE 'UTC';

-- Defensive, and a no-op on PostgreSQL 12+: ALTER TYPE re-derives a column default against the new
-- type, so the implicit `now()`-into-TIMESTAMP cast is dropped and the default is already a plain
-- TIMESTAMPTZ `now()` by this point — measured on PG 16, where the ALTER TYPEs above *alone* leave a
-- default that stores the true instant from a UTC+14 session (asserted by
-- InstantColumnsTimestamptzMigrationTest). These three statements only make the intended default
-- explicit rather than inherited from that re-derivation. Don't cite them as load-bearing; the
-- `USING ... AT TIME ZONE 'UTC'` clauses above are the part that is.
-- (Hibernate supplies all three columns on insert anyway; the defaults matter only to raw SQL.)
ALTER TABLE trainer_clients ALTER COLUMN started_at SET DEFAULT now();
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT now();
