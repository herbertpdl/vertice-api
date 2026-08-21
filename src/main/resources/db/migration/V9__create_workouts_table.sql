CREATE TABLE workouts (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    training_plan_id  BIGINT NOT NULL,
    CONSTRAINT fk_workouts_training_plan FOREIGN KEY (training_plan_id) REFERENCES training_plans (id)
);
