--liquibase formatted sql
--changeset bagyshan:0029-add-max-kw-quantity-to-transaction
--comment: Max kWh the user can consume given the wallet balance at start (recomputed on top-up)

ALTER TABLE transaction
    ADD COLUMN max_kw_quantity DECIMAL(12, 3);

--rollback ALTER TABLE transaction DROP COLUMN max_kw_quantity;
