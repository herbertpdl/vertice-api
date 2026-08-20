ALTER TABLE students ADD COLUMN password_hash VARCHAR(255);
UPDATE students SET password_hash = '' WHERE password_hash IS NULL;
ALTER TABLE students ALTER COLUMN password_hash SET NOT NULL;
