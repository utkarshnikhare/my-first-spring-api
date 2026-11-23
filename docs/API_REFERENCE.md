# SocioMart — REST API Reference

This document provides a reference for the REST API endpoints supported by SocioMart across the Buyer, Seller, Admin, and Operational controllers.

---

## 1. Authentication & Session APIs (`AuthController`, `AdminLoginController`)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Register a new Buyer account with mobile number and society. |
| `POST` | `/api/auth/login` | Public | Authenticate a user via mobile number and password; establishes HTTP session. |
| `POST` | `/api/auth/logout` | Authenticated | Invalidate server-side session and purge security context. |
| `GET` | `/api/auth/me` | Public / Auth | Return the currently authenticated user's profile and active roles. |
| `POST` | `/admin/login` | Public | Dedicated Admin authentication entry point. |
| `POST` | `/api/auth/forgot-password` | Public | Request a password reset token. |
| `POST` | `/api/auth/reset-password` | Public | Reset password using verified token. |

---

## 2. Buyer APIs (`DiscoveryController`, `BuyerOrderController`, `BuyerProfileController`, `FavouriteController`)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/discovery/categories` | Public | List food and product categories with active item counts. |
| `GET` | `/api/discovery/items` | Public | List orderable food items filtered by buyer's society. |
| `GET` | `/api/discovery/kitchens` | Public | List community kitchens currently taking orders in the buyer's area. |
| `GET` | `/api/kitchens/{slugOrId}` | Public | Get detailed kitchen profile, orderable items, and upcoming pre-orders. |
| `GET` | `/api/search` | Public | Full-text search across kitchens, dishes, and offerings. |
| `GET` | `/api/buyer/orders/draft` | Buyer | Fetch the buyer's current active cart/draft order. |
| `POST` | `/api/buyer/orders/draft` | Buyer | Create or update items in the current active draft order. |
| `POST` | `/api/buyer/orders/place` | Buyer | Place order from draft; reserves inventory and creates order record. |
| `GET` | `/api/buyer/orders` | Buyer | List all historical and active orders placed by the current buyer. |
| `GET` | `/api/buyer/orders/{id}` | Buyer | Get complete order details, item breakdown, and delivery status. |
| `GET` | `/api/favourites` | Buyer | List buyer's saved favourite kitchens. |
| `POST` | `/api/favourites/{kitchenId}` | Buyer | Toggle saved favourite status for a kitchen. |
| `GET` | `/api/buyer/profile` | Buyer | Fetch current buyer profile details. |
| `PUT` | `/api/buyer/profile` | Buyer | Update buyer name, flat number, or society. |

---

## 3. Seller Studio APIs (`SellerController`, `SellerAppController`, `RecurringScheduleController`)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/seller/dashboard` | Seller | Fetch core seller overview: kitchen status, today's metrics, and offerings list. |
| `GET` | `/api/seller-app/orders/summary` | Seller | Fetch aggregated order counts and breakdown by offering. |
| `GET` | `/api/seller/kitchen` | Seller | Retrieve seller's owned kitchen profile details. |
| `PUT` | `/api/seller/kitchen` | Seller | Update kitchen display name, description, photo URL, and UPI ID. |
| `POST` | `/api/seller/products` | Seller | Create a new food or homemade product offering. |
| `PUT` | `/api/seller/products/{id}` | Seller | Update offering pricing, description, cutoff time, or ready time. |
| `POST` | `/api/seller/products/{id}/pause` | Seller | Pause taking orders for an offering. |
| `POST` | `/api/seller/products/{id}/sold-out`| Seller | Mark offering immediately as Sold Out for today. |
| `DELETE`| `/api/seller/products/{id}` | Seller | Soft delete / archive a product offering. |
| `GET` | `/api/seller/orders` | Seller | List customer orders placed with the seller's kitchen. |
| `POST` | `/api/seller/orders/{id}/delivered`| Seller | Record order as delivered. |
| `POST` | `/api/seller/orders/{id}/verify-payment` | Seller | Verify and record customer payment status as PAID. |
| `GET` | `/api/seller/schedules` | Seller | List recurring schedules owned by the seller. |
| `GET` | `/api/seller/schedules/{id}/occurrences` | Seller | List individual dated occurrences for a recurring schedule. |
| `POST` | `/api/seller/schedules/{productId}` | Seller | *(Sunset - Returns HTTP 410 GONE)* New creation disabled by design. |
| `POST` | `/api/seller/schedules/{id}/end` | Seller | Terminate a recurring schedule; ends future occurrences cleanly. |

---

## 4. Admin Console APIs (`AdminController`, `SuperAdminController`, `AnalyticsController`)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/dashboard` | Admin | Overall operations overview: active orders, pending approvals, revenue. |
| `GET` | `/api/admin/sellers` | Admin | List registered sellers with approval status and society coverage. |
| `POST` | `/api/admin/sellers/{id}/approve` | Admin | Approve a pending seller application. |
| `POST` | `/api/admin/sellers/{id}/reject` | Admin | Reject a seller application with reason. |
| `GET` | `/api/admin/buyers` | Admin | List registered buyers with society and order metrics. |
| `GET` | `/api/admin/orders` | Admin | Master order registry across all kitchens and societies with status filters. |
| `GET` | `/api/admin/exports/{domain}.csv` | Admin | Export filter-aware CSV reports (`orders`, `sellers`, `buyers`, `analytics`). |
| `GET` | `/api/admin/analytics/summary` | Admin | Platform traffic, order volumes, and Recorded Order Value (ROV) analytics. |
| `GET` | `/api/superadmin/areas` | Super Admin | Master location list of operating areas. |
| `POST` | `/api/superadmin/areas` | Super Admin | Provision a new operating area. |
| `POST` | `/api/superadmin/societies` | Super Admin | Provision a new society attached to an area. |

---

## 5. System Health & Observability APIs (`HealthController`, `VersionController`)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/health` | Public | System health status; returns `{"status":"UP"}` and database ping. |
| `GET` | `/api/version` | Public | Current deployed commit SHA, branch name, and build timestamp. |
