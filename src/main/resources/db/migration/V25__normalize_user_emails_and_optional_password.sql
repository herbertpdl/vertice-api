-- Emails become case-insensitive (REL-API-007) and a user may exist without a password (P0-02).
-- See docs/specs/password-lifecycle/spec.md.

-- Two rows that only differ by case or surrounding spaces can't be merged automatically: they are
-- two accounts with their own data. Stop here and let someone resolve them by hand. Only ids are
-- reported, so the migration log carries no email addresses.
DO $$
DECLARE
    collisions TEXT;
BEGIN
    SELECT string_agg('[' || ids || ']', ', ')
      INTO collisions
      FROM (SELECT string_agg(id::TEXT, ', ' ORDER BY id) AS ids
              FROM users
             GROUP BY lower(trim(email))
            HAVING count(*) > 1) duplicates;

    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION 'V25: users share an email when compared case-insensitively (user ids %). '
                        'Change or merge those accounts by hand, then rerun the migration.', collisions;
    END IF;
END $$;

UPDATE users
   SET email = lower(trim(email))
 WHERE email <> lower(trim(email));

ALTER TABLE users DROP CONSTRAINT uq_users_email;
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- NULL means "no password yet". V5/V6 back-filled legacy rows with '' for the same thing.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
UPDATE users SET password_hash = NULL WHERE password_hash = '';
