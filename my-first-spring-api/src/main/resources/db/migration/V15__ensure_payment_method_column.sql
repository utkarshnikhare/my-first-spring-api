-- V15: additive safety net for production databases where V14 did not run or the
-- Flyway history is incomplete. This is intentionally forward-only and does not
-- modify existing order rows or rewrite Flyway history.
ALTER TABLE IF EXISTS orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);
