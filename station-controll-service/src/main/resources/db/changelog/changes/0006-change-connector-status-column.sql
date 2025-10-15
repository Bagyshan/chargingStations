--liquibase formatted sql
--changeset bagyshan:0006-change-connector-status-column
--comment: Change column status to nullable

ALTER TABLE connector
    ALTER COLUMN status DROP NOT NULL;

--rollback ALTER TABLE connector ALTER COLUMN status SET NOT NULL;