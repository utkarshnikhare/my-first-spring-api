-- V12__sync_order_items_unit_price.sql
-- Ensure legacy unit_price and product_name columns on order_items are safely defined and nullable with defaults

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS unit_price numeric(10,2);
ALTER TABLE order_items ALTER COLUMN unit_price DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN unit_price SET DEFAULT 0;
UPDATE order_items SET unit_price = COALESCE(price, 0) WHERE unit_price IS NULL;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS product_name varchar(255);
ALTER TABLE order_items ALTER COLUMN product_name DROP NOT NULL;
UPDATE order_items oi SET product_name = p.name FROM products p WHERE oi.product_id = p.id AND oi.product_name IS NULL;
