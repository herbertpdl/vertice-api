-- An idempotency key per feedback (vertice-mobile client-workout-experience spec, decisions log
-- 2026-10-23): the app resends a feedback whose answer was lost, and the key makes that resend
-- return the stored row instead of adding a duplicate. NULL on feedback sent without one.
ALTER TABLE workout_feedback ADD COLUMN client_key VARCHAR(64);
ALTER TABLE workout_feedback ADD CONSTRAINT ck_workout_feedback_client_key
    CHECK (client_key IS NULL OR client_key ~ '^[A-Za-z0-9_-]{1,64}$');
CREATE UNIQUE INDEX uq_workout_feedback_log_client_key
    ON workout_feedback (workout_log_id, client_key) WHERE client_key IS NOT NULL;
