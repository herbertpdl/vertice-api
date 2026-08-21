ALTER TABLE trainers ADD COLUMN cpf VARCHAR(11);
UPDATE trainers SET cpf = LPAD(id::text, 11, '0') WHERE cpf IS NULL;
ALTER TABLE trainers ALTER COLUMN cpf SET NOT NULL;
ALTER TABLE trainers ADD CONSTRAINT uq_trainers_cpf UNIQUE (cpf);
