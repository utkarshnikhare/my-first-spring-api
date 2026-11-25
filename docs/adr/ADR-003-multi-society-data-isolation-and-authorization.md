# ADR 003: Multi-Society and Multi-Tenant Data Isolation Architecture

## Status
Accepted

## Context
SocioMart is designed to scale across multiple residential societies and geographical areas. Multi-society data isolation is a critical security and compliance requirement:
- A buyer residing in Society A must only see kitchens and offerings that explicitly deliver to Society A.
- A seller operating from Society A can configure delivery coverage for Society A, Society B, or entire areas.
- Sellers must never see or manipulate orders, offerings, or customer details belonging to other sellers.
- Buyers must never access other buyers' orders, profiles, or draft checkouts.

## Decision & Enforced Controls

### 1. Delivery Coverage and Visibility Isolation
- Sourced dynamically via authoritative relational mapping in `kitchen_served_societies` and `KitchenVisibility.isServiceAreaVisible(Kitchen, User)`.
- When an authenticated buyer browses the marketplace, `KitchenVisibility` strictly matches the buyer's `societyRef.id` against the seller's `servedSocieties` collection.
- Logged-out visitors can browse public discovery, but the moment checkout/order creation is initiated, `OrderService.createOrUpdateDraftOrder` enforces `isServiceAreaVisible(kitchen, buyer)` server-side, preventing out-of-coverage orders.

### 2. Multi-Society Database Optimization
- In Flyway migration `V28__enterprise_scalability_indexes.sql`, added index:
  `CREATE INDEX IF NOT EXISTS idx_kitchen_served_societies_society ON kitchen_served_societies (society_id);`
  This transforms society-level filtering queries from full table scans into `O(log N)` index seeks, allowing rapid scaling to hundreds of societies.

### 3. Server-Side Object-Level Authorization (IDOR Defense)
- **Order Scoping:** In `OrderService` and `SellerAppService`, all order modifications (status updates, delivery tracking, cancellations) require:
  `if (!isOwnedBySeller(order.getKitchen(), seller)) throw new SellerNotAuthorizedException("Not authorized");`
  The seller identity is retrieved from the authenticated server-side session (`HttpSession`), never from user-supplied request parameters.
- **Buyer Isolation:** Order queries and payment status updates enforce:
  `if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) throw new OrderNotFoundException(orderId);`
  Cross-buyer order reads return a neutral 404 response to avoid information leakage.
- **Admin Isolation:** Super Admin operations (`/api/superadmin/**`) require `ROLE_SUPER_ADMIN`; Admin operations (`/api/admin/**`) require `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` checked at the Spring Security filter chain level.

## Consequences
- Guaranteed multi-society isolation with zero reliance on frontend checks.
- Complete protection against Insecure Direct Object Reference (IDOR) attacks.
