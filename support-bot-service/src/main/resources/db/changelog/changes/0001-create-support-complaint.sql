--liquibase formatted sql
--changeset bagyshan:0001-create-support-complaint
--comment: Create support_complaint table (жалобы из Telegram-бота поддержки)

CREATE TABLE IF NOT EXISTS support_complaint (
    id                BIGSERIAL PRIMARY KEY,
    telegram_user_id  BIGINT,
    telegram_username VARCHAR(255),
    telegram_name     VARCHAR(255),
    contact           VARCHAR(255),
    message           TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_support_complaint_created_at ON support_complaint (created_at DESC);

COMMENT ON COLUMN support_complaint.status IS 'NEW, IN_PROGRESS, RESOLVED';

--rollback DROP TABLE support_complaint;
