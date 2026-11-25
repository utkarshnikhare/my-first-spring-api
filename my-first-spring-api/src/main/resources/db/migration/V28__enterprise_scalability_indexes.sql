-- V28: Enterprise Scalability & Performance Indexes for 1,000+ Concurrent Users
--
-- 1. kitchen_served_societies: multi-society discovery filter index
-- Accelerates buyer discovery lookups by society_id (O(log N) instead of sequential scan).
CREATE INDEX IF NOT EXISTS idx_kitchen_served_societies_society ON kitchen_served_societies (society_id);

-- 2. products: marketplace active offerings hot path
-- Accelerates active product listings sorted by creation date without scanning archived history.
CREATE INDEX IF NOT EXISTS idx_products_available_today_created ON products (available_today, created_at DESC);

-- 3. users: admin pending approvals and role-based listings
-- Optimizes admin queries filtering by user role and seller approval status.
CREATE INDEX IF NOT EXISTS idx_users_role_approval ON users (role, seller_approval_status);

-- 4. orders: multi-condition lifecycle and payment filter index
-- Optimizes dashboard aggregate calculations across active orders and payment verification states.
CREATE INDEX IF NOT EXISTS idx_orders_status_composite ON orders (order_status, payment_status, created_at DESC);
