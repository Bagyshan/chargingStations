--liquibase formatted sql
--changeset bagyshan:0002-topup
--comment: Create top_up table to track wallet top-up invoices and history

CREATE TABLE IF NOT EXISTS top_up (
    id          uuid PRIMARY KEY,
    user_id     uuid           NOT NULL,
    order_id    varchar(64)    NOT NULL UNIQUE,
    invoice_id  varchar(32),
    trans_id    varchar(32),
    amount      DECIMAL(12, 2) NOT NULL,
    currency    varchar(3)     NOT NULL DEFAULT 'KGS',
    status      varchar(16)    NOT NULL DEFAULT 'PENDING',
    description varchar(255),
    qr_url      text,
    link_app    text,
    paylink_url text,
    test        BOOLEAN        NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    paid_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_top_up_user_id ON top_up (user_id);
CREATE INDEX IF NOT EXISTS idx_top_up_invoice_id ON top_up (invoice_id);
CREATE INDEX IF NOT EXISTS idx_top_up_status ON top_up (status);

--rollback DROP TABLE top_up;
