--liquibase formatted sql
--changeset bagyshan:0003-add-is-charging
--comment: Track whether the user currently has an active charging transaction

ALTER TABLE balance
    ADD COLUMN IF NOT EXISTS is_charging BOOLEAN NOT NULL DEFAULT false;

--rollback ALTER TABLE balance DROP COLUMN is_charging;
