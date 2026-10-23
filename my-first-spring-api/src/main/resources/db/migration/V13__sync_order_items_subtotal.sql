-- V13__sync_order_items_subtotal.sql
-- Ensure legacy subtotal column on order_items is safely defined, drops NOT NULL, and sets default

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS subtotal numeric(10,2);
ALTER TABLE order_items ALTER COLUMN subtotal DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN subtotal SET DEFAULT 0;
UPDATE order_items SET subtotal = COALESCE(price * quantity, price, 0) WHERE subtotal IS NULL;
