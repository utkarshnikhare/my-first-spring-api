-- SocioMart Demo Data SQL
-- This script populates demo sellers, kitchens, and menu items.
-- Only executed when 'demo' or 'dev' profile is active (spring.sql.init.mode=always).

-- ============================================
-- DEMO SELLERS (9 sellers: 8 approved, 1 pending)
-- ============================================
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Aarti', '9100000001', 'A-101', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Meena', '9100000002', 'A-102', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- ============================================
-- DEMO KITCHENS (8 approved + 1 pending)
-- ============================================
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('aarti-kitchen', 'Aarti Kitchen', 'North Indian home-style meals', 'North Indian home food cooked to order.', 'Pride World City', 'Tower A', 'aarti@okhdfc', true, '9:30 PM', 4.7, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('moms-special-kitchen', 'Mom''s Special Kitchen', 'Wholesome recipes from mom''s kitchen', 'Comfort food that tastes like home.', 'Pride World City', 'Tower A', 'meena@okhdfc', true, '9:00 PM', 4.8, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('ravi-tiffin-service', 'Ravi Tiffin Service', 'Daily tiffin and thali service', 'Wholesome daily meals delivered.', 'Pride World City', 'Tower A', 'ravi@okhdfc', true, '12:00 PM', 4.5, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('lakshmi-south-indian', 'Lakshmi South Indian', 'Authentic South Indian dosas and idlis', 'Traditional South Indian recipes.', 'Pride World City', 'Tower A', 'lakshmi@okhdfc', true, '10:00 PM', 4.6, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- Aarti Kitchen (kitchen_id=1)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Butter Chicken', 'Creamy tomato curry with tender chicken', 250, 'plate', true, 20, 15, 4.8, false, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Dal Makhani', 'Slow-cooked black lentils with butter', 150, 'plate', true, 25, 20, 4.6, false, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Masala Chai', 'Spiced Indian tea with milk', 20, 'cup', true, 50, 40, 4.5, false, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Mom's Special Kitchen (kitchen_id=2)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Veg Thali', 'Complete meal with rice, dal, sabzi, roti', 120, 'plate', true, 30, 25, 4.7, false, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Aloo Paratha', 'Stuffed potato paratha with curd', 60, 'piece', true, 20, 15, 4.6, false, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- Suresh Biryani House (kitchen_id=5)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Chicken Biryani', 'Hyderabadi dum biryani with raita', 220, 'plate', true, 25, 20, 4.9, false, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Veg Biryani', 'Fragrant biryani with mixed vegetables', 160, 'plate', true, 20, 15, 4.6, false, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Farah Healthy Bites (kitchen_id=6)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Poha', 'Light poha with peanuts and peas', 40, 'plate', true, 20, 15, 4.5, false, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Fresh Veg Salad', 'Garden bowl with sprouts and seeds', 120, 'bowl', true, 10, 6, 4.4, false, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Geeta Snack Corner (kitchen_id=7)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Samosa', 'Crisp samosa with spiced potato filling', 20, 'piece', true, 30, 18, 4.6, false, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Gulab Jamun', 'Hot gulab jamuns in rose syrup', 50, 'plate', true, 12, 6, 4.8, false, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Arjun Punjabi Dhaba (kitchen_id=8)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Paneer Butter Masala', 'Rich creamy paneer curry', 200, 'plate', true, 20, 15, 4.7, false, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Lassi', 'Thick sweet lassi with cardamom', 50, 'glass', true, 20, 12, 4.5, false, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Pending kitchen (kitchen_id=9) - must never appear publicly
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Hidden Chicken Curry', 'Should never appear publicly', 200, 'plate', false, 10, 5, 3.0, false, 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Demo data seeded flag
INSERT INTO platform_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('demo_data_seeded', 'true', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Ravi Tiffin Service (kitchen_id=3)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('North Indian Thali', 'Rice, dal, sabzi, 4 rotis, papad', 100, 'plate', true, 50, 40, 4.5, false, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Lakshmi South Indian (kitchen_id=4)
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Masala Dosa', 'Crispy dosa with spiced potato filling', 70, 'piece', true, 30, 25, 4.6, false, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (name, description, price, price_unit, available_today, max_quantity, remaining_quantity, rating, is_preorder, kitchen_id, created_at, updated_at)
VALUES ('Filter Coffee', 'South Indian style filter coffee', 30, 'cup', true, 40, 35, 4.7, false, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('suresh-biryani-house', 'Suresh Biryani House', 'Hyderabadi biryani and kebabs', 'Authentic Hyderabadi dum biryani.', 'Pride World City', 'Tower A', 'suresh@okhdfc', true, '10:30 PM', 4.9, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('farah-healthy-bites', 'Farah Healthy Bites', 'Salads, bowls, and clean eating', 'Nutritious meals for health-conscious.', 'Pride World City', 'Tower A', 'farah@okhdfc', true, '8:00 PM', 4.4, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('geeta-snack-corner', 'Geeta Snack Corner', 'Evening snacks and tea-time favorites', 'Crispy samosas, pakoras, and sweets.', 'Pride World City', 'Tower A', 'geeta@okhdfc', true, '7:00 PM', 4.7, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('arjun-punjabi-dhaba', 'Arjun Punjabi Dhaba', 'Rich Punjabi curries and butter rotis', 'Dhaba-style Punjabi food.', 'Pride World City', 'Tower A', 'arjun@okhdfc', true, '11:00 PM', 4.6, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO kitchen (slug, name, short_description, description, society, building, upi_id, available_today, order_deadline, rating, seller_id, created_at, updated_at)
VALUES ('priya-pending-kitchen', 'Priya Pending Kitchen', 'Pending approval kitchen', 'This kitchen is pending admin approval.', 'Pride World City', 'Tower A', 'priya@okhdfc', false, '9:00 PM', 3.0, 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Ravi', '9100000003', 'B-201', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Lakshmi', '9100000004', 'C-301', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Suresh', '9100000005', 'B-210', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Farah', '9100000006', 'D-401', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Geeta', '9100000007', 'D-402', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Arjun', '9100000008', 'E-501', 'Pride World City', 'Tower A', 'SELLER', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (name, mobile_number, flat_house_number, society, building, role, seller_approval_status, approved_at, created_at, updated_at)
VALUES ('Priya', '9100000009', 'E-502', 'Pride World City', 'Tower A', 'SELLER', 'PENDING', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);