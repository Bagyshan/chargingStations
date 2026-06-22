--liquibase formatted sql
--changeset bagyshan:0003-add-total-minutes-and-total-sum-to-booking
--comment: Create add total minutes and total sum to booking

ALTER TABLE booking
    ADD COLUMN total_sum DECIMAL(10, 2),
    ADD COLUMN total_minutes int;

--rollback ALTER TABLE booking DROP COLUMN total_sum, DROP COLUMN total_minutes;