--liquibase formatted sql
--changeset bagyshan:0005-create-device-tokens splitStatements:false
--comment: Create device_tokens table (FCM push tokens per user device)

CREATE TABLE IF NOT EXISTS device_tokens (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(36) NOT NULL,
    token VARCHAR(512) NOT NULL,
    platform VARCHAR(16) NOT NULL DEFAULT 'android',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_keycloak_id ON device_tokens(keycloak_id);

--rollback DROP TABLE device_tokens;
