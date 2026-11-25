# SocioMart

**A hyper-local community marketplace connecting apartment residents with verified home-kitchen chefs and homemade creators. The platform includes Buyer, Seller, and Admin web applications, progressive loading, recurring offerings, seller-recorded delivery, and an operational management console.**

---

## 🚀 Quick Links & Production Deployments

| Portal / Resource | URL / Reference | Hosting Characteristics |
| :--- | :--- | :--- |
| ⚡ **Direct Web App (Recommended)** | [https://sociomart-demo.onrender.com](https://sociomart-demo.onrender.com) | **Zero-Timeout, Direct Spring Boot Origin (24/7 Warm via UptimeRobot)** |
| 🧑‍🍳 **Direct Seller Studio** | [https://sociomart-demo.onrender.com/seller.html](https://sociomart-demo.onrender.com/seller.html) | Direct Seller Management & Delivery Coverage Portal |
| 🛡️ **Direct Admin Console** | [https://sociomart-demo.onrender.com/admin.html](https://sociomart-demo.onrender.com/admin.html) | Master Location Management & Seller Verification |
| 🌐 **Netlify Edge CDN (PWA)** | [https://sociomart-frontend.netlify.app/](https://sociomart-frontend.netlify.app/) | Global Edge CDN Delivery with Reverse Proxy `/api/*` |
| 🟢 **Live 24/7 Availability** | Active UptimeRobot Monitor (5-min Heartbeat) | **100% Uptime, Zero Cold Starts** |
| 💻 **GitHub Repository** | [utkarshnikhare/my-first-spring-api](https://github.com/utkarshnikhare/my-first-spring-api) | Main branch with verified CI and Playwright E2E suites |

---

## 📚 Authoritative Documentation Suite

For complete technical and operational details, refer to the documentation suite in [`docs/`](./docs/):

- 🏛️ [**System & Application Architecture**](./docs/ARCHITECTURE.md): Complete architecture specification, topology diagrams, and sequence flows.
- 📋 [**Architecture Decision Records (ADRs)**](./docs/adr/): Formal decisions on modular monolith, dual-backend evaluation, multi-society isolation, and concurrency.
- 📈 [**Scalability & Capacity Planning**](./docs/SCALABILITY.md): 1,000+ to 100,000 active user capacity forecast, RPS, connection pool sizing, and cost models.
- 🛡️ [**Disaster Recovery & Business Continuity**](./docs/DISASTER_RECOVERY.md): RPO/RTO targets, automated backup pipelines, and verified restoration procedures.
- 📋 [**Operational Runbooks**](./docs/RUNBOOKS.md): Incident handling for 504 timeouts, connection pool exhaustion, and emergency rollbacks.
- 📁 [**Project Structure**](./docs/PROJECT_STRUCTURE.md): Comprehensive repository directory layout and component classification.
- 🔌 [**REST API Reference**](./docs/API_REFERENCE.md): Full specification for all 182 supported endpoints.
- 🗄️ [**Database Architecture**](./docs/DATABASE.md): Schema models, Flyway migrations (V1–V28), and indexing strategy.
- 💻 [**Developer Onboarding & Setup**](./docs/DEVELOPMENT.md): Local environment setup, H2 console, and debugging.
- 🚀 [**Deployment Runbook**](./docs/DEPLOYMENT.md): Netlify frontend, Render backend, and rollback procedures.
- 🔒 [**Security Architecture**](./docs/SECURITY.md): RBAC, session isolation, formula injection defense, and CSP.
- 🧪 [**Testing Strategy**](./docs/TESTING.md): Maven JUnit 5, Testcontainers, and Playwright E2E suites.
- 📜 [**Changelog**](./CHANGELOG.md): Platform release history, version tags, and notable changes.

---

## 📌 What is SocioMart?

SocioMart is a **society-level food and homemade-goods marketplace**.

In residential apartment communities, residents often cook and sell meals or homemade goods. SocioMart provides each seller with a structured storefront and allows neighbours to discover what is available and order reliably.

It is deliberately **location-first**: buyers see only the storefronts that actively serve their residential society. An Admin provisions the location hierarchy (**Area → Society**), sellers select their society delivery coverage, and buyer discovery reflects that intersection.

### Three Integrated Portals

| Portal | Source | Purpose |
| :--- | :--- | :--- |
| **Buyer App** | `index.html` | Food & kitchen discovery, real-time search, cart, checkout, UPI payment copy, order tracking, and favourites. |
| **Seller Studio** | `seller.html` | Storefront setup, offering creation, stock allocation, order fulfillment, delivery tracking, and earnings. |
| **Admin Console** | `admin.html` | Location master data, seller onboarding approvals, user moderation, order lifecycle audit, and CSV exports. |

---

## ✨ Key Technical Highlights

- **Dual-Delivery Hosting**: Fully containerized Spring Boot backend capable of serving the static web client directly with zero proxy hops, paired with a global Netlify edge CDN.
- **Zero Cold-Start Latency**: Synthetic 5-minute health monitoring keeps the cloud container warm 24/7.
- **Location-First Discovery**: High-performance $O(\log N)$ indexed multi-society queries via Flyway migration V28.
- **Progressive Loading**: Content-shaped shimmer skeletons ensure instant (<500ms) page rendering with smooth asynchronous data hydration.
- **Zero-Overselling Concurrency**: Atomic SQL decrements prevent overselling during high-concurrency order rushes.
- **Dual Themes**: Warm Editorial Light Theme (`#F5EFE6`) and Espresso Dark Theme (`#1C1613`) with full contrast compliance and persistence.
- **Order Lifecycle**: Real-time inventory deduction, UPI payment recording, and per-order delivery verification.
- **Admin Operations**: Filter-aware CSV exports with spreadsheet formula injection protection.
- **PWA Ready**: Offline fallback, web manifest installation, and cache-first static asset delivery.

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
