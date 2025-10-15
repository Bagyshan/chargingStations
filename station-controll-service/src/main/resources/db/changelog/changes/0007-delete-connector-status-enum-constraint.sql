--liquibase formatted sql
--changeset bagyshan:0007-delete-connector-status-enum-constraint
--comment: Drop CHECK constraint on connector.status

ALTER TABLE connector
    DROP CONSTRAINT chk_connector_status;

--rollback ALTER TABLE connector ADD CONSTRAINT chk_connector_status CHECK (status IN ('Available', 'Preparing', 'Charging', 'SuspendedEV', 'SuspendedEVSE', 'Reserved', 'Unavailable', 'Faulted'));
