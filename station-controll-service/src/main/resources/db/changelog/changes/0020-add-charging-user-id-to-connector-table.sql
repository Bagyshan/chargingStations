--liquibase formatted sql
--changeset bagyshan:0020-add-charging-user-id-to-connector-table
--comment: Alter table and add charging user id to connector

ALTER TABLE connector
    ADD COLUMN charging_user_id uuid;

--rollback ALTER TABLE connector DROP COLUMN charging_user_id;
