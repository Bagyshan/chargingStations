--liquibase formatted sql
--changeset bagyshan:0018 add booking user to connector
--comment: Alter table add booking user if to connector

ALTER TABLE connector
    ADD COLUMN booking_user_id VARCHAR(100) DEFAULT null;

--rollback ALTER TABLE connector DROP COLUMN booking_user_id;