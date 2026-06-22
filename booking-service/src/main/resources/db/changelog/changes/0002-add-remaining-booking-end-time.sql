--liquibase formatted sql
--changeset bagyshan:0001-add-remaining-booking-end-time
--comment: Create add remaining booking end time to booking table

ALTER TABLE booking
    ADD COLUMN remaining_booking_end_time TIMESTAMP;

--rollback ALTER TABLE booking DROP COLUMN remaining_booking_end_time;