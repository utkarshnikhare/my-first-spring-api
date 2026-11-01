ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_configuration_snapshot VARCHAR(20);
