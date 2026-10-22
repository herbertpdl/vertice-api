-- Client workout experience (vertice-mobile docs/prds/client-workout-experience): a finished
-- workout can be redone in the same week, replacing the earlier session only once the redo is
-- finished. So a workout/client/week holds at most one session IN_PROGRESS and at most one
-- COMPLETED; a replaced one becomes SUPERSEDED (kept, with its feedback, for the trainer).
ALTER TABLE workout_logs ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS';
ALTER TABLE workout_logs ADD COLUMN completion_kind VARCHAR(10);
UPDATE workout_logs SET status = 'COMPLETED', completion_kind = 'CLIENT' WHERE completed_at IS NOT NULL;
ALTER TABLE workout_logs ADD CONSTRAINT ck_workout_logs_status
    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'SUPERSEDED'));
ALTER TABLE workout_logs ADD CONSTRAINT ck_workout_logs_completion
    CHECK ((status = 'IN_PROGRESS') = (completed_at IS NULL)
           AND (completion_kind IS NULL OR completion_kind IN ('CLIENT', 'AUTO'))
           AND ((completed_at IS NULL) = (completion_kind IS NULL)));
ALTER TABLE workout_logs DROP CONSTRAINT uq_workout_logs_workout_client_week;
CREATE UNIQUE INDEX uq_workout_logs_in_progress
    ON workout_logs (workout_id, client_id, week_start_date) WHERE status = 'IN_PROGRESS';
CREATE UNIQUE INDEX uq_workout_logs_completed
    ON workout_logs (workout_id, client_id, week_start_date) WHERE status = 'COMPLETED';
-- The weekly rollover looks for sessions left in progress from earlier weeks.
CREATE INDEX idx_workout_logs_in_progress_week
    ON workout_logs (week_start_date) WHERE status = 'IN_PROGRESS';

-- How hard the client found the workout. NULL on feedback given before the rating existed; every
-- new submission carries one. The text becomes optional ('' when the client only rated).
ALTER TABLE workout_feedback ADD COLUMN rating VARCHAR(20);
ALTER TABLE workout_feedback ADD CONSTRAINT ck_workout_feedback_rating
    CHECK (rating IS NULL OR rating IN ('VERY_HARD', 'HARD', 'JUST_RIGHT', 'EASY', 'VERY_EASY'));
