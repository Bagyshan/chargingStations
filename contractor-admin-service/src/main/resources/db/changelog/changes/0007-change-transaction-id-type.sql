--liquibase formatted sql
--changeset analyst:0007-change-transaction-id-type
--comment: поменял тип для transaction id

ALTER TABLE transaction ALTER COLUMN transaction_id TYPE bigint;

--rollback ALTER TABLE transaction ALTER COLUMN transaction_id TYPE int;