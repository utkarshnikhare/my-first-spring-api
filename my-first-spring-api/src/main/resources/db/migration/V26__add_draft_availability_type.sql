-- Existing offerings have no reliable structured availability type.
ALTER TABLE products ADD COLUMN homemade_availability_type VARCHAR(32);
