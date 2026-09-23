-- Removes every pre-starter exercise (R51-R53) except the ones listed in keep_list, which become
-- private to the trainers whose workouts use them (R56-R59). See
-- docs/specs/exercise-starter-catalog/spec.md §3 V23.
--
-- ONE-WAY DOOR: deleted exercises, workout entries, prescribed sets and logged set values are gone
-- for good; there is no undo migration. Workout sessions (workout_logs) and their feedback are
-- never touched (R54).
--
-- Runs after V22 (muscle groups exist) and before V24 (the starter seed), so every row in
-- `exercises` at this point is pre-starter; that is how they are identified.

-- 1. The keep-list: one row per (kept exercise, launch group name). A kept exercise becomes a
--    trainer's own exercise (R57), so, like every trainer-created exercise, none of its groups is
--    primary. A repeated (exercise, group) row is collapsed. Group names must match
--    muscle_groups.name exactly.
--    Shipped empty: every pre-starter row is test data (assessment Q2).
CREATE TEMP TABLE keep_list (
    exercise_id        BIGINT      NOT NULL,
    muscle_group_name  VARCHAR(50) NOT NULL
) ON COMMIT DROP;

-- @keep-list
-- Example: INSERT INTO keep_list VALUES (42, 'Peito');

DO $$
DECLARE
    offending TEXT;
BEGIN
    SELECT k.muscle_group_name INTO offending
    FROM keep_list k
    LEFT JOIN muscle_groups mg ON mg.name = k.muscle_group_name
    WHERE mg.id IS NULL
    LIMIT 1;
    IF offending IS NOT NULL THEN
        RAISE EXCEPTION 'V23 keep_list: unknown muscle group name "%"', offending;
    END IF;

    SELECT k.exercise_id::TEXT INTO offending
    FROM keep_list k
    LEFT JOIN exercises e ON e.id = k.exercise_id
    WHERE e.id IS NULL
    LIMIT 1;
    IF offending IS NOT NULL THEN
        RAISE EXCEPTION 'V23 keep_list: unknown exercise id %', offending;
    END IF;
END $$;

-- 2. Ownership (R57): the trainers whose workouts use each kept exercise. The lowest trainer id
--    keeps the original row. A kept exercise no workout uses gets no owner and is removed in step 5.
CREATE TEMP TABLE kept_usage ON COMMIT DROP AS
SELECT DISTINCT we.exercise_id, tp.trainer_id
FROM workout_exercises we
JOIN workouts w ON w.id = we.workout_id
JOIN training_plans tp ON tp.id = w.training_plan_id
WHERE we.exercise_id IN (SELECT exercise_id FROM keep_list);

UPDATE exercises e
SET owner_id = first_trainer.trainer_id
FROM (SELECT exercise_id, MIN(trainer_id) AS trainer_id FROM kept_usage GROUP BY exercise_id) first_trainer
WHERE e.id = first_trainer.exercise_id;

-- 3. Per-trainer copies (R58, E23): every other trainer gets an identical private copy and their
--    workout entries are repointed to it. Sets and set logs hang off workout_exercises.id, which
--    does not change, so logged history follows the entry.
CREATE TEMP TABLE copies (
    source_exercise_id  BIGINT NOT NULL,
    new_exercise_id     BIGINT NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    pair    RECORD;
    new_id  BIGINT;
BEGIN
    FOR pair IN
        SELECT ku.exercise_id, ku.trainer_id
        FROM kept_usage ku
        JOIN exercises e ON e.id = ku.exercise_id
        WHERE ku.trainer_id <> e.owner_id
        ORDER BY ku.exercise_id, ku.trainer_id
    LOOP
        INSERT INTO exercises (name, description, video_url, owner_id)
        SELECT name, description, video_url, pair.trainer_id
        FROM exercises
        WHERE id = pair.exercise_id
        RETURNING id INTO new_id;

        INSERT INTO copies (source_exercise_id, new_exercise_id) VALUES (pair.exercise_id, new_id);

        UPDATE workout_exercises
        SET exercise_id = new_id
        WHERE exercise_id = pair.exercise_id
          AND workout_id IN (SELECT w.id
                             FROM workouts w
                             JOIN training_plans tp ON tp.id = w.training_plan_id
                             WHERE tp.trainer_id = pair.trainer_id);
    END LOOP;
END $$;

-- 4. Groups for kept rows and their copies (R59). Never primary and never ordered: only starter
--    rows have a primary group and a catalog order (R49).
INSERT INTO exercise_muscle_groups (exercise_id, muscle_group_id, is_primary, catalog_order)
SELECT DISTINCT e.id, mg.id, FALSE, NULL::INTEGER
FROM keep_list k
JOIN muscle_groups mg ON mg.name = k.muscle_group_name
JOIN exercises e ON e.id = k.exercise_id
                 OR e.id IN (SELECT c.new_exercise_id FROM copies c WHERE c.source_exercise_id = k.exercise_id)
WHERE e.owner_id IS NOT NULL;

-- 5. Removal (R51-R53, E12), children first. "Not kept" = still without an owner.
DELETE FROM set_logs
WHERE exercise_set_id IN (SELECT es.id
                          FROM exercise_sets es
                          JOIN workout_exercises we ON we.id = es.workout_exercise_id
                          JOIN exercises e ON e.id = we.exercise_id
                          WHERE e.owner_id IS NULL);

DELETE FROM exercise_sets
WHERE workout_exercise_id IN (SELECT we.id
                              FROM workout_exercises we
                              JOIN exercises e ON e.id = we.exercise_id
                              WHERE e.owner_id IS NULL);

DELETE FROM workout_exercises
WHERE exercise_id IN (SELECT id FROM exercises WHERE owner_id IS NULL);

DELETE FROM exercises WHERE owner_id IS NULL;
