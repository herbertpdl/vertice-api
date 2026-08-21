CREATE TABLE exercise_sets (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_exercise_id   BIGINT NOT NULL,
    set_number            INTEGER NOT NULL,
    reps                  INTEGER,
    duration_seconds      INTEGER,
    weight                NUMERIC(6, 2),
    load_percentage       NUMERIC(5, 2),
    strategy              VARCHAR(32) NOT NULL,
    rest_seconds          INTEGER,
    notes                 VARCHAR(255),
    CONSTRAINT fk_exercise_sets_workout_exercise FOREIGN KEY (workout_exercise_id) REFERENCES workout_exercises (id)
);
