-- Hot-path indexes for tables that V1 created WITHOUT any secondary index.
-- Only queries that (a) run on every dashboard load and (b) currently have no
-- covering index are included. The audit/analytics/aggregate/occurrence tables
-- already carry their indexes from V1; nothing there needs adding.
--
-- orders: seller dashboard lists (findByKitchenOrderByCreatedAtDesc),
-- buyer history (findByBuyerOrderByCreatedAtDesc), reminder scans and
-- analytics windows (countByCreatedAtAfter / createdAt BETWEEN).
CREATE INDEX IF NOT EXISTS idx_orders_kitchen_created ON orders (kitchen_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_buyer_created ON orders (buyer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders (created_at);

-- order_items: joined to orders on every order read (items fetch / N+1 avoidance).
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);

-- enquiries: buyer history and seller inbox listings.
CREATE INDEX IF NOT EXISTS idx_enquiries_user ON enquiries (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_enquiries_kitchen ON enquiries (kitchen_id);

-- products/kitchens: per-seller and per-kitchen product listings.
CREATE INDEX IF NOT EXISTS idx_products_kitchen ON products (kitchen_id);
CREATE INDEX IF NOT EXISTS idx_kitchens_seller ON kitchens (seller_id);
