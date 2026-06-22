--liquibase formatted sql
--changeset bagyshan:0024-add-address-to-charge-box
--comment: Add address relation to charge_box

ALTER TABLE charge_box
    ADD COLUMN address_id INTEGER;

ALTER TABLE charge_box
    ADD CONSTRAINT fk_charge_box_address
        FOREIGN KEY (address_id)
            REFERENCES address(id)
            ON DELETE SET NULL;

--rollback
-- ALTER TABLE charge_box DROP CONSTRAINT fk_charge_box_address;
-- ALTER TABLE charge_box DROP COLUMN address_id;