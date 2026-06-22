--liquibase formatted sql
--changeset bagyshan:0031-add-connectivity-to-charge-box
--comment: Online / last-seen for offline detection (station.connectivity events + scheduled sweep)

ALTER TABLE charge_box
    ADD COLUMN online BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE charge_box
    ADD COLUMN last_seen_at TIMESTAMP;

--rollback ALTER TABLE charge_box DROP COLUMN last_seen_at;
--rollback ALTER TABLE charge_box DROP COLUMN online;
