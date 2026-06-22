--liquibase formatted sql
--changeset analyst:0006-create-station-hourly-tariffs-table
--comment: Почасовые тарифы станций

CREATE TABLE IF NOT EXISTS station_hourly_tariffs (
    id BIGSERIAL PRIMARY KEY,
    station_id VARCHAR(255) NOT NULL,
    hour INTEGER NOT NULL,
    kw_cost DECIMAL(10,2) NOT NULL,
    booking_minute_cost DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMENT ON TABLE station_hourly_tariffs IS 'Почасовые тарифы станций';
COMMENT ON COLUMN station_hourly_tariffs.hour IS 'Час суток от 0 до 23';
COMMENT ON COLUMN station_hourly_tariffs.kw_cost IS 'Стоимость 1 kWh';
COMMENT ON COLUMN station_hourly_tariffs.booking_minute_cost IS 'Стоимость минуты бронирования';

ALTER TABLE station_hourly_tariffs
    ADD CONSTRAINT uq_station_hour
        UNIQUE(station_id, hour);

ALTER TABLE station_hourly_tariffs
    ADD CONSTRAINT chk_station_hour
        CHECK (hour >= 0 AND hour <= 23);

CREATE INDEX idx_station_hourly_tariffs_station
    ON station_hourly_tariffs(station_id);

CREATE INDEX idx_station_hourly_tariffs_hour
    ON station_hourly_tariffs(hour);

--rollback DROP INDEX idx_station_hourly_tariffs_hour;
--rollback DROP INDEX idx_station_hourly_tariffs_station;
--rollback ALTER TABLE station_hourly_tariffs DROP CONSTRAINT chk_station_hour;
--rollback ALTER TABLE station_hourly_tariffs DROP CONSTRAINT uq_station_hour;
--rollback DROP TABLE station_hourly_tariffs;