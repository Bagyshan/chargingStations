--liquibase formatted sql
--changeset bagyshan:0013-create-spatial-functions
--comment: Create helper functions for spatial operations

-- Функция для поиска станций в радиусе (в метрах)
CREATE OR REPLACE FUNCTION find_stations_in_radius(
    center_latitude DOUBLE PRECISION,
    center_longitude DOUBLE PRECISION,
    search_radius DOUBLE PRECISION,
    max_results INTEGER DEFAULT 100
)
    RETURNS TABLE(
                     station_id INTEGER,
                     charge_box_id VARCHAR(255),
                     latitude DOUBLE PRECISION,
                     longitude DOUBLE PRECISION,
                     distance_meters DOUBLE PRECISION
                 ) AS $$
BEGIN
    RETURN QUERY
        SELECT
            cb.id,
            cb.charge_box_id,
            ST_Y(cb.geolocation) as latitude,
            ST_X(cb.geolocation) as longitude,
            ST_Distance(
                    geography(cb.geolocation),
                    geography(ST_SetSRID(ST_MakePoint(center_longitude, center_latitude), 4326))
            ) as distance_meters
        FROM charge_box cb
        WHERE cb.geolocation IS NOT NULL
          AND ST_DWithin(
                geography(cb.geolocation),
                geography(ST_SetSRID(ST_MakePoint(center_longitude, center_latitude), 4326)),
                search_radius
              )
        ORDER BY distance_meters ASC
        LIMIT max_results;
END
$$ LANGUAGE plpgsql;

-- Функция для поиска ближайших станций
CREATE OR REPLACE FUNCTION find_nearest_stations(
    center_latitude DOUBLE PRECISION,
    center_longitude DOUBLE PRECISION,
    max_results INTEGER DEFAULT 10
)
    RETURNS TABLE(
                     station_id INTEGER,
                     charge_box_id VARCHAR(255),
                     latitude DOUBLE PRECISION,
                     longitude DOUBLE PRECISION,
                     distance_meters DOUBLE PRECISION
                 ) AS $$
BEGIN
    RETURN QUERY
        SELECT
            cb.id,
            cb.charge_box_id,
            ST_Y(cb.geolocation) as latitude,
            ST_X(cb.geolocation) as longitude,
            ST_Distance(
                    geography(cb.geolocation),
                    geography(ST_SetSRID(ST_MakePoint(center_longitude, center_latitude), 4326))
            ) as distance_meters
        FROM charge_box cb
        WHERE cb.geolocation IS NOT NULL
        ORDER BY distance_meters ASC
        LIMIT max_results;
END
$$ LANGUAGE plpgsql;

-- Функция для обновления геолокации станции
CREATE OR REPLACE FUNCTION update_station_geolocation(
    station_id INTEGER,
    station_latitude DOUBLE PRECISION,
    station_longitude DOUBLE PRECISION
)
    RETURNS VOID AS $$
BEGIN
    UPDATE charge_box
    SET geolocation = ST_SetSRID(ST_MakePoint(station_longitude, station_latitude), 4326)
    WHERE id = station_id;
END
$$ LANGUAGE plpgsql;

--rollback DROP FUNCTION IF EXISTS update_station_geolocation(INTEGER, DOUBLE PRECISION, DOUBLE PRECISION);
--rollback DROP FUNCTION IF EXISTS find_nearest_stations(DOUBLE PRECISION, DOUBLE PRECISION, INTEGER);
--rollback DROP FUNCTION IF EXISTS find_stations_in_radius(DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER);