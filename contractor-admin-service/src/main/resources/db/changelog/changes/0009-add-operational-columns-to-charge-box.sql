--liquibase formatted sql
--changeset analyst:0009-add-operational-columns-to-charge-box
--comment: Паритет со station-controll — административный статус, online/last_seen, координаты, версия

ALTER TABLE charge_box ADD COLUMN IF NOT EXISTS service_status VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE';
ALTER TABLE charge_box ADD COLUMN IF NOT EXISTS online BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE charge_box ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE charge_box ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE charge_box ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE charge_box ADD COLUMN IF NOT EXISTS version BIGINT;

--rollback ALTER TABLE charge_box DROP COLUMN version;
--rollback ALTER TABLE charge_box DROP COLUMN longitude;
--rollback ALTER TABLE charge_box DROP COLUMN latitude;
--rollback ALTER TABLE charge_box DROP COLUMN last_seen_at;
--rollback ALTER TABLE charge_box DROP COLUMN online;
--rollback ALTER TABLE charge_box DROP COLUMN service_status;
