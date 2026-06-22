--liquibase formatted sql
--changeset bagyshan:0014-add-user-column-to-transaction
--comment: Add user column to transaction table

ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS user_id INTEGER NOT NULL DEFAULT 0;

--rollback ALTER TABLE transaction DROP COLUMN IF EXISTS user_id;