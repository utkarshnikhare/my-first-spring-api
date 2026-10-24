-- V11__sync_order_status_column.sql
-- Ensure legacy status column is safely defined and nullable with default PENDING

ALTER TABLE orders ADD COLUMN IF NOT EXISTS status varchar(255) DEFAULT 'PENDING';
ALTER TABLE orders ALTER COLUMN status DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';
UPDATE orders SET status = COALESCE(order_status, 'PENDING') WHERE status IS NULL;
