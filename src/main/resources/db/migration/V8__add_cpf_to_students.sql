ALTER TABLE students ADD COLUMN cpf VARCHAR(11);
UPDATE students SET cpf = LPAD(id::text, 11, '0') WHERE cpf IS NULL;
ALTER TABLE students ALTER COLUMN cpf SET NOT NULL;
ALTER TABLE students ADD CONSTRAINT uq_students_cpf UNIQUE (cpf);
