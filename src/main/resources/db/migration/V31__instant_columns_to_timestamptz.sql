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

-- ALTER TYPE re-casts a column default through the new type, which would leave the broken
-- `now()::timestamp` reading in place. Restate them so they are plain TIMESTAMPTZ `now()`, correct
-- in any session zone. (Hibernate always supplies these three on insert; the defaults only matter
-- to raw SQL and future migrations.)
ALTER TABLE trainer_clients ALTER COLUMN started_at SET DEFAULT now();
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT now();
