-- V10__harden_product_and_order_defaults.sql
-- Harden product availability defaults and order status defaults.

ALTER TABLE products ADD COLUMN IF NOT EXISTS is_available boolean DEFAULT true;
ALTER TABLE products ALTER COLUMN is_available SET DEFAULT true;
ALTER TABLE products ALTER COLUMN is_available DROP NOT NULL;

ALTER TABLE orders ALTER COLUMN order_status SET DEFAULT 'PENDING';
ALTER TABLE orders ALTER COLUMN payment_status SET DEFAULT 'PENDING';
