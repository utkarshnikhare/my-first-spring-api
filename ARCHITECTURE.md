# SocioMart — Enterprise System & Application Architecture

This document provides the authoritative, end-to-end architectural specification of SocioMart, detailing the system topology, multi-society data isolation, concurrency models, database performance engineering, security boundaries, and high-availability deployment pipelines.

---

## 1. System Architecture Overview

SocioMart is an enterprise-grade, hyper-local community marketplace engineered to connect residents within residential societies with verified home chefs and artisan creators. 

The system utilizes a high-cohesion, low-friction **Modular Monolith** architecture with dual-origin edge delivery:

```mermaid
flowchart TD
    subgraph Clients["Client Layer (Mobile & Desktop)"]
        BuyerBrowser["Buyer PWA (index.html)"]
        SellerBrowser["Seller Studio (seller.html)"]
        AdminBrowser["Admin Console (admin.html)"]
    end

    subgraph CDN["Dual-Delivery Edge Layer"]
        RenderDirect["Direct Render Origin (Zero-Timeout)<br/>https://sociomart-demo.onrender.com"]
        NetlifyEdge["Netlify Edge CDN (Reverse Proxy)<br/>https://sociomart-frontend.netlify.app"]
    end

    subgraph Monitor["High-Availability Heartbeat"]
        UptimeRobot["UptimeRobot 24/7 Monitor<br/>Pings /actuator/health every 5m"]
    end

    subgraph Compute["Application Container (Render / Cloud Run)"]
        SpringApp["Java 21 / Spring Boot 3.4.x Service (Port 8081)"]
        StaticFilter["Static Resource Handler (/static)"]
        SecurityChain["Spring Security Chain (CSRF, RBAC, Sessions)"]
        RestControllers["REST Controllers (182 Endpoints)"]
        DomainServices["Transactional Domain Services"]
        HikariPool["HikariCP Connection Pool (Max 10)"]
    end

    subgraph DataStore["Managed Persistence Layer (Supabase AWS)"]
        PostgresDB[("PostgreSQL 16 Database")]
        FlywaySchema["Flyway Migrations (V1 to V28)"]
        SessionStore["Spring Session JDBC (SPRING_SESSION)"]
        DataIndexes["V28 Enterprise Indexes"]
    end

    BuyerBrowser -->|Direct Access| RenderDirect
    SellerBrowser -->|Direct Access| RenderDirect
    AdminBrowser -->|Direct Access| RenderDirect
    BuyerBrowser -.->|Proxy Access| NetlifyEdge
    SellerBrowser -.->|Proxy Access| NetlifyEdge
    AdminBrowser -.->|Proxy Access| NetlifyEdge

    UptimeRobot -->|Keep-Alive Heartbeat| RenderDirect
    NetlifyEdge -->|Proxy Rewrite /api/*| RenderDirect
    RenderDirect --> SpringApp

    SpringApp --> StaticFilter
    SpringApp --> SecurityChain
    SecurityChain --> RestControllers
    RestControllers --> DomainServices
    DomainServices --> HikariPool
    HikariPool --> PostgresDB

    PostgresDB --- FlywaySchema
    PostgresDB --- SessionStore
    PostgresDB --- DataIndexes
```

---

## 2. Dual-Delivery Architecture & Cold-Start Elimination

To guarantee 100% uptime, zero loading latency, and complete resilience against cloud provider limits, SocioMart implements a **Dual-Delivery Hosting Model**:

1. **Direct Spring Boot Origin (`https://sociomart-demo.onrender.com`)**:
   - The Spring Boot Docker container packages both the REST API and the entire static frontend (`src/main/resources/static`).
   - Requests served directly through this origin eliminate reverse-proxy hops, completely bypassing proxy timeouts.
2. **24/7 Active Heartbeat (UptimeRobot)**:
   - An external synthetic monitoring probe pings `/actuator/health` or `/` every 5 minutes.
   - Prevents the Render free-tier container from entering sleep mode, eliminating cold-start latency.
3. **Netlify Edge CDN (`https://sociomart-frontend.netlify.app`)**:
   - Serves as a globally distributed static edge cache.
   - Transparently rewrites `/api/*` to the active Render service origin with enhanced client-side retry logic.

---

## 3. Multi-Society Data Isolation Architecture

SocioMart enforces strict multi-tenant community isolation at both the query level and the authorization filter level:

```mermaid
sequenceDiagram
    autonumber
    actor Buyer as Buyer / Neighbor
    participant Controller as Marketplace API
    participant Service as KitchenVisibility & ProductService
    participant DB as PostgreSQL 16 (V28 Indexed)

    Buyer->>Controller: GET /api/v1/marketplace/offerings?societyId=42
    Controller->>Service: resolveActiveOfferings(societyId: 42)
    Note over Service: Server-Side Society Boundary Check
    Service->>DB: SELECT p.* FROM products p JOIN kitchen_served_societies kss<br/>ON p.kitchen_id = kss.kitchen_id<br/>WHERE kss.society_id = 42 AND p.available_today = true<br/>ORDER BY p.created_at DESC
    Note over DB: Index seek via idx_kitchen_served_societies_society<br/>and idx_products_available_today_created (sub-15ms)
    DB-->>Service: Filtered Offerings (Zero Leaks)
    Service-->>Controller: Verified Community DTOs
    Controller-->>Buyer: 200 OK (Clean Community Catalog)
```

