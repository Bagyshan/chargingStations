--liquibase formatted sql
--changeset bagyshan:0012-add-geolocation-column-to-charge-box-table
--comment: Add geolocation column to charge_box table for spatial queries

ALTER TABLE charge_box
    ADD COLUMN IF NOT EXISTS geolocation geometry(Point, 4326);

-- Создание пространственного индекса для эффективного поиска
CREATE INDEX IF NOT EXISTS idx_charge_box_geolocation
    ON charge_box USING GIST (geolocation);

-- Создание индекса для geography типа (для точных расчетов расстояния)
CREATE INDEX IF NOT EXISTS idx_charge_box_geolocation_geography
    ON charge_box USING GIST (geography(geolocation));

--rollback DROP INDEX IF EXISTS idx_charge_box_geolocation_geography;
--rollback DROP INDEX IF EXISTS idx_charge_box_geolocation;
--rollback ALTER TABLE charge_box DROP COLUMN IF EXISTS geolocation;