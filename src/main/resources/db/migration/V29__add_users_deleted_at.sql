-- P1-05 (b): DeleteAccount anonymises the users row instead of deleting it (plans the user wrote,
-- and relationship history, keep pointing at it). deleted_at marks the row as a removed account:
-- the per-call role check treats it as missing, so the user's tokens stop working at once.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

-- An anonymised trainer has no CPF any more; every live trainer still needs one.
ALTER TABLE users DROP CONSTRAINT ck_users_trainer_cpf;
ALTER TABLE users ADD CONSTRAINT ck_users_trainer_cpf
    CHECK (role <> 'TRAINER' OR cpf IS NOT NULL OR deleted_at IS NOT NULL);
