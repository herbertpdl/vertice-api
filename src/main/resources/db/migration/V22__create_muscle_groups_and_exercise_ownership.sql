-- Muscle groups become reference data, exercises get one or more of them through a join table, and
-- an exercise is either part of the shared starter set (owner_id NULL) or private to one trainer.
-- See docs/specs/exercise-starter-catalog/spec.md §3.

CREATE TABLE muscle_groups (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(50) NOT NULL,
    CONSTRAINT uq_muscle_groups_name UNIQUE (name)
);

-- R8 launch groups, in R8 order; a fresh identity column yields ids 1..14 in this order.
INSERT INTO muscle_groups (name) VALUES
    ('Peito'), ('Costas'), ('Ombros'), ('Bíceps'), ('Tríceps'), ('Antebraço'), ('Quadríceps'),
    ('Posteriores de coxa'), ('Glúteos'), ('Panturrilhas'), ('Abdômen'), ('Lombar'),
    ('Trapézio'), ('Cardio');

CREATE TABLE exercise_muscle_groups (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exercise_id      BIGINT NOT NULL,
    muscle_group_id  BIGINT NOT NULL,
    is_primary       BOOLEAN NOT NULL DEFAULT FALSE,
    catalog_order    INTEGER,
    CONSTRAINT fk_exercise_muscle_groups_exercise FOREIGN KEY (exercise_id) REFERENCES exercises (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_muscle_groups_muscle_group FOREIGN KEY (muscle_group_id) REFERENCES muscle_groups (id),
    CONSTRAINT uq_exercise_muscle_groups_exercise_group UNIQUE (exercise_id, muscle_group_id),
    CONSTRAINT ck_exercise_muscle_groups_order_only_primary CHECK (catalog_order IS NULL OR is_primary)
);
CREATE UNIQUE INDEX uq_exercise_muscle_groups_one_primary ON exercise_muscle_groups (exercise_id) WHERE is_primary;
CREATE INDEX idx_exercise_muscle_groups_group_order ON exercise_muscle_groups (muscle_group_id, is_primary, catalog_order);

-- NULL = shared starter set (R13); a user id = private to that trainer (R14).
ALTER TABLE exercises ADD COLUMN owner_id BIGINT REFERENCES users (id);
CREATE INDEX idx_exercises_owner ON exercises (owner_id);

-- V20's single enum column is superseded by the join table (D6). Pre-starter rows lose their
-- throwaway heuristic group here; V23 removes or refiles them (R51, R59).
ALTER TABLE exercises DROP COLUMN muscle_group;
