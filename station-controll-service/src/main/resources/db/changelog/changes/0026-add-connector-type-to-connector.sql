--liquibase formatted sql
--changeset bagyshan:0026-add-connector-type-to-connector
--comment: Add connector_type relation

ALTER TABLE connector
    ADD COLUMN connector_type_id INTEGER;

ALTER TABLE connector
    ADD CONSTRAINT fk_connector_connector_type
        FOREIGN KEY (connector_type_id)
            REFERENCES connector_type(id)
            ON DELETE SET NULL;

--rollback
-- ALTER TABLE connector DROP CONSTRAINT fk_connector_connector_type;
-- ALTER TABLE connector DROP COLUMN connector_type_id;