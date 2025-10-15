--liquibase formatted sql
--changeset bagyshan:0003-add-connector-status
--comment: Add status column to connector table

ALTER TABLE connector
    ADD COLUMN status VARCHAR(20);

ALTER TABLE connector
    ADD CONSTRAINT chk_connector_status
        CHECK (status IN ('Available', 'Preparing', 'Charging', 'SuspendedEV', 'SuspendedEVSE', 'Reserved', 'Unavailable', 'Faulted'));

ALTER TABLE connector
    ALTER COLUMN status SET DEFAULT 'Available';

UPDATE connector
SET status = 'Available'
WHERE status IS NULL;

ALTER TABLE connector
    ALTER COLUMN status SET NOT NULL;

--rollback ALTER TABLE connector ALTER COLUMN status DROP NOT NULL;
--rollback ALTER TABLE connector DROP CONSTRAINT IF EXISTS chk_connector_status;
--rollback ALTER TABLE connector DROP COLUMN IF EXISTS status;
