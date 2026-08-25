CREATE TABLE workout_feedback (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_log_id  BIGINT NOT NULL,
    text            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_workout_feedback_workout_log FOREIGN KEY (workout_log_id) REFERENCES workout_logs (id)
);
