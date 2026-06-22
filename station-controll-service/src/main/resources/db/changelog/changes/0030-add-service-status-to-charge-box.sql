--liquibase formatted sql
--changeset bagyshan:0030-add-service-status-to-charge-box
--comment: Administrative service status — operator can take a station out of service / maintenance

ALTER TABLE charge_box
    ADD COLUMN service_status VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE';

--rollback ALTER TABLE charge_box DROP COLUMN service_status;
