CREATE TABLE training_plans (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(255),
    trainer_id   BIGINT NOT NULL,
    CONSTRAINT fk_training_plans_trainer FOREIGN KEY (trainer_id) REFERENCES trainers (id)
);
