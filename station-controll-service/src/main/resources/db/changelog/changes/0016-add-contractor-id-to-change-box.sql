--liquibase formatted sql
--changeset bagyshan:0016-add-contractor-id-to-charge-box
--comment: Alter table add contractor id to charge box

ALTER TABLE charge_box
    ADD COLUMN owner_id VARCHAR(100);

--rollback ALTER TABLE charge_box DROP COLUMN owner_id;