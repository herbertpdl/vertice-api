-- P1-04 (COMP-API-002, COMP-API-003): record which Terms version a user accepted and when, record
-- the health-data consent (LGPD art. 11, I) the same way, give every user created_at/updated_at,
-- and make CPF optional for clients. See docs/data-inventory.md.

-- NULL means "not accepted yet": existing users accepted nothing the platform can prove, so they
-- are asked at their next access (RecordConsent). Version and time are set together or not at all.
ALTER TABLE users
    ADD COLUMN accepted_terms_version      VARCHAR(32),
    ADD COLUMN terms_accepted_at           TIMESTAMP,
    ADD COLUMN health_data_consent_version VARCHAR(32),
    ADD COLUMN health_data_consent_at      TIMESTAMP,
    -- Existing rows get the migration time: their real creation time was never stored.
    ADD COLUMN created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN updated_at                  TIMESTAMP NOT NULL DEFAULT now(),
    ADD CONSTRAINT ck_users_terms_acceptance
        CHECK ((accepted_terms_version IS NULL) = (terms_accepted_at IS NULL)),
    ADD CONSTRAINT ck_users_health_data_consent
        CHECK ((health_data_consent_version IS NULL) = (health_data_consent_at IS NULL));

-- A CLIENT no longer needs a CPF (data minimisation); a TRAINER still does. UserService enforces
-- the rule with a proper error, this is the backstop. Every existing row has a CPF (the column was
-- NOT NULL), so the check holds for them.
ALTER TABLE users ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_trainer_cpf CHECK (role <> 'TRAINER' OR cpf IS NOT NULL);

-- Any number of users without a CPF, still at most one user per CPF.
ALTER TABLE users DROP CONSTRAINT uq_users_cpf;
CREATE UNIQUE INDEX uq_users_cpf ON users (cpf) WHERE cpf IS NOT NULL;
