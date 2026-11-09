ALTER TABLE products ADD COLUMN sold_out_manually boolean NOT NULL DEFAULT false;
ALTER TABLE products ADD COLUMN manual_reactivation_required boolean NOT NULL DEFAULT false;
