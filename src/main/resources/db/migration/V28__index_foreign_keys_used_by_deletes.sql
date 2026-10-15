-- Foreign-key columns that the delete refusals (P1-05) and the per-user list queries filter on.
-- Postgres doesn't index a foreign key's referencing column on its own; the existing unique
-- indexes lead with other columns (workout_logs: workout_id, set_logs: workout_log_id), and
-- idx_trainer_clients_trainer_active covers only active rows. Part of PERF-API-001.
CREATE INDEX IF NOT EXISTS idx_set_logs_exercise_set ON set_logs (exercise_set_id);
CREATE INDEX IF NOT EXISTS idx_workout_logs_client ON workout_logs (client_id);
CREATE INDEX IF NOT EXISTS idx_training_plans_trainer ON training_plans (trainer_id);
CREATE INDEX IF NOT EXISTS idx_training_plans_client ON training_plans (client_id);
CREATE INDEX IF NOT EXISTS idx_workouts_training_plan ON workouts (training_plan_id);
CREATE INDEX IF NOT EXISTS idx_trainer_clients_trainer ON trainer_clients (trainer_id);
