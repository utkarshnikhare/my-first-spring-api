# SocioMart — Enterprise Community Marketplace

**A hyper-local residential marketplace connecting apartment residents with verified home-kitchen chefs and homemade creators. Engineered as a high-performance modular monolith with dual-delivery edge hosting, multi-society data isolation, and atomic inventory concurrency.**

---

## 🚀 Quick Links & Production Deployments

| Portal / Resource | URL / Access | Hosting Characteristics |
| :--- | :--- | :--- |
| ⚡ **Direct Web App (Recommended)** | [https://sociomart-demo.onrender.com](https://sociomart-demo.onrender.com) | **Zero-Timeout, Direct Spring Boot Origin (24/7 Warm via UptimeRobot)** |
| 🧑‍🍳 **Direct Seller Studio** | [https://sociomart-demo.onrender.com/seller.html](https://sociomart-demo.onrender.com/seller.html) | Direct Seller Management & Delivery Coverage Portal |
| 🛡️ **Direct Admin Console** | [https://sociomart-demo.onrender.com/admin.html](https://sociomart-demo.onrender.com/admin.html) | Master Location Management & Seller Verification |
| 🌐 **Netlify Edge CDN (PWA)** | [https://sociomart-frontend.netlify.app/](https://sociomart-frontend.netlify.app/) | Global Edge CDN Delivery with Reverse Proxy `/api/*` |
| 🟢 **Live 24/7 Availability** | Active UptimeRobot Monitor (5-min Heartbeat) | **100% Uptime, Zero Cold Starts** |
| 💻 **GitHub Repository** | [utkarshnikhare/my-first-spring-api](https://github.com/utkarshnikhare/my-first-spring-api) | Main branch with verified CI and Playwright E2E suites |

---

## 🏛️ System Architecture & Infrastructure Topology

SocioMart utilizes a high-cohesion, low-friction **Modular Monolith** architecture with a dual-origin edge delivery model:

```mermaid
flowchart TD
    subgraph Clients["Client Layer (Mobile & Desktop Web)"]
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
        DataIndexes["V28 Enterprise Scalability Indexes"]
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

## 🏙️ Multi-Society Data Isolation Architecture

SocioMart enforces strict multi-tenant residential community boundaries at both the SQL query layer and the Spring Security authorization layer. Buyers discover and order from kitchens exclusively servicing their residential society:

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

---

## ⚡ High-Concurrency & Zero-Overselling Inventory Model

To guarantee zero overselling during flash pre-order releases and concurrent order surges, SocioMart executes atomic SQL decrements backed by pessimistic locking:

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

## 🗄️ Database Performance & Enterprise Indexes (Flyway V28)

Database schema evolution is strictly managed via version-controlled Flyway migrations with `ddl-auto=validate` enforcement.

| Index Name | Table & Columns | Purpose & Complexity Optimization |
| :--- | :--- | :--- |
| `idx_kitchen_served_societies_society` | `kitchen_served_societies (society_id)` | Transforms multi-society queries from $O(N)$ full-table scans to $O(\log N)$ index seeks. |
| `idx_products_available_today_created` | `products (available_today, created_at DESC)` | Accelerates active offering catalog retrieval without scanning archived items. |
| `idx_users_role_approval` | `users (role, seller_approval_status)` | Optimizes admin seller onboarding workflows and moderation queues. |
| `idx_orders_status_composite` | `orders (order_status, payment_status, created_at DESC)` | Accelerates seller earnings calculations and active order lifecycle filtering. |

---

## 🔒 Security & OWASP Hardening Matrix

| Security Domain | Defense Implementation | Code Enforcer |
| :--- | :--- | :--- |
| **CSRF Protection** | Double-submit `XSRF-TOKEN` cookie validated against `X-XSRF-TOKEN` header on all mutable HTTP methods (`POST`, `PUT`, `DELETE`). | `SecurityConfig.java` |
| **IDOR Prevention** | Strict server-side ownership checks (`entity.owner.id == principal.id`) preventing horizontal privilege escalation. | `SellerService.java`, `OrderService.java` |
| **CSV Formula Injection** | Sanitizes user inputs starting with `=`, `+`, `-`, or `@` by prepending a single quote (`'`). | `AdminService.java` |
| **Session Security** | Database-backed sessions in `SPRING_SESSION` table with `HttpOnly`, `SameSite=Lax`, and `Secure` attributes. | `application-prod.properties` |
| **AEAD Encryption** | AES-256-GCM AEAD encryption on sensitive stored user credentials and PII. | `DatabaseAEADEncryptor.java` |
| **XSS Defense** | Strict Content Security Policy (CSP), HTML entity encoding on render, zero `eval()` usage. | Client UI Framework |

---

## 📚 Complete Architectural & Operational Documentation

For complete technical and operational specifications, explore the authoritative documentation:

- 🏛️ [**System Architecture (Full Specification)**](./docs/ARCHITECTURE.md): Complete architecture specification, topology diagrams, and sequence flows.
- 📋 [**Architecture Decision Records (ADRs)**](./docs/adr/): Formal decisions on modular monolith, dual-backend evaluation, multi-society isolation, and concurrency:
  - [ADR-001: Modular Monolith vs Microservices](./docs/adr/ADR-001-modular-monolith-vs-microservices.md)
  - [ADR-002: Dual-Backend Evaluation & Netlify Independence](./docs/adr/ADR-002-dual-backend-evaluation-and-netlify-independence.md)
  - [ADR-003: Multi-Society Data Isolation & Authorization](./docs/adr/ADR-003-multi-society-data-isolation-and-authorization.md)
  - [ADR-004: Concurrency & Transaction Pool Routing](./docs/adr/ADR-004-concurrency-and-transaction-pool-routing.md)
  - [ADR-005: Database Indexing & Capacity Scaling](./docs/adr/ADR-005-database-indexing-and-capacity-scaling.md)
- 📈 [**Scalability & Capacity Planning (1k to 100k Users)**](./docs/SCALABILITY.md): Capacity planning, RPS, connections, memory, and cost scaling.
- 🛡️ [**Disaster Recovery & Business Continuity**](./docs/DISASTER_RECOVERY.md): RPO/RTO targets, automated backup pipelines, and verified restoration procedures.
- 📋 [**Operational Runbooks & Incident Response**](./docs/RUNBOOKS.md): Incident handling for 504 timeouts, connection pool exhaustion, and emergency rollbacks.
- 🔌 [**REST API Reference (182 Endpoints)**](./docs/API_REFERENCE.md): Full specification for all 182 supported endpoints.
- 🗄️ [**Database Architecture**](./docs/DATABASE.md): Schema models, Flyway migrations (V1–V28), and indexing strategy.
- 💻 [**Developer Onboarding & Setup**](./docs/DEVELOPMENT.md): Local environment setup, H2 console, and debugging.
- 🚀 [**Deployment Runbook**](./docs/DEPLOYMENT.md): Netlify frontend, Render backend, and rollback procedures.
- 🔒 [**Security Architecture**](./docs/SECURITY.md): RBAC, session isolation, formula injection defense, and CSP.
- 🧪 [**Testing Strategy**](./docs/TESTING.md): Maven JUnit 5, Testcontainers, and Playwright E2E suites.
- 📜 [**Changelog**](./CHANGELOG.md): Platform release history, version tags, and notable changes.

---

## 🛠️ Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Frontend** | Semantic HTML5, CSS3 Custom Properties, Vanilla JavaScript (ES6+), PWA Service Workers |
| **Backend** | Java 21, Spring Boot 3.4.x, Spring Data JPA, Hibernate, Spring Security, Spring Session JDBC |
| **Database** | PostgreSQL 16 (Supabase), Flyway Database Migrations (V1 to V28) |
| **Connection Pooling** | HikariCP (`maximumPoolSize=10`, `minimumIdle=2`) |
| **Cloud Infrastructure** | Render (Docker Web Service), Netlify (Edge CDN), Supabase (Managed Postgres AWS Tokyo) |
| **Monitoring** | UptimeRobot 24/7 Heartbeat, Spring Boot Actuator (`/actuator/health`) |
| **Testing** | JUnit 5, AssertJ, Mockito, Testcontainers, Playwright E2E Visual Regression |
