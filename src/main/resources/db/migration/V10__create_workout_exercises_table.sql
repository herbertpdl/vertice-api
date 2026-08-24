CREATE TABLE workout_exercises (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_id                  BIGINT NOT NULL,
    exercise_id                 BIGINT NOT NULL,
    exercise_order              INTEGER NOT NULL,
    rest_seconds_between_sets   INTEGER,
    notes                       VARCHAR(255),
    CONSTRAINT fk_workout_exercises_workout FOREIGN KEY (workout_id) REFERENCES workouts (id),
    CONSTRAINT fk_workout_exercises_exercise FOREIGN KEY (exercise_id) REFERENCES exercises (id)
);
