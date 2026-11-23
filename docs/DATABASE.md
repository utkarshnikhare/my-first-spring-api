# SocioMart — Database Architecture & Schema Specification

This document details the database architecture, entity-relationship models, indexing strategies, transaction boundaries, and Flyway migration practices for SocioMart.

---

## 1. Database Architecture & Engine

- **Production**: Supabase Managed PostgreSQL 16 (AWS Tokyo `ap-northeast-1`).
- **Connection Pooling**: PgBouncer transaction-mode connection pool (`aws-0-ap-northeast-1.pooler.supabase.com:5432`) configured with HikariCP on Spring Boot.
- **Local / Unit Testing**: Embedded in-memory H2 database running in PostgreSQL compatibility mode (`MODE=PostgreSQL`).
- **Integration CI Testing**: Native Dockerized PostgreSQL 16 Testcontainers (`testcontainers-postgresql`).

---

## 2. Flyway Schema Migrations

Flyway strictly manages all production database schemas. Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`, ensuring that schema creation or alteration is never attempted at runtime by the ORM.

### Migration Chain Summary (V1 to V27)
- **V1__initial_schema.sql**: Base tables for users, roles, areas, societies, kitchens, products, orders, order items, and notifications.
- **V2__direct_demo_auth_and_seller_onboarding.sql**: Added demo auth fields and onboarding flags.
- **V3__kitchen_public_url_slug.sql**: Added unique URL slug column and slug alias registry for shareable kitchen links.
- **V4__spring_session_tables.sql**: Created persistent session storage tables (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`).
- **V5__hot_path_indexes.sql**: High-performance composite indexes for order queries, active offerings, and society filtering.
- **V6__password_reset_tokens.sql**: Secure token hashing table for self-service password recovery.
- **V7__add_email_to_users.sql** & **V9__add_email_to_users_if_missing.sql**: Added optional email contact field.
- **V8__password_reset_requests.sql**: Password reset audit logs.
- **V10__harden_product_and_order_defaults.sql**: Enforced non-null constraints and monetary precision defaults.
- **V11__sync_order_status_column.sql** to **V13__sync_order_items_subtotal.sql**: Data integrity synchronization for order item subtotals.
- **V14__add_payment_method_to_orders.sql** & **V17__repair_missing_payment_method_column.sql**: Added `payment_method` enum column to orders.
- **V15__add_orders_open_from.sql**: Dynamic opening hours configuration for kitchen storefronts.
- **V16__add_orderable_quick_posts.sql**: Added fast-turnaround instant quick post offerings.
- **V18__add_quick_post_request_id.sql**: Idempotency keys for quick post submissions.
- **V19__seller_payment_configuration.sql**: Seller UPI and payment acceptance preferences.
- **V21__snapshot_order_payment_configuration.sql**: Order-time payment snapshotting to preserve historical transaction records.
- **V22__add_occurrence_composite_indexes.sql**: High-throughput indexes for recurring schedule occurrence lookups.
- **V23__add_manual_sold_out_state.sql**: Immediate seller inventory cutoff controls.
- **V24__add_order_item_snapshots.sql**: Immutable order item name and pricing snapshots at checkout.
- **V25__add_explicit_draft_marker.sql**: Temporary draft reservation markers.
- **V26__add_draft_availability_type.sql**: Cart draft availability tracking.
- **V27__add_product_archive_state.sql**: Soft deletion / archive state for retired seller products.

*Strict Rule: Migrations V1 through V27 are immutable historical records. They must never be rewritten, deleted, or reordered.*

---

## 3. Core Domain Entities & Relationships

```
[ Area ] 1 ──< * [ Society ]
                     │
                     ├────────────< * [ User (Buyer) ]
                     │                      │
                     │                      └── 1 ──< * [ Order ] 1 ──< * [ OrderItem ]
                     │                                     │                 │
                     └────────────< * [ Kitchen ]          │                 │
                                         │                 │                 │
                                         └── 1 ──< * [ Product ] <───────────┘
                                                        │
                                                        └── 1 ──< 1 [ RecurringSchedule ]
                                                                          │
                                                                          └── 1 ──< * [ Occurrence ]
```

---

## 4. Concurrency & Data Integrity Rules

1. **Stock Reservation & Cart Concurrency**:
   - Order placement validates real-time quantity against `Product.quantity` or `Occurrence.quantity`.
   - Inventory decrements occur inside Spring `@Transactional` boundaries, preventing double-selling or negative inventory balances.
2. **Transaction Isolation**:
   - Default isolation level: `READ_COMMITTED`.
   - Critical order placement queries execute with row-level locks (`SELECT ... FOR UPDATE` where needed).
