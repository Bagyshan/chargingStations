--liquibase formatted sql
--changeset bagyshan:0017-add-power-and-kw-cost-to-charge-box
--comment: Alter table add power and kw cost to charge box

ALTER TABLE charge_box
    ADD COLUMN power VARCHAR(100),
    ADD COLUMN kw_cost DECIMAL(6, 2);

--rollback ALTER TABLE charge_box DROP COLUMN power, DROP COLUMN kw_cost;