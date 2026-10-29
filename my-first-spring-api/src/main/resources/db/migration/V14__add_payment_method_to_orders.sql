-- Add payment_method column to orders table (DEF-05/DEF-06)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);