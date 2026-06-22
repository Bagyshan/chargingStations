--liquibase formatted sql
--changeset bagyshan:0001-first-test-migration
--comment: Create balance table

CREATE TABLE IF NOT EXISTS balance (
    user_id uuid PRIMARY KEY NOT NULL,
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0,
    is_booking BOOLEAN NOT NULL DEFAULT false
);

--rollback DROP TABLE balance;