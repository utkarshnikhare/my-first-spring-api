-- Existing order items retain NULL: their historical name and unit are unknown.
ALTER TABLE order_items ADD COLUMN product_name_snapshot VARCHAR(255);
ALTER TABLE order_items ADD COLUMN price_unit_snapshot VARCHAR(255);
