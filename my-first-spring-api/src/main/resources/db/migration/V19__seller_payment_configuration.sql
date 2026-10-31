ALTER TABLE kitchens
    ADD COLUMN IF NOT EXISTS accepted_payment_mode VARCHAR(20);

ALTER TABLE kitchens
    ADD COLUMN IF NOT EXISTS gpay_identifier_type VARCHAR(20);

ALTER TABLE kitchens
    ADD COLUMN IF NOT EXISTS gpay_identifier VARCHAR(255);
