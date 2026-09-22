-- Seeds the 199-exercise shared starter set (R1-R12) transcribed from
-- docs/prds/exercise-starter-catalog/prd.md §10. See docs/specs/exercise-starter-catalog/spec.md
-- §3 V24.
--
-- One staging row per PRD §10 table row: primary_group = the section it is listed under,
-- catalog_order = its # there, name = the Exercise column verbatim, secondary_groups = the Groups
-- column without the section's own group. Names are unique, so name is the join key below.
--
-- Reversible while no workout uses a starter row: DELETE FROM exercises WHERE owner_id IS NULL.

CREATE TEMP TABLE starter_seed (
    primary_group     VARCHAR(50)   NOT NULL,
    catalog_order     INTEGER       NOT NULL,
    name              VARCHAR(255)  NOT NULL,
    secondary_groups  VARCHAR(50)[] NOT NULL
) ON COMMIT DROP;

INSERT INTO starter_seed (primary_group, catalog_order, name, secondary_groups) VALUES
    -- Peito
    ('Peito', 1, 'Supino reto com barra', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 2, 'Supino inclinado com halteres', ARRAY['Ombros']::VARCHAR(50)[]),
    ('Peito', 3, 'Supino inclinado com barra', ARRAY['Ombros']::VARCHAR(50)[]),
    ('Peito', 4, 'Supino reto com halteres', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 5, 'Crucifixo reto com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Peito', 6, 'Peck deck na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Peito', 7, 'Crossover na polia alta', ARRAY[]::VARCHAR(50)[]),
    ('Peito', 8, 'Supino declinado com barra', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 9, 'Flexão de braço', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 10, 'Supino reto na máquina', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 11, 'Crucifixo inclinado com halteres', ARRAY['Ombros']::VARCHAR(50)[]),
    ('Peito', 12, 'Crossover na polia baixa', ARRAY[]::VARCHAR(50)[]),
    ('Peito', 13, 'Supino inclinado na máquina', ARRAY['Ombros']::VARCHAR(50)[]),
    ('Peito', 14, 'Supino reto no Smith', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 15, 'Pullover com halter', ARRAY['Costas']::VARCHAR(50)[]),
    ('Peito', 16, 'Supino declinado com halteres', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 17, 'Mergulho nas paralelas com tronco inclinado', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Peito', 18, 'Supino inclinado no Smith', ARRAY['Ombros']::VARCHAR(50)[]),
    ('Peito', 19, 'Crucifixo na máquina inclinada', ARRAY[]::VARCHAR(50)[]),
    ('Peito', 20, 'Flexão de braço com pés elevados', ARRAY['Ombros']::VARCHAR(50)[]),
    -- Costas
    ('Costas', 1, 'Puxada alta na polia com pegada pronada', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 2, 'Remada curvada com barra', ARRAY['Lombar']::VARCHAR(50)[]),
    ('Costas', 3, 'Remada baixa na polia com triângulo', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 4, 'Barra fixa pronada', ARRAY['Bíceps']::VARCHAR(50)[]),
    ('Costas', 5, 'Remada unilateral com halter', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 6, 'Puxada alta na polia com pegada supinada', ARRAY['Bíceps']::VARCHAR(50)[]),
    ('Costas', 7, 'Levantamento terra com barra', ARRAY['Lombar', 'Posteriores de coxa']::VARCHAR(50)[]),
    ('Costas', 8, 'Remada cavalinho', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 9, 'Barra fixa supinada', ARRAY['Bíceps']::VARCHAR(50)[]),
    ('Costas', 10, 'Remada na máquina articulada', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 11, 'Puxada alta na polia com triângulo', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 12, 'Remada sentada na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 13, 'Pulldown com braços estendidos na polia', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 14, 'Remada curvada com halteres', ARRAY['Lombar']::VARCHAR(50)[]),
    ('Costas', 15, 'Puxada alta com pegada aberta', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 16, 'Remada com pegada neutra na polia', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 17, 'Remada no Smith', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 18, 'Pullover na polia alta', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 19, 'Barra fixa australiana', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 20, 'Puxada frontal na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Costas', 21, 'Levantamento terra sumô', ARRAY['Glúteos', 'Posteriores de coxa']::VARCHAR(50)[]),
    ('Costas', 22, 'Remada curvada com pegada supinada', ARRAY['Bíceps']::VARCHAR(50)[]),
    -- Ombros
    ('Ombros', 1, 'Desenvolvimento com halteres sentado', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Ombros', 2, 'Elevação lateral com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 3, 'Desenvolvimento com barra à frente', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Ombros', 4, 'Elevação frontal com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 5, 'Crucifixo inverso com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 6, 'Desenvolvimento na máquina', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Ombros', 7, 'Elevação lateral na polia', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 8, 'Face pull na polia', ARRAY['Trapézio']::VARCHAR(50)[]),
    ('Ombros', 9, 'Desenvolvimento militar em pé com barra', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Ombros', 10, 'Voador inverso na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 11, 'Elevação frontal com barra', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 12, 'Remada alta com barra', ARRAY['Trapézio']::VARCHAR(50)[]),
    ('Ombros', 13, 'Elevação lateral na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 14, 'Desenvolvimento Arnold com halteres', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Ombros', 15, 'Elevação frontal na polia', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 16, 'Elevação lateral inclinada com halter', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 17, 'Desenvolvimento no Smith', ARRAY['Tríceps']::VARCHAR(50)[]),
    ('Ombros', 18, 'Remada alta com halteres', ARRAY['Trapézio']::VARCHAR(50)[]),
    ('Ombros', 19, 'Elevação posterior na polia', ARRAY[]::VARCHAR(50)[]),
    ('Ombros', 20, 'Elevação lateral com anilha', ARRAY[]::VARCHAR(50)[]),
    -- Bíceps
    ('Bíceps', 1, 'Rosca direta com barra', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 2, 'Rosca alternada com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 3, 'Rosca martelo com halteres', ARRAY['Antebraço']::VARCHAR(50)[]),
    ('Bíceps', 4, 'Rosca direta com barra W', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 5, 'Rosca scott com barra W', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 6, 'Rosca concentrada com halter', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 7, 'Rosca na polia baixa com barra', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 8, 'Rosca scott na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 9, 'Rosca inclinada com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 10, 'Rosca martelo na polia com corda', ARRAY['Antebraço']::VARCHAR(50)[]),
    ('Bíceps', 11, 'Rosca inversa com barra W', ARRAY['Antebraço']::VARCHAR(50)[]),
    ('Bíceps', 12, 'Rosca scott com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 13, 'Rosca 21 com barra', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 14, 'Rosca direta com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 15, 'Rosca na polia alta unilateral', ARRAY[]::VARCHAR(50)[]),
    ('Bíceps', 16, 'Rosca martelo cruzada com halter', ARRAY['Antebraço']::VARCHAR(50)[]),
    -- Tríceps
    ('Tríceps', 1, 'Tríceps na polia com barra reta', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 2, 'Tríceps na polia com corda', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 3, 'Tríceps testa com barra W', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 4, 'Mergulho nas paralelas', ARRAY['Peito']::VARCHAR(50)[]),
    ('Tríceps', 5, 'Supino fechado com barra', ARRAY['Peito']::VARCHAR(50)[]),
    ('Tríceps', 6, 'Tríceps francês com halter', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 7, 'Tríceps coice com halter', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 8, 'Tríceps testa com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 9, 'Tríceps na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 10, 'Mergulho no banco', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 11, 'Tríceps na polia com barra V', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 12, 'Extensão de tríceps acima da cabeça na polia com corda', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 13, 'Tríceps coice na polia', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 14, 'Tríceps unilateral na polia com pegada inversa', ARRAY[]::VARCHAR(50)[]),
    ('Tríceps', 15, 'Flexão diamante', ARRAY['Peito']::VARCHAR(50)[]),
    ('Tríceps', 16, 'Supino fechado no Smith', ARRAY['Peito']::VARCHAR(50)[]),
    -- Antebraço
    ('Antebraço', 1, 'Rosca de punho com barra', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 2, 'Rosca de punho inversa com barra', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 3, 'Rosca de punho com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 4, 'Rolo de punho', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 5, 'Farmer''s walk com halteres', ARRAY['Trapézio']::VARCHAR(50)[]),
    ('Antebraço', 6, 'Pegada em pinça com anilhas', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 7, 'Suspensão na barra fixa', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 8, 'Rosca de punho na polia', ARRAY[]::VARCHAR(50)[]),
    ('Antebraço', 9, 'Rosca inversa na polia com barra reta', ARRAY['Bíceps']::VARCHAR(50)[]),
    -- Quadríceps
    ('Quadríceps', 1, 'Agachamento livre com barra', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 2, 'Leg press 45', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 3, 'Cadeira extensora', ARRAY[]::VARCHAR(50)[]),
    ('Quadríceps', 4, 'Agachamento no Smith', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 5, 'Hack machine', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 6, 'Afundo com halteres', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 7, 'Agachamento búlgaro com halteres', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 8, 'Agachamento frontal com barra', ARRAY[]::VARCHAR(50)[]),
    ('Quadríceps', 9, 'Leg press horizontal', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 10, 'Passada com halteres', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 11, 'Agachamento goblet com halter', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 12, 'Afundo no Smith', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 13, 'Cadeira extensora unilateral', ARRAY[]::VARCHAR(50)[]),
    ('Quadríceps', 14, 'Step up no banco com halteres', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 15, 'Agachamento sumô com halter', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 16, 'Agachamento pêndulo', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 17, 'Leg press unilateral', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 18, 'Agachamento livre com peso corporal', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Quadríceps', 19, 'Sissy squat', ARRAY[]::VARCHAR(50)[]),
    ('Quadríceps', 20, 'Agachamento na barra guiada', ARRAY['Glúteos']::VARCHAR(50)[]),
    -- Posteriores de coxa
    ('Posteriores de coxa', 1, 'Mesa flexora', ARRAY[]::VARCHAR(50)[]),
    ('Posteriores de coxa', 2, 'Cadeira flexora', ARRAY[]::VARCHAR(50)[]),
    ('Posteriores de coxa', 3, 'Stiff com barra', ARRAY['Glúteos', 'Lombar']::VARCHAR(50)[]),
    ('Posteriores de coxa', 4, 'Levantamento terra romeno com barra', ARRAY['Glúteos', 'Lombar']::VARCHAR(50)[]),
    ('Posteriores de coxa', 5, 'Stiff com halteres', ARRAY['Glúteos', 'Lombar']::VARCHAR(50)[]),
    ('Posteriores de coxa', 6, 'Flexora em pé unilateral', ARRAY[]::VARCHAR(50)[]),
    ('Posteriores de coxa', 7, 'Levantamento terra romeno com halteres', ARRAY['Glúteos', 'Lombar']::VARCHAR(50)[]),
    ('Posteriores de coxa', 8, 'Bom dia com barra', ARRAY['Lombar']::VARCHAR(50)[]),
    ('Posteriores de coxa', 9, 'Mesa flexora unilateral', ARRAY[]::VARCHAR(50)[]),
    ('Posteriores de coxa', 10, 'Nordic curl', ARRAY[]::VARCHAR(50)[]),
    ('Posteriores de coxa', 11, 'Stiff no Smith', ARRAY['Glúteos', 'Lombar']::VARCHAR(50)[]),
    ('Posteriores de coxa', 12, 'Flexora na polia', ARRAY[]::VARCHAR(50)[]),
    ('Posteriores de coxa', 13, 'Levantamento terra com pernas estendidas', ARRAY['Lombar']::VARCHAR(50)[]),
    -- Glúteos
    ('Glúteos', 1, 'Elevação pélvica com barra', ARRAY['Posteriores de coxa']::VARCHAR(50)[]),
    ('Glúteos', 2, 'Coice na polia', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 3, 'Cadeira abdutora', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 4, 'Abdução de quadril na polia', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 5, 'Elevação pélvica na máquina', ARRAY['Posteriores de coxa']::VARCHAR(50)[]),
    ('Glúteos', 6, 'Coice na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 7, 'Ponte de glúteo no solo', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 8, 'Elevação pélvica unilateral', ARRAY['Posteriores de coxa']::VARCHAR(50)[]),
    ('Glúteos', 9, 'Afundo com barra', ARRAY['Quadríceps']::VARCHAR(50)[]),
    ('Glúteos', 10, 'Extensão de quadril no banco', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 11, 'Step up alto no banco com halteres', ARRAY['Quadríceps']::VARCHAR(50)[]),
    ('Glúteos', 12, 'Passada lateral com elástico', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 13, 'Abdução de quadril na máquina em pé', ARRAY[]::VARCHAR(50)[]),
    ('Glúteos', 14, 'Agachamento sumô com barra', ARRAY['Quadríceps']::VARCHAR(50)[]),
    -- Panturrilhas
    ('Panturrilhas', 1, 'Panturrilha em pé na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 2, 'Panturrilha sentada na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 3, 'Panturrilha no leg press', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 4, 'Panturrilha em pé com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 5, 'Panturrilha no Smith', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 6, 'Panturrilha unilateral com halter', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 7, 'Panturrilha no step', ARRAY[]::VARCHAR(50)[]),
    ('Panturrilhas', 8, 'Panturrilha burrinho', ARRAY[]::VARCHAR(50)[]),
    -- Abdômen
    ('Abdômen', 1, 'Abdominal supra no solo', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 2, 'Prancha isométrica', ARRAY['Lombar']::VARCHAR(50)[]),
    ('Abdômen', 3, 'Elevação de pernas na barra fixa', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 4, 'Abdominal na polia ajoelhado com corda', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 5, 'Abdominal infra no solo', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 6, 'Abdominal na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 7, 'Elevação de joelhos no paralelo', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 8, 'Prancha lateral', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 9, 'Abdominal bicicleta', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 10, 'Rotação russa com anilha', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 11, 'Elevação de pernas no banco', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 12, 'Abdominal oblíquo no solo', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 13, 'Rodinha abdominal', ARRAY['Lombar']::VARCHAR(50)[]),
    ('Abdômen', 14, 'Abdominal remador', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 15, 'Escalador', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 16, 'Abdominal canivete', ARRAY[]::VARCHAR(50)[]),
    ('Abdômen', 17, 'Prancha com elevação de braço', ARRAY[]::VARCHAR(50)[]),
    -- Lombar
    ('Lombar', 1, 'Hiperextensão lombar no banco romano', ARRAY['Glúteos']::VARCHAR(50)[]),
    ('Lombar', 2, 'Hiperextensão lombar na máquina', ARRAY[]::VARCHAR(50)[]),
    ('Lombar', 3, 'Superman no solo', ARRAY[]::VARCHAR(50)[]),
    ('Lombar', 4, 'Extensão lombar unilateral no banco', ARRAY[]::VARCHAR(50)[]),
    ('Lombar', 5, 'Prancha reversa', ARRAY['Abdômen']::VARCHAR(50)[]),
    ('Lombar', 6, 'Bird dog no solo', ARRAY['Abdômen']::VARCHAR(50)[]),
    ('Lombar', 7, 'Ponte isométrica no banco romano', ARRAY[]::VARCHAR(50)[]),
    -- Trapézio
    ('Trapézio', 1, 'Encolhimento com halteres', ARRAY[]::VARCHAR(50)[]),
    ('Trapézio', 2, 'Encolhimento com barra', ARRAY[]::VARCHAR(50)[]),
    ('Trapézio', 3, 'Encolhimento no Smith', ARRAY[]::VARCHAR(50)[]),
    ('Trapézio', 4, 'Encolhimento na polia', ARRAY[]::VARCHAR(50)[]),
    ('Trapézio', 5, 'Encolhimento com anilhas', ARRAY[]::VARCHAR(50)[]),
    ('Trapézio', 6, 'Remada alta na polia', ARRAY['Ombros']::VARCHAR(50)[]),
    ('Trapézio', 7, 'Encolhimento unilateral com halter', ARRAY[]::VARCHAR(50)[]),
    -- Cardio
    ('Cardio', 1, 'Esteira', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 2, 'Bicicleta ergométrica', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 3, 'Elíptico', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 4, 'Escada simuladora', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 5, 'Remo ergômetro', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 6, 'Bicicleta de spinning', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 7, 'Pular corda', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 8, 'Caminhada inclinada na esteira', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 9, 'Corda naval', ARRAY[]::VARCHAR(50)[]),
    ('Cardio', 10, 'Assault bike', ARRAY[]::VARCHAR(50)[]);

-- R2 name, R4 no description, R5 no video, R13 owner_id NULL = starter set.
INSERT INTO exercises (name, description, video_url, owner_id)
SELECT s.name, NULL, NULL, NULL
FROM starter_seed s
JOIN muscle_groups mg ON mg.name = s.primary_group
ORDER BY mg.id, s.catalog_order;

-- Primary link, carrying the PRD position (R49). The owner_id IS NULL guard keeps a private
-- exercise that happens to share a starter name (R44) from receiving starter links.
INSERT INTO exercise_muscle_groups (exercise_id, muscle_group_id, is_primary, catalog_order)
SELECT e.id, mg.id, TRUE, s.catalog_order
FROM starter_seed s
JOIN exercises e ON e.name = s.name AND e.owner_id IS NULL
JOIN muscle_groups mg ON mg.name = s.primary_group;

-- Secondary links: unordered within their group (R11, R50).
INSERT INTO exercise_muscle_groups (exercise_id, muscle_group_id, is_primary, catalog_order)
SELECT e.id, mg.id, FALSE, NULL
FROM starter_seed s
CROSS JOIN LATERAL unnest(s.secondary_groups) AS g(name)
JOIN exercises e ON e.name = s.name AND e.owner_id IS NULL
JOIN muscle_groups mg ON mg.name = g.name;

DO $$
DECLARE
    actual BIGINT;
BEGIN
    SELECT count(*) INTO actual FROM exercises WHERE owner_id IS NULL;
    IF actual <> 199 THEN
        RAISE EXCEPTION 'V24: expected 199 starter exercises, found %', actual;
    END IF;

    SELECT count(*) INTO actual FROM muscle_groups;
    IF actual <> 14 THEN
        RAISE EXCEPTION 'V24: expected 14 muscle groups, found %', actual;
    END IF;

    SELECT count(*) INTO actual
    FROM exercise_muscle_groups emg
    JOIN exercises e ON e.id = emg.exercise_id
    WHERE e.owner_id IS NULL;
    IF actual <> 283 THEN
        RAISE EXCEPTION 'V24: expected 283 starter group links, found %', actual;
    END IF;

    SELECT count(*) INTO actual
    FROM exercises e
    WHERE e.owner_id IS NULL
      AND (SELECT count(*)
           FROM exercise_muscle_groups emg
           WHERE emg.exercise_id = e.id AND emg.is_primary AND emg.catalog_order IS NOT NULL) <> 1;
    IF actual <> 0 THEN
        RAISE EXCEPTION 'V24: % starter exercises lack exactly one ordered primary group', actual;
    END IF;
END $$;
