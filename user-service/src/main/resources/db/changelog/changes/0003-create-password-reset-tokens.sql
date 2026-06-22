--liquibase formatted sql
--changeset bagyshan:0003-create-password-reset-tokens
--comment: Create password reset tokens table

CREATE TABLE IF NOT EXISTS password_reset_tokens (
     id BIGSERIAL PRIMARY KEY,
     token VARCHAR(255) NOT NULL UNIQUE,
     user_id BIGINT NOT NULL,
     expires_at TIMESTAMP NOT NULL,
     used BOOLEAN NOT NULL DEFAULT FALSE,
     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

     CONSTRAINT fk_password_reset_tokens_user
         FOREIGN KEY (user_id)
             REFERENCES users(id)
             ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

--rollback DROP TABLE password_reset_tokens;