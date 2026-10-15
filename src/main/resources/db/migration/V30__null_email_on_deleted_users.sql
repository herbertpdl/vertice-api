-- P1-05 (b), review of #68: an anonymised account has no email at all. A placeholder such as
-- deleted+<id>@invalid is predictable, so a live account could claim it first and make that
-- user's DeleteAccount fail on uq_users_email_lower forever. NULL can't collide: the unique index
-- on lower(email) treats every NULL as distinct. A live account still needs an email.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_email_unless_deleted
    CHECK (email IS NOT NULL OR deleted_at IS NOT NULL);
