--liquibase formatted sql
--changeset bagyshan:0001-create-booking-table
--comment: Create booking table

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS booking (
    booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    station_id VARCHAR(255) NOT NULL,
    connector_id INTEGER NOT NULL,
    price_per_minute DECIMAL(10,2) NOT NULL,
    max_booking_minutes INTEGER NOT NULL,
    current_booking_minutes INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

COMMENT ON COLUMN booking.status IS 'ACTIVE, COMPLETED, FAILED';

--rollback DROP TABLE booking;