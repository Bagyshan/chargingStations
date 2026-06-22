--liquibase formatted sql
--changeset bagyshan:0011-turn-on-postgis
--comment: Enable PostGIS extension for spatial data support

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

--rollback DROP EXTENSION IF EXISTS postgis;
--rollback DROP EXTENSION IF EXISTS postgis_topology;