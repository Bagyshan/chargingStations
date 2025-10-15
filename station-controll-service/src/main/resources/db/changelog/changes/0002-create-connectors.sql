--liquibase formatted sql
--changeset bagyshan:0002-create-connectors
--comment: Create initial tables

CREATE TABLE IF NOT EXISTS connector (
    id SERIAL PRIMARY KEY,
    charge_box_id VARCHAR(255) NOT NULL,
    connector_id INTEGER NOT NULL,
    info TEXT,
    action_type VARCHAR(50),
    created_at TIMESTAMP,
    vendor_id VARCHAR(255),


    CONSTRAINT uc_connector_charge_box_connector UNIQUE (charge_box_id, connector_id),
    CONSTRAINT fk_connector_charge_box
        FOREIGN KEY (charge_box_id)
        REFERENCES charge_box(charge_box_id)
        ON DELETE CASCADE
);


--rollback DROP TABLE connector;