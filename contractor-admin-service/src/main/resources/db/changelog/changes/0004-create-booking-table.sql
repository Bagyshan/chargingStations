--liquibase formatted sql
--changeset analyst:004-create-booking-table
--comment: Таблица бронирований
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS booking (
    id BIGSERIAL PRIMARY KEY,
    booking_id UUID NOT NULL,
    user_id UUID NOT NULL,
    station_id VARCHAR(255) NOT NULL,
    connector_id INTEGER NOT NULL,
    price_per_minute DECIMAL(10,2) NOT NULL,
    total_sum DECIMAL(10,2),
    total_minutes INTEGER,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMENT ON COLUMN booking.status IS 'ACTIVE, COMPLETED, CANCELLED, REJECTED';
CREATE INDEX idx_booking_user ON booking(user_id);
CREATE INDEX idx_booking_station ON booking(station_id);
CREATE INDEX idx_booking_status ON booking(status);

--rollback DROP INDEX idx_booking_status;
--rollback DROP INDEX idx_booking_station;
--rollback DROP INDEX idx_booking_user;
--rollback DROP TABLE booking;