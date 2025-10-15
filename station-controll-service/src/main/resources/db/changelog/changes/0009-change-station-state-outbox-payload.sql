--liquibase formatted sql
--changeset bagyshan:0009-change-station-state-outbox-payload
--comment: Change column payload to string

ALTER TABLE station_state_outbox
    ALTER COLUMN payload TYPE TEXT;

--rollback ALTER TABLE station_state_outbox ALTER COLUMN payload ALTER TYPE JSONB;