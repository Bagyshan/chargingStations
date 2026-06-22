--liquibase formatted sql
--changeset analyst:002-create-connector-table
--comment: Таблица коннекторов
CREATE TABLE IF NOT EXISTS connector (
    id SERIAL PRIMARY KEY,
    charge_box_id VARCHAR(255) NOT NULL,
    connector_id INTEGER NOT NULL,
    info TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    vendor_id VARCHAR(255),
    status VARCHAR(20),
    connector_type_id INTEGER,
    UNIQUE(charge_box_id, connector_id)
);

CREATE INDEX idx_connector_charge_box ON connector(charge_box_id);
CREATE INDEX idx_connector_status ON connector(status);

--rollback DROP INDEX idx_connector_status;
--rollback DROP INDEX idx_connector_charge_box;
--rollback DROP TABLE connector;
