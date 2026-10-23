-- Trainer contacts the client app shows (vertice-mobile PRD client-workout-experience, R74-R79):
-- a trainer may add a WhatsApp number and an Instagram handle, and chooses which of e-mail,
-- WhatsApp and Instagram their clients see. Hidden until the trainer switches one on.
ALTER TABLE users ADD COLUMN whatsapp VARCHAR(11);
ALTER TABLE users ADD COLUMN instagram VARCHAR(30);
ALTER TABLE users ADD COLUMN show_email BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN show_whatsapp BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN show_instagram BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD CONSTRAINT ck_users_whatsapp CHECK (whatsapp IS NULL OR whatsapp ~ '^[0-9]{10,11}$');
ALTER TABLE users ADD CONSTRAINT ck_users_instagram CHECK (instagram IS NULL OR instagram ~ '^[A-Za-z0-9._]{1,30}$');
