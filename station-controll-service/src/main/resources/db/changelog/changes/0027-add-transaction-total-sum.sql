--liquibase formatted sql
--changeset bagyshan:0027-add-transaction-total-sum
--comment: Alter table transaction to total sum

ALTER TABLE transaction
    ADD COLUMN total_sum DECIMAL(10, 2),
    ADD COLUMN price_per_kwh DECIMAL(10, 2);

--rollback ALTER TABLE transaction DROP COLUMN total_sum;
--rollback ALTER TABLE transaction DROP COLUMN price_per_kwh;
