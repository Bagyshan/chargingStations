--liquibase formatted sql
--changeset bagyshan:0010-add-version-column-to-charge-box-and-connector
--comment: Add version columns to ChargeBoxEntity and ConnectorEntity

ALTER TABLE charge_box
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE connector
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

--rollback ALTER TABLE connector DROP COLUMN version;
--rollback ALTER TABLE charge_box DROP COLUMN version;