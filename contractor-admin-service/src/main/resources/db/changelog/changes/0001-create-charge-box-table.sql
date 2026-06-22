--liquibase formatted sql
--changeset analyst:001-create-charge_box-table
--comment: Таблица станций (зарядных устройств)
CREATE TABLE IF NOT EXISTS charge_box (
    id SERIAL PRIMARY KEY,
    charge_box_id VARCHAR(255) NOT NULL UNIQUE,
    ocpp_protocol VARCHAR(50),
    charge_point_vendor VARCHAR(255),
    charge_point_model VARCHAR(255),
    charge_point_serial_number VARCHAR(255),
    charge_box_serial_number VARCHAR(255),
    firmware_version VARCHAR(100),
    iccid VARCHAR(50),
    imsi VARCHAR(50),
    meter_type VARCHAR(100),
    meter_serial_number VARCHAR(100),
    ocpp_tag VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    owner_id VARCHAR(100),
    power VARCHAR(20),
    kw_cost DECIMAL(10,2),
    booking_minute_cost DECIMAL(10,2),
    address_id INTEGER
);

CREATE INDEX idx_charge_box_owner ON charge_box(owner_id);

--rollback DROP INDEX idx_charge_box_owner;
--rollback DROP TABLE charge_box;

