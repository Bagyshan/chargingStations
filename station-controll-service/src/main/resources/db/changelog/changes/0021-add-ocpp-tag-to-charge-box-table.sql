--liquibase formatted sql
--changeset bagyshan:0021-add-ocpp-tag-to-charge-box-table
--comment: Alter table and add ocpp tag to charge box

ALTER TABLE charge_box
    ADD COLUMN ocpp_tag VARCHAR(100) DEFAULT 'CP_TEST_TAG';

--rollback ALTER TABLE charge_box DROP COLUMN ocpp_tag;


