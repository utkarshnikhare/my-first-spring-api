ALTER TABLE products
    ADD COLUMN IF NOT EXISTS quick_post_request_id VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS uk_products_kitchen_quick_post_request
    ON products (kitchen_id, quick_post_request_id);
