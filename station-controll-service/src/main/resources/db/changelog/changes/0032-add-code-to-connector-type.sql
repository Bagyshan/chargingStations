--liquibase formatted sql
--changeset bagyshan:0032-add-code-to-connector-type
--comment: Stable machine code for connector type (CCS2, TYPE2, ...) — join key with app/admin bundled SVG icons

ALTER TABLE connector_type
    ADD COLUMN IF NOT EXISTS connector_type_code VARCHAR(32);

-- Backfill existing rows from the name (same normalization as ConnectorTypeCodes.fromName).
-- CCS/CHAdeMO/NACS checked before Type, т.к. в «CCS Combo 2» встречается «Type 2».
UPDATE connector_type
SET connector_type_code = CASE
    WHEN lower(connector_type_name) LIKE '%chademo%' THEN 'CHADEMO'
    WHEN lower(connector_type_name) LIKE '%nacs%'
      OR lower(connector_type_name) LIKE '%tesla%'
      OR lower(connector_type_name) LIKE '%supercharger%' THEN 'NACS'
    WHEN (lower(connector_type_name) LIKE '%ccs%' OR lower(connector_type_name) LIKE '%combo%')
      AND (lower(connector_type_name) LIKE '%combo 1%'
        OR lower(connector_type_name) LIKE '%combo1%'
        OR lower(connector_type_name) LIKE '%ccs1%') THEN 'CCS1'
    WHEN lower(connector_type_name) LIKE '%ccs%' OR lower(connector_type_name) LIKE '%combo%' THEN 'CCS2'
    WHEN (lower(connector_type_name) LIKE '%gb/t%'
        OR lower(connector_type_name) LIKE '%gbt%'
        OR lower(connector_type_name) LIKE '%guobiao%')
      AND lower(connector_type_name) LIKE '%dc%' THEN 'GBT_DC'
    WHEN lower(connector_type_name) LIKE '%gb/t%'
      OR lower(connector_type_name) LIKE '%gbt%'
      OR lower(connector_type_name) LIKE '%guobiao%' THEN 'GBT'
    WHEN lower(connector_type_name) LIKE '%type 2%'
      OR lower(connector_type_name) LIKE '%type2%'
      OR lower(connector_type_name) LIKE '%mennekes%' THEN 'TYPE2'
    WHEN lower(connector_type_name) LIKE '%type 1%'
      OR lower(connector_type_name) LIKE '%type1%'
      OR lower(connector_type_name) LIKE '%j1772%' THEN 'TYPE1'
    ELSE 'OTHER'
END
WHERE connector_type_code IS NULL;

--rollback ALTER TABLE connector_type DROP COLUMN connector_type_code;
