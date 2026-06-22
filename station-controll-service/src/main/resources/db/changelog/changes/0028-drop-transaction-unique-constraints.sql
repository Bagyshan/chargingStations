--liquibase formatted sql
--changeset bagyshan:0028-drop-transaction-unique-constraints
--comment: Remove unique constraints on transaction_id — SteVe reuses IDs over time (normal OCPP behavior)

ALTER TABLE transaction DROP CONSTRAINT IF EXISTS uc_transaction_unique;
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS uq_transaction_transaction_id;

--rollback ALTER TABLE transaction ADD CONSTRAINT uc_transaction_unique UNIQUE (charge_box_id, connector_id, transaction_id);
--rollback ALTER TABLE transaction ADD CONSTRAINT uq_transaction_transaction_id UNIQUE (transaction_id);