### Server-Side IDOR & Cross-Community Defenses
- **Buyer Isolation**: Offerings are constrained by the buyer's selected society; kitchens that do not actively register coverage for `societyId` are pruned from query execution.
- **Seller Isolation**: All offering mutations verify `product.kitchen.owner.id == authenticatedUser.id`.
- **Admin Isolation**: Master location hierarchies (Area → Society) require `ROLE_ADMIN` with database constraint verification.

---

## 4. Concurrency & Inventory Integrity

To ensure zero overselling during peak pre-order launches and high-concurrency ordering bursts, SocioMart uses atomic SQL decrements combined with pessimistic row locking:

```mermaid
sequenceDiagram
    autonumber
    actor BuyerA as Buyer A
    actor BuyerB as Buyer B
    participant OrderSvc as Order Processing Engine
    participant DB as PostgreSQL (Products Table)

    BuyerA->>OrderSvc: Checkout Item #101 (Qty: 2)
    BuyerB->>OrderSvc: Checkout Item #101 (Qty: 1)
    
    par Concurrency Execution
        OrderSvc->>DB: UPDATE products SET stock_quantity = stock_quantity - 2<br/>WHERE id = 101 AND stock_quantity >= 2 AND is_active = true
        OrderSvc->>DB: UPDATE products SET stock_quantity = stock_quantity - 1<br/>WHERE id = 101 AND stock_quantity >= 1 AND is_active = true
    end

    Note over DB: Atomic DB Row Lock<br/>Single Update Wins
    DB-->>OrderSvc: Rows affected: 1 (Buyer A Success)
    DB-->>OrderSvc: Stock exhausted / Rows affected: 0 (Buyer B)
    
    OrderSvc-->>BuyerA: 201 Created (Order Placed)
    OrderSvc-->>BuyerB: 409 Conflict ("Item sold out or insufficient stock")
```

---

## 5. Database Performance & Indexing Strategy (Flyway V28)

Database schema evolution is managed via version-controlled Flyway migrations with strict `ddl-auto=validate` enforcement.

### Critical Indexes in Migration `V28__enterprise_scalability_indexes.sql`:
1. **`idx_kitchen_served_societies_society`** on `kitchen_served_societies (society_id)`:
   - Converts multi-society marketplace filtering from $O(N)$ full-table scans to $O(\log N)$ B-tree seeks.
2. **`idx_products_available_today_created`** on `products (available_today, created_at DESC)`:
   - Optimizes high-throughput catalog listing queries by ignoring archived or draft items.
3. **`idx_users_role_approval`** on `users (role, seller_approval_status)`:
   - Accelerates admin seller onboarding workflows and moderation queues.
4. **`idx_orders_status_composite`** on `orders (order_status, payment_status, created_at DESC)`:
   - Accelerates seller earnings calculations and active order lifecycle filtering.

---

## 6. Security Architecture & Threat Defense

| Threat / Vulnerability | SocioMart Mitigation Strategy | Implementation Point |
| :--- | :--- | :--- |
| **Cross-Site Request Forgery (CSRF)** | Double-submit `XSRF-TOKEN` cookie validated against `X-XSRF-TOKEN` header on all mutable methods (`POST`, `PUT`, `DELETE`). | `SecurityConfig.java` |
| **Insecure Direct Object Reference (IDOR)** | Server-side user identity verification on all entity mutations (`sellerId`, `orderId`). | `SellerService.java`, `OrderService.java` |
| **Formula Injection (CSV Injection)** | Prepending single quote (`'`) to any exported field starting with `=`, `+`, `-`, or `@`. | `AdminService.java` |
| **Session Hijacking** | Persistent `SPRING_SESSION` stored in PostgreSQL with `HttpOnly`, `SameSite=Lax`, and `Secure` cookie flags. | `application-prod.properties` |
| **Data-at-Rest Exposure** | AES-256-GCM AEAD encryption on sensitive user data fields. | `DatabaseAEADEncryptor.java` |
| **XSS & Injection Attacks** | Strict Content Security Policy (CSP), HTML entity encoding on render, zero `eval()` usage. | Client UI Framework |

---

## 7. Architecture Decision Records (ADRs)

For in-depth justifications of key architectural decisions, refer to:
* **[ADR-001: Modular Monolith vs Microservices](./adr/ADR-001-modular-monolith-vs-microservices.md)**
* **[ADR-002: Dual-Backend Evaluation & Netlify Independence](./adr/ADR-002-dual-backend-evaluation-and-netlify-independence.md)**
* **[ADR-003: Multi-Society Data Isolation & Authorization](./adr/ADR-003-multi-society-data-isolation-and-authorization.md)**
* **[ADR-004: Concurrency & Transaction Pool Routing](./adr/ADR-004-concurrency-and-transaction-pool-routing.md)**
* **[ADR-005: Database Indexing & Capacity Scaling](./adr/ADR-005-database-indexing-and-capacity-scaling.md)**

---

## 8. Operational & SRE Documentation

* **[Capacity Planning Guide (1k–100k Users)](./SCALABILITY.md)**
* **[Disaster Recovery & Backup Procedures](./DISASTER_RECOVERY.md)**
* **[Incident Response & Operational Runbooks](./RUNBOOKS.md)**
* **[REST API Reference (182 Endpoints)](./API_REFERENCE.md)**
