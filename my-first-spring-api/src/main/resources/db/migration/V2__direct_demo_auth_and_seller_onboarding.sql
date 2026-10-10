ALTER TABLE users ADD COLUMN password_hash varchar(255);
ALTER TABLE users ADD COLUMN seller_whatsapp_number varchar(32);
ALTER TABLE users ADD COLUMN seller_alternate_contact varchar(32);
ALTER TABLE users ADD COLUMN seller_category varchar(30);

ALTER TABLE kitchens ADD COLUMN speciality varchar(255);
ALTER TABLE kitchens DROP CONSTRAINT IF EXISTS kitchens_seller_type_check;
ALTER TABLE kitchens ADD CONSTRAINT kitchens_seller_type_check
    CHECK (seller_type IN ('KITCHEN', 'HOMEMADE_PRODUCTS', 'BOTH'));
