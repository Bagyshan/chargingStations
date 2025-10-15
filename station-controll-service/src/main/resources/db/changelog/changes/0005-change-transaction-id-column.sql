--liquibase formatted sql
--changeset bagyshan:0005-change-transaction-id-column
--comment: Change column transaction id to unique

ALTER TABLE transaction
    ADD CONSTRAINT uq_transaction_transaction_id UNIQUE (transaction_id);

--rollback ALTER TABLE transaction DROP CONSTRAINT uq_transaction_transaction_id;
