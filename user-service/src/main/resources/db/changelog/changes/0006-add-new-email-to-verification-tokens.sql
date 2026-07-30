--liquibase formatted sql
--changeset bagyshan:0006-add-new-email-to-verification-tokens
--comment: Store target email address for EMAIL_CHANGE verification tokens (email change flow)

ALTER TABLE verification_tokens ADD COLUMN IF NOT EXISTS new_email VARCHAR(255);

--rollback ALTER TABLE verification_tokens DROP COLUMN new_email;
