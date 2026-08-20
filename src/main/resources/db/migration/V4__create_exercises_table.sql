CREATE TABLE exercises (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(255),
    sets              INTEGER,
    reps              INTEGER,
    training_plan_id  BIGINT NOT NULL,
    CONSTRAINT fk_exercises_training_plan FOREIGN KEY (training_plan_id) REFERENCES training_plans (id)
);
