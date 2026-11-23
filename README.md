# SocioMart

**A society-level marketplace connecting residents with home-kitchen chefs and homemade creators. The platform includes Buyer, Seller, and Admin web applications, progressive loading, recurring offerings, seller-recorded delivery, and an operational management console.**

---

## 🔗 Quick Links

| Portal / Resource | URL / Reference |
| :--- | :--- |
| 🌐 **Buyer Application (Live)** | [https://sociomart-frontend.netlify.app/](https://sociomart-frontend.netlify.app/) |
| 🏪 **Seller Studio (Live)** | [https://sociomart-frontend.netlify.app/seller.html](https://sociomart-frontend.netlify.app/seller.html) |
| 🛡️ **Admin Console (Live)** | [https://sociomart-frontend.netlify.app/admin.html](https://sociomart-frontend.netlify.app/admin.html) |
| ⚡ **Backend API Service** | [https://sociomart-demo.onrender.com](https://sociomart-demo.onrender.com) |
| 💻 **GitHub Repository** | [https://github.com/utkarshnikhare/my-first-spring-api](https://github.com/utkarshnikhare/my-first-spring-api) |

---

## 📚 Authoritative Documentation

For complete technical and operational details, refer to the documentation suite in [`docs/`](./docs/):

- 🏛️ [**System Architecture**](./docs/ARCHITECTURE.md): Frontend, backend, database, session persistence, and API proxy design.
- 📁 [**Project Structure**](./docs/PROJECT_STRUCTURE.md): Comprehensive repository directory layout and component classification.
- 🔌 [**REST API Reference**](./docs/API_REFERENCE.md): Full specification for all 182 supported endpoints.
- 🗄️ [**Database Architecture**](./docs/DATABASE.md): Schema models, Flyway migrations (V1–V27), and indexing strategy.
- 💻 [**Developer Onboarding & Setup**](./docs/DEVELOPMENT.md): Local environment setup, H2 console, and debugging.
- 🚀 [**Deployment Runbook**](./docs/DEPLOYMENT.md): Netlify frontend, Render backend, and rollback procedures.
- 🔒 [**Security Architecture**](./docs/SECURITY.md): RBAC, session isolation, formula injection defense, and CSP.
- 🧪 [**Testing Strategy**](./docs/TESTING.md): Maven JUnit 5, Testcontainers, and Playwright E2E suites.
- 📝 [**Changelog**](./CHANGELOG.md): Platform release history and notable changes.

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

## ✨ Key Features

- **Location-First Discovery**: Buyers browse storefronts and dishes available within their specific community.
- **Progressive Loading**: Content-shaped shimmer skeletons ensure instant (<500ms) page rendering with smooth asynchronous data hydration.
- **Dual Themes**: Warm Editorial Light Theme (`#F5EFE6`) and Espresso Dark Theme (`#1C1613`) with full contrast compliance and persistence.
- **Dynamic Offerings**: Support for live orderable items, upcoming pre-orders, and instant today-only Quick Posts.
- **Order Lifecycle**: Real-time inventory deduction, UPI payment recording, and per-order delivery verification.
- **Admin Operations**: Filter-aware CSV exports with spreadsheet formula injection protection.
- **PWA Ready**: Offline fallback, web manifest installation, and cache-first static asset delivery.

---

## 🛠️ Technology Stack

- **Backend**: Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA, Spring Session JDBC.
- **Frontend**: Vanilla ES6+ JavaScript, Semantic HTML5, CSS3 Custom Properties (No heavy SPA framework or bundler overhead).
- **Database**: PostgreSQL 16 (Production: Supabase; Local/Test: In-Memory H2 & Dockerized Testcontainers).
- **Hosting**: Netlify (Frontend edge CDN) & Render (Backend Docker web service).
- **Testing**: Maven Surefire, JUnit 5, AssertJ, MockMvc, and Playwright.

---

## 🚀 Quick Start (Local Development)

### Prerequisites
- JDK 21 (Temurin recommended)
- Node.js v20+ LTS

### Run Backend
```bash
cd my-first-spring-api
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```
*Application starts on [http://localhost:8081](http://localhost:8081)*

### Run Tests
```bash
# Run Maven unit and integration tests (940 tests)
cd my-first-spring-api
.\mvnw.cmd test

# Run Playwright E2E tests
cd my-first-spring-api/e2e
npm test
```

---

## 📄 License & Attribution

SocioMart is developed and maintained as an open-source residential marketplace platform.
