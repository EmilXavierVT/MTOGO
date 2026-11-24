-- Run once against an existing database before deploying issue #36.
-- Preserve historical values; do not recalculate old commissions or net amounts.
BEGIN;

ALTER TABLE orders
    ALTER COLUMN net_amount TYPE numeric(13, 3),
    ALTER COLUMN commission TYPE numeric(12, 2);

COMMIT;
