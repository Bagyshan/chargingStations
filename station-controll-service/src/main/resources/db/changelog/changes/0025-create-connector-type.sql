--liquibase formatted sql
--changeset bagyshan:0025-create-connector-type
--comment: Create connector_type table

CREATE TABLE IF NOT EXISTS connector_type (
    id SERIAL PRIMARY KEY,
    connector_type_name VARCHAR(255) NOT NULL,
    connector_type_icon VARCHAR(1024)
);

--rollback DROP TABLE connector_type;