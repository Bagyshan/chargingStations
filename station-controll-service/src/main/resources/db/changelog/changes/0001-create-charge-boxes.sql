--liquibase formatted sql
--changeset bagyshan:0001-create-charge-boxes
--comment: Create initial tables

CREATE TABLE IF NOT EXISTS charge_box (
    id SERIAL PRIMARY KEY,
    charge_box_id VARCHAR(255) NOT NULL UNIQUE,
    ocpp_protocol VARCHAR(64),
    charge_point_vendor VARCHAR(255),
    charge_point_model VARCHAR(255),
    charge_point_serial_number VARCHAR(255),
    charge_box_serial_number VARCHAR(255),
    firmware_version VARCHAR(128),
    iccid VARCHAR(128),
    imsi VARCHAR(128),
    meter_type VARCHAR(255),
    meter_serial_number VARCHAR(255),
    action_type VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

--rollback DROP TABLE charge_box;