-- Nullable-add, backfill, then NOT NULL — same shape V15 uses for existing local rows (see
-- docs/specs/training-plan-fields/spec.md), since local `exercises` rows predate this column.
-- Backfill infers a group from each existing name where reasonably obvious, defaulting to CORE
-- otherwise; this is throwaway local data, so exactness isn't the goal.
ALTER TABLE exercises ADD COLUMN muscle_group VARCHAR(20);

UPDATE exercises
SET muscle_group = CASE
    WHEN name ILIKE '%supino%' OR name ILIKE '%bench%' THEN 'CHEST'
    WHEN name ILIKE '%agachamento%' OR name ILIKE '%leg%' OR name ILIKE '%squat%' THEN 'LEGS'
    WHEN name ILIKE '%rosca%' OR name ILIKE '%curl%' THEN 'ARMS'
    WHEN name ILIKE '%remada%' OR name ILIKE '%row%' OR name ILIKE '%puxada%' THEN 'BACK'
    WHEN name ILIKE '%desenvolvimento%' OR name ILIKE '%press%' THEN 'SHOULDERS'
    ELSE 'CORE'
END
WHERE muscle_group IS NULL;

ALTER TABLE exercises ALTER COLUMN muscle_group SET NOT NULL;
