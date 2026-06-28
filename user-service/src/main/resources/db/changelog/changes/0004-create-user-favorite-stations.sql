--liquibase formatted sql
--changeset bagyshan:0004-create-user-favorite-stations splitStatements:false
--comment: Create user_favorite_stations table (membership only; station data stays in state-updater cache)

CREATE TABLE IF NOT EXISTS user_favorite_stations (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(36) NOT NULL,
    charge_box_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_favorite UNIQUE (keycloak_id, charge_box_id)
);

CREATE INDEX idx_user_fav_keycloak_id ON user_favorite_stations(keycloak_id);

--rollback DROP TABLE user_favorite_stations;
