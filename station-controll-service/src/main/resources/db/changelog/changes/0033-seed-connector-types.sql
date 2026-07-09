--liquibase formatted sql
--changeset bagyshan:0033-seed-connector-types
--comment: Seed standard connector types with app-linked codes (idempotent by code). Клиенты рисуют встроенную SVG-иконку по коду.

INSERT INTO connector_type (connector_type_name, connector_type_code)
SELECT v.name, v.code
FROM (VALUES
    ('Type 1', 'TYPE1'),
    ('Type 2', 'TYPE2'),
    ('CCS Combo 1', 'CCS1'),
    ('CCS Combo 2', 'CCS2'),
    ('CHAdeMO', 'CHADEMO'),
    ('GB/T', 'GBT'),
    ('GB/T (DC)', 'GBT_DC'),
    ('NACS', 'NACS')
) AS v(name, code)
WHERE NOT EXISTS (
    SELECT 1 FROM connector_type ct WHERE ct.connector_type_code = v.code
);

--rollback DELETE FROM connector_type WHERE connector_type_code IN ('TYPE1','CCS1','GBT_DC','NACS') AND id NOT IN (SELECT connector_type_id FROM connector WHERE connector_type_id IS NOT NULL);
