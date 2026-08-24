ALTER TABLE exercises DROP CONSTRAINT fk_exercises_training_plan;
ALTER TABLE exercises DROP COLUMN training_plan_id;
ALTER TABLE exercises DROP COLUMN sets;
ALTER TABLE exercises DROP COLUMN reps;
