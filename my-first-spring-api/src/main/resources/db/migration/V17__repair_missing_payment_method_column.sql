-- Repair schema drift when an earlier database recorded migrations without this column.
-- This is additive, preserves existing order rows, and does not alter migration history.
ALTER TABLE IF EXISTS orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);
