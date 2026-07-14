--liquibase formatted sql
--changeset bagyshan:0034-add-last-charging-ended-at-to-connector
--comment: Track when the last charging session ended per connector (used for the post-charging booking cooldown)

ALTER TABLE connector
    ADD COLUMN last_charging_ended_at timestamptz DEFAULT null;

--rollback ALTER TABLE connector DROP COLUMN last_charging_ended_at;
