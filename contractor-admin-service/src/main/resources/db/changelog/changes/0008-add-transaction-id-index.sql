--liquibase formatted sql
--changeset analyst:0008-add-transaction-id-index
--comment: Добавил индекс для transaction id

CREATE INDEX idx_transaction_id ON transaction(transaction_id);

--rollback DROP INDEX idx_transaction_id;