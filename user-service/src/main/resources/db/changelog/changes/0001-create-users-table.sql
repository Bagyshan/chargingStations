--liquibase formatted sql
--changeset bagyshan:0001-create-users-table splitStatements:false
--comment: Create users table with additional fields

CREATE TABLE IF NOT EXISTS users (
     id BIGSERIAL PRIMARY KEY,
     keycloak_id VARCHAR(36) NOT NULL UNIQUE,
     email VARCHAR(255) NOT NULL UNIQUE,
     phone VARCHAR(20),
     first_name VARCHAR(100),
     last_name VARCHAR(100),
     role VARCHAR(50) NOT NULL DEFAULT 'USER',
     email_verified BOOLEAN NOT NULL DEFAULT FALSE,
     phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
     active BOOLEAN NOT NULL DEFAULT TRUE,
     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
     last_login_at TIMESTAMP,
     metadata JSONB
);

-- Индексы для быстрого поиска
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_active ON users(active);

-- Триггер для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

--rollback DROP TRIGGER update_users_updated_at ON users;
--rollback DROP FUNCTION update_updated_at_column();
--rollback DROP TABLE users;