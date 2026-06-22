--liquibase formatted sql
--changeset analyst:0010-add-user-columns-to-connector
--comment: Паритет со station-controll — заряжающий/бронирующий пользователь, версия

ALTER TABLE connector ADD COLUMN IF NOT EXISTS charging_user_id VARCHAR(100);
ALTER TABLE connector ADD COLUMN IF NOT EXISTS booking_user_id VARCHAR(100);
ALTER TABLE connector ADD COLUMN IF NOT EXISTS version BIGINT;

--rollback ALTER TABLE connector DROP COLUMN version;
--rollback ALTER TABLE connector DROP COLUMN booking_user_id;
--rollback ALTER TABLE connector DROP COLUMN charging_user_id;
