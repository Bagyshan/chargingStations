--liquibase formatted sql
--changeset bagyshan:0015-change-user-id-type-in-transaction
--comment: Alter column user id in transaction

ALTER TABLE transaction
    ALTER COLUMN user_id TYPE VARCHAR(100);

--rollback ALTER TABLE transaction ALTER COLUMN user_id TYPE int default 0;