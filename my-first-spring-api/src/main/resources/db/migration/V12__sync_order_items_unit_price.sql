-- V12__sync_order_items_unit_price.sql
-- Ensure legacy unit_price column on order_items is safely defined, drops NOT NULL, and sets default

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS unit_price numeric(10,2);
ALTER TABLE order_items ALTER COLUMN unit_price DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN unit_price SET DEFAULT 0;
UPDATE order_items SET unit_price = COALESCE(price, 0) WHERE unit_price IS NULL;
