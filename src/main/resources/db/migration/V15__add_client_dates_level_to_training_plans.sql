-- Nullable-add, backfill, then NOT NULL — same shape V5/V6/V7/V8 already use for existing local
-- rows (see docs/specs/cpf-field/spec.md §0), since local `training_plans` rows predate these
-- columns. Backfill picks the first existing CLIENT-role user and today's date as a placeholder;
-- there's no way to know a "real" answer for pre-existing rows, and this is throwaway local data.
ALTER TABLE training_plans ADD COLUMN client_id BIGINT REFERENCES users (id);
ALTER TABLE training_plans ADD COLUMN start_date DATE;
ALTER TABLE training_plans ADD COLUMN end_date DATE;
ALTER TABLE training_plans ADD COLUMN level VARCHAR(20);

UPDATE training_plans
SET client_id = (SELECT id FROM users WHERE role = 'CLIENT' ORDER BY id LIMIT 1),
    start_date = CURRENT_DATE,
    end_date = CURRENT_DATE,
    level = 'BEGINNER'
WHERE client_id IS NULL;

ALTER TABLE training_plans ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE training_plans ALTER COLUMN start_date SET NOT NULL;
ALTER TABLE training_plans ALTER COLUMN end_date SET NOT NULL;
ALTER TABLE training_plans ALTER COLUMN level SET NOT NULL;
