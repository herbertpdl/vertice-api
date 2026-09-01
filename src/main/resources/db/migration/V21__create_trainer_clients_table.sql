CREATE TABLE trainer_clients (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trainer_id  BIGINT NOT NULL REFERENCES users (id),
    client_id   BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_at  TIMESTAMP NOT NULL DEFAULT now(),
    ended_at    TIMESTAMP
);

-- "active as customer of that trainer": a client has at most one active trainer relationship at a time
CREATE UNIQUE INDEX uq_trainer_clients_active_client ON trainer_clients (client_id) WHERE ended_at IS NULL;
CREATE INDEX idx_trainer_clients_trainer_active ON trainer_clients (trainer_id) WHERE ended_at IS NULL;
