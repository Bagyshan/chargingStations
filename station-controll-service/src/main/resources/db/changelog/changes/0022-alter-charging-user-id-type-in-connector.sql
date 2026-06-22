--liquibase formatted sql
--changeset bagyshan:0022-alter-charging-user-id-type-in-connector
--comment: Alter charging user id type in connector

ALTER TABLE connector
    ALTER COLUMN charging_user_id TYPE VARCHAR(100);

--rollback ALTER TABLE connector ALTER COLUMN charging_user_id TYPE uuid;

