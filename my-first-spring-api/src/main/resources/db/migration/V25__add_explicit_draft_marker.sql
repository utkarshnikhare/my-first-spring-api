-- NULL preserves the unknown publication history of existing offerings.
ALTER TABLE products ADD COLUMN publication_draft BOOLEAN;
