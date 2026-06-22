--liquibase formatted sql
--changeset bagyshan:0019-add-booking-minute-cost-column-to-charge-box
--comment: Alter table and add booking minute cost column to charge box

ALTER TABLE charge_box
    ADD COLUMN booking_minute_cost DECIMAL(10, 2) DEFAULT 0;

--rollback ALTER TABLE charge_box DROP COLUMN booking_minute_cost;