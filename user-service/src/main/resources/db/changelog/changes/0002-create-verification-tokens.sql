--liquibase formatted sql
--changeset bagyshan:0002-create-verification-tokens
--comment: Create email verification tokens table

CREATE TABLE IF NOT EXISTS verification_tokens (
   id BIGSERIAL PRIMARY KEY,
   token VARCHAR(255) NOT NULL UNIQUE,
   user_id BIGINT NOT NULL,
   token_type VARCHAR(50) NOT NULL, -- EMAIL_VERIFICATION, PHONE_VERIFICATION
   expires_at TIMESTAMP NOT NULL,
   used BOOLEAN NOT NULL DEFAULT FALSE,
   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

   CONSTRAINT fk_verification_tokens_user
       FOREIGN KEY (user_id)
           REFERENCES users(id)
           ON DELETE CASCADE
);

CREATE INDEX idx_verification_tokens_token ON verification_tokens(token);
CREATE INDEX idx_verification_tokens_user_id ON verification_tokens(user_id);
CREATE INDEX idx_verification_tokens_expires_at ON verification_tokens(expires_at);

--rollback DROP TABLE verification_tokens;