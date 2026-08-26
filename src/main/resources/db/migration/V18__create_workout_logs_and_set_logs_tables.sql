CREATE TABLE workout_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_id       BIGINT NOT NULL,
    client_id        BIGINT NOT NULL,
    week_start_date  DATE NOT NULL,
    started_at       TIMESTAMP NOT NULL,
    completed_at     TIMESTAMP,
    CONSTRAINT fk_workout_logs_workout FOREIGN KEY (workout_id) REFERENCES workouts (id),
    CONSTRAINT fk_workout_logs_client FOREIGN KEY (client_id) REFERENCES users (id),
    CONSTRAINT uq_workout_logs_workout_client_week UNIQUE (workout_id, client_id, week_start_date)
);

CREATE TABLE set_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_log_id   BIGINT NOT NULL,
    exercise_set_id  BIGINT NOT NULL,
    weight           NUMERIC(6, 2),
    reps             INTEGER,
    recorded_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_set_logs_workout_log FOREIGN KEY (workout_log_id) REFERENCES workout_logs (id),
    CONSTRAINT fk_set_logs_exercise_set FOREIGN KEY (exercise_set_id) REFERENCES exercise_sets (id),
    CONSTRAINT uq_set_logs_workout_log_exercise_set UNIQUE (workout_log_id, exercise_set_id)
);
