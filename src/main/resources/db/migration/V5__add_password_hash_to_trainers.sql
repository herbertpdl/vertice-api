ALTER TABLE trainers ADD COLUMN password_hash VARCHAR(255);
UPDATE trainers SET password_hash = '' WHERE password_hash IS NULL;
ALTER TABLE trainers ALTER COLUMN password_hash SET NOT NULL;
