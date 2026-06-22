--liquibase formatted sql
--changeset analyst:003-create-transaction-table
--comment: Таблица транзакций
CREATE TABLE IF NOT EXISTS transaction (
    id BIGSERIAL PRIMARY KEY,
    transaction_id INTEGER NOT NULL,
    charge_box_id VARCHAR(255) NOT NULL,
    connector_id INTEGER NOT NULL,
    start_timestamp TIMESTAMP WITH TIME ZONE,
    start_value INTEGER,
    stop_timestamp TIMESTAMP WITH TIME ZONE,
    stop_value INTEGER,
    transaction_value INTEGER,
    status VARCHAR(50),
    reason VARCHAR(255),
    user_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transaction_charge_box ON transaction(charge_box_id);
CREATE INDEX idx_transaction_user ON transaction(user_id);
CREATE INDEX idx_transaction_status ON transaction(status);
CREATE INDEX idx_transaction_start_time ON transaction(start_timestamp);

--rollback DROP INDEX idx_transaction_start_time;
--rollback DROP INDEX idx_transaction_status;
--rollback DROP INDEX idx_transaction_user;
--rollback DROP INDEX idx_transaction_charge_box;
--rollback DROP TABLE transaction;