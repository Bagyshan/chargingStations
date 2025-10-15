--liquibase formatted sql
--changeset bagyshan:0004-create-transaction
--comment: Create transaction table


CREATE TABLE transaction (
    id SERIAL PRIMARY KEY,
    transaction_id INTEGER NOT NULL,
    charge_box_id VARCHAR(255) NOT NULL,
    connector_id INTEGER NOT NULL,
    start_timestamp TIMESTAMP,
    start_value INTEGER,
    stop_timestamp TIMESTAMP,
    stop_value INTEGER,
    transaction_value INTEGER,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_charge_box
        FOREIGN KEY (charge_box_id)
        REFERENCES charge_box(charge_box_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_transaction_connector
        FOREIGN KEY (charge_box_id, connector_id)
        REFERENCES connector(charge_box_id, connector_id)
        ON DELETE CASCADE,

    CONSTRAINT uc_transaction_unique
        UNIQUE (charge_box_id, connector_id, transaction_id)
);

CREATE INDEX idx_transaction_charge_box ON transaction(charge_box_id);
CREATE INDEX idx_transaction_connector ON transaction(charge_box_id, connector_id);
CREATE INDEX idx_transaction_status ON transaction(status);
CREATE INDEX idx_transaction_timestamps ON transaction(start_timestamp, stop_timestamp);

--rollback DROP TABLE transaction;