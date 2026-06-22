--liquibase formatted sql
--changeset analyst:0005-add-total-sum-and-price-per-kwh-to-transaction
--comment: Таблица транзакций

ALTER TABLE transaction
    ADD COLUMN total_sum DECIMAL(10, 2),
    ADD COLUMN price_per_kwh DECIMAL(10, 2);

--rollback ALTER TABLE transaction DROP COLUMN total_sum;
--rollback ALTER TABLE transaction DROP COLUMN price_per_kwh;

