--liquibase formatted sql
--changeset bagyshan:0023-create-address
--comment: Create address table

CREATE TABLE IF NOT EXISTS address (
    id SERIAL PRIMARY KEY,
    address_name VARCHAR(500) NOT NULL
);

--rollback DROP TABLE address;
