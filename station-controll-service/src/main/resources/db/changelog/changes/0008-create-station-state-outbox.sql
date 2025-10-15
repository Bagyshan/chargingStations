--liquibase formatted sql
--changeset bagyshan:0008-create-station-state-outbox
--comment: Create station state outbox table

CREATE TABLE station_state_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_station_state_outbox_unpublished ON station_state_outbox (published) WHERE NOT published;
CREATE INDEX idx_station_state_outbox_aggregate ON station_state_outbox (aggregate_id, aggregate_type);

--rollback DROP TABLE station_state_outbox;