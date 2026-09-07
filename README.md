# SocioMart — Food & Kitchen Marketplace

> A community-driven homemade-food marketplace connecting local home kitchens with buyers in their neighbourhood.

---

## Live Demo

| Role | URL |
|------|-----|
| 🛒 **Buyer** | https://sociomart-demo.onrender.com/ |
| 👨‍🍳 **Seller** | https://sociomart-demo.onrender.com/seller.html |
| 🛠️ **Admin** | https://sociomart-demo.onrender.com/admin.html |

> Free-tier hosting: the service sleeps after ~15 min of inactivity. First request may take ~60–90 s to wake. Subsequent requests are fast.

---

## What is SocioMart?

SocioMart is a hyper-local community marketplace where home cooks sell homemade food to buyers in their housing society or neighbourhood. The current release ships the **Food & Kitchen** marketplace only.

### What is included

- Buyers discover kitchens, view offerings, place orders, and track them
- Sellers manage their kitchen profile, offerings, orders, history, and earnings
- Admins review and approve sellers

### What is NOT included

- Services marketplace
- Real Estate / Rentals
- Products / Buy & Sell marketplace
- OTP authentication
- Three.js / 3D features
- Social feed, chat, live delivery tracking, real payments, wallet, subscriptions

---

## Roles

### Buyer
Discover home kitchens, browse menus, place orders, pay (demo/UPI), and track order status.

### Seller
Operate a home kitchen: manage profile, create offerings, process orders, view earnings, and republish from history.

### Admin
Review seller applications, approve or reject sellers, and monitor platform activity.

---

## Demo Credentials

| Role | Access |
|------|--------|
| **Buyer** | Open the app — no login required to browse. Place orders as a guest. |
| **Seller** | The seller dashboard pre-loads the demo kitchen. |
| **Admin** | Open `admin.html` — admin functions are accessible without authentication in demo mode. |

> No real passwords, API keys, or secrets are embedded in the application.

---

## Features

### Buyer
- Home with kitchen discovery
- Kitchen detail pages with gallery, menu, and availability
- Offering/menu cards with price, unit, availability, and order CTA
- Favourites
- Order flow with quantity selection and fulfilment preferences
- Demo payment (UPI-style claim-based flow)
- Order history with status tracking
- Responsive UI with light/dark theme

### Seller
- Dashboard with views, followers, orders, and offering summary
- Manage Kitchen (profile, gallery, contact info)
- Add Offering via three paths:
  - Create from Favourite
  - Create New Offering
  - Quick Create (paste WhatsApp message — does NOT auto-publish)
- History with republish (creates a new offering)
- Orders by date with customer drilldown
- Earnings breakdown (confirmed, pending, monthly)

### Admin
- Seller approval workflow
- Platform overview

---

## UI & Theme

### Light Theme
- Warm cream / soft-white background
- Deep navy/charcoal text
- Orange/rust primary
- Green secondary
- Subtle borders and soft shadows

### Dark Theme
- Deep charcoal / warm navy background
- Dark elevated surfaces
- Warm white text with muted grey secondary
- Restrained orange/rust and green accents
- No pure-black surfaces or neon effects

### Theme Switching
- Light/dark toggle persisted to `localStorage`
- Defaults to system `prefers-color-scheme`
- No flash on page load (theme applied before first paint)

### Design Tokens
Key CSS variables: `--background`, `--surface`, `--surface-elevated`, `--text-primary`, `--text-secondary`, `--border`, `--primary`, `--secondary`, `--success`, `--warning`, `--error`, `--overlay`.

### Typography
- Headings: Montserrat
- Body: Segoe UI / system fonts
- Hierarchy: 12–14 px labels, 14–16 px body, 18–20 px card titles, 24 px section titles, 28–32 px headings

### Spacing
4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 px scale

### Radius
8 / 12 / 16 / 20 px + pill (9999 px)

### Responsive Breakpoints
Mobile-first. Tested at 360, 390, 430, 768, 1024, and 1440 px. Desktop uses sensible max-width containers without stretching content edge-to-edge.

### Seller Navigation
**Home · Kitchen / Manage Kitchen · Orders · History · Earnings** (unchanged).

---

## Architecture

### Tech Layer
| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot, Java, Maven |
| Frontend | Vanilla HTML/CSS/JS SPA |
| Database | In-memory H2 (resets on restart) |
| API Docs | Swagger/OpenAPI (`/swagger-ui.html`) |
| Deployment | Docker on Render |

### Project Structure
```
my-first-spring-api/
├── .github/                         GitHub configuration
├── data/                            Runtime H2 data (git-ignored)
├── deploy/                          Static deploy copy
├── docs/                            HTML/PDF project documentation
├── docs-tools/                      Local QA tooling (git-ignored)
├── my-first-spring-api/             Maven project root
│   ├── Dockerfile                   Multi-stage Docker build (JDK 21)
│   ├── pom.xml                      Spring Boot 4.1.1, Java 21
│   ├── mvnw / mvnw.cmd              Maven wrapper
│   └── src/main/
│       ├── java/com/example/my_first_spring_api/
│       │   ├── config/              Web/App configuration
│       │   ├── controller/          REST controllers
│       │   ├── dto/                 Request/Response DTOs
│       │   ├── exception/           Domain exceptions + global handler
│       │   ├── model/               JPA entities
│       │   ├── repository/          Spring Data repositories
│       │   ├── security/            Security configuration
│       │   ├── service/             Business services
│       │   ├── DataInitializer.java Demo data seeding
│       │   └── MyFirstSpringApiApplication.java
│       └── resources/
│           ├── application.properties
│           └── static/              The SPA (index.html, seller.html, admin.html, css/, js/)
├── render.yaml                      Render Blueprint configuration
├── start.sh / start.cmd / start.ps1 One-command local runners
├── Makefile
├── .env.example
└── README.md
```
└── README.md
```

---

## Data Model

Key entities and their relationships:

| Entity | Responsibility |
|--------|---------------|
| `User` | Base user (buyer or seller) |
| `Kitchen` | Seller's storefront: name, society, building, gallery, description |
| `Product` | Offering/item sold by a kitchen: name, price, unit, availability, quantity |
| `Order` | Buyer's order: status, payment status, delivery info |
| `OrderItem` | Line items linking order ↔ product with quantity |
| `Favourite` | Buyer's favourited kitchens |
| `SellerTemplate` | Saved favourite offerings for quick republish |
| `Enquiry` | Buyer-to-seller messages |

### Implemented Business Rules
- Seller ownership: sellers can only manage their own kitchen and offerings
- Inventory: limited-quantity offerings track booked vs available
- Sold-out offerings show clearly and disable ordering
- Order quantity validated against available stock
- Payment state propagates: pending → paid → confirmed
- Earnings calculated from confirmed orders
- Favourites have a maximum count
- Republishing from history creates a NEW offering (not an update)
- Quick Create does NOT auto-publish — it opens a draft editor
- Order/payment consistency enforced at the service layer

---

## API Overview

The REST API is auto-documented at `/swagger-ui.html`.

### Controller Groups
| Controller | Path |
|-----------|------|
| `AuthController` | `/api/auth` |
| `DiscoveryController` | `/api/kitchens`, `/api/search` |
| `MarketplaceController` | `/api/marketplace/*` |
| `BuyerOrderController` | `/api/orders` |
| `BuyerProfileController` | `/api/profile` |
| `FavouriteController` | `/api/favourites` |
| `EnquiryController` | `/api/enquiries` |
| `SellerController` | `/api/seller/*` |
| `SellerAppController` | `/api/seller/kitchen`, `/api/seller/products`, `/api/seller/orders`, `/api/seller/earnings` |
| `AdminController` | `/api/admin/*` |
| `SuperAdminController` | `/api/superadmin/*` |

### Frontend Routes
| File | Route |
|------|-------|
| `index.html` | Buyer SPA (hash-based routing: `#/home`, `#/kitchens`, `#/kitchen/:id`, etc.) |
| `seller.html` | Seller dashboard (hash-based routing) |
| `admin.html` | Admin panel |
| `admin.html` | Admin panel |

---

## Local Setup

### Prerequisites
- **JDK 21** (Temurin recommended: https://adoptium.net, tick *Set JAVA_HOME*)
- Verify: `java -version` → `21.x`

### Clone & Run
```bash
git clone https://github.com/utkarshnikhare/my-first-spring-api.git
cd my-first-spring-api
```

Then use the one-command runner for your OS:

| OS | Command |
|----|---------|
| macOS / Linux / WSL / Git Bash | `./start.sh` or `make` |
| Windows Command Prompt | `start.cmd` |
| Windows PowerShell | `.\start.ps1` |

Each runner:
1. Verifies JDK 21+
2. Applies optional `.env` overrides (copy `.env.example` → `.env` to change port)
3. Boots via Maven wrapper — downloads dependencies on first run (2–5 min)
4. Seeds demo data on startup

Wait for `Started MyFirstSpringApiApplication`, then open [http://localhost:8081](http://localhost:8081).

### Alternative: Maven directly
```bash
cd my-first-spring-api
../mvnw spring-boot:run
```

### Build & Test
```bash
cd my-first-spring-api
../mvnw clean compile
../mvnw test
```

---

## URLs When Running Locally

| URL | Purpose |
|-----|---------|
| `http://localhost:8081` | Buyer application |
| `http://localhost:8081/seller.html` | Seller dashboard |
| `http://localhost:8081/admin.html` | Admin panel |
| `http://localhost:8081/swagger-ui.html` | API documentation |
| `http://localhost:8081/api/kitchens` | Health check / kitchen list |

---

## Deployment

- **Platform**: Render (free-tier Docker hosting)
- **Configuration**: `render.yaml` (Blueprint)
- **Build**: Multi-stage Dockerfile — Maven compile on JDK 21, runtime on slim JRE 21
- **Auto-deploy**: Enabled — push to `main` triggers a new deployment
- **Environment variables**: `SPRING_PROFILES_ACTIVE=demo`, `PORT` (injected by Render)
- **Health check**: `/api/kitchens`

---

## Verification Status

| Check | Status |
|-------|--------|
| Build (`clean compile`) | PASS |
| Automated tests | PASS |
| Buyer flow (discovery → order → payment) | PASS (verified on public deployment) |
| Seller flow (dashboard → kitchen → orders → earnings) | PASS (verified on public deployment) |
| Admin flow (login → approval) | PASS (verified on public deployment) |
| Light theme — all screens | PASS |
| Dark theme — all screens | PASS |
| Mobile (360–430 px) | PASS |
| Tablet (768–1024 px) | PASS |
| Desktop (1280–1440 px) | PASS |
| Public Buyer URL loads | PASS |
| Public Seller URL loads | PASS |
| Public Admin URL loads | PASS |

---

## Known Limitations

- Free-tier hosting: cold-start latency of ~60–90 s after inactivity
- In-memory database: data resets when the server restarts
- Demo payment only: no real payment gateway integration
- Single-kitchen cart: one kitchen per order
- Cross-browser testing not performed (Chromium-only verification)
- Real-time features (chat, live tracking) not implemented

---

## Verification Guardrails

### SHARED DATABASE PROOF — MANDATORY

Do not consider Buyer/Seller/Admin integration verified merely because they point to the same API.

Perform a real end-to-end data mutation:

1. Create/place a new Buyer order.
2. Record its exact Order ID.
3. Verify the SAME Order ID and details appear in:
   - Buyer My Orders
   - Seller Orders
   - Admin Orders
4. Verify inventory/booked quantity changes consistently.
5. Verify Admin order count/value changes.
6. Verify Seller earnings/order figures update according to existing business rules.

Then create a second order using:
Payment Status = Will Pay Later

Verify:
- SAME order appears in Buyer/Seller/Admin
- Admin Paid value does NOT include it
- Admin Will Pay Later value DOES include it

This is the acceptance proof that Buyer, Seller and Admin are operating on the same live data source.

### DATA SOURCE RULE

Demo seed data is legitimate database data and may be included in Admin totals.

However:
- no hardcoded frontend numbers
- no manually duplicated dashboard totals
- no fake rows created only in JavaScript
- every displayed aggregate must be derivable from actual backend records.

Document which records are seeded demo records.

### RESTART / PERSISTENCE CHECK

Because the current demo may use in-memory H2:

Verify:
- Buyer, Seller and Admin use the same database during one running application instance.
- Restarting the application may reset demo/custom data if H2 is in-memory.
- Seed data must be recreated correctly on startup.
- No duplicate seed records should accumulate during normal startup.
- Admin calculations after restart must still be correct.

Do NOT silently claim permanent database persistence if the current database is in-memory.

### ADMIN SECURITY BOUNDARY

Audit Admin APIs and UI.

Do not expose privileged Admin mutation APIs without the existing Admin authentication/authorization mechanism.

Do not put Admin credentials, secrets, passwords or sensitive configuration into frontend JavaScript.

Admin UI may be directly reachable by URL, but privileged data/actions must follow the application's existing authentication/authorization design.

Do not invent a complex authentication system unless genuinely required by the existing architecture.

### ADMIN RECONCILIATION TEST

For every Admin aggregate, reconcile dashboard values against detail records.

Examples:

Dashboard Total Orders == count of Admin Orders records

Dashboard Live Kitchens == count of kitchens having >=1 currently live offering

Dashboard Live Offerings == count of currently live offerings

Dashboard Paid Value == sum of qualifying Paid orders

Dashboard Will Pay Later Value == sum of qualifying Will Pay Later orders

Dashboard Seller counts == seller detail/status counts

Dashboard Buyer count == buyer records

No aggregate may disagree with its underlying list.

### PUBLIC DEPLOYMENT ACCEPTANCE

After implementation and tests pass:

Verify the actual deployed URLs, not only localhost:

Buyer:
https://sociomart-demo.onrender.com/

Seller:
https://sociomart-demo.onrender.com/seller.html

Admin:
https://sociomart-demo.onrender.com/admin.html

For Admin specifically verify:
- page loads after Render cold start
- Admin login/access works
- dashboard calculations load
- no failed API requests
- orders load
- sellers load
- buyers load
- kitchens load
- offerings load
- enquiries load
- refresh works
- no blank dashboard
- no hardcoded fallback numbers

Confirm the deployed application corresponds to the final Git commit.

If Render is sleeping, wait for wake-up and verify again.
Do not report HTTP 200 alone as successful functional verification.

---

## Final Freeze

Once the above guardrails are verified:

- Do NOT add new features
- Do NOT add new marketplace categories
- Do NOT add payment gateway
- Do NOT add OTP
- Do NOT add social feed/chat
- Do NOT add wallet/subscriptions
- Do NOT add unnecessary analytics
- Do NOT redesign UI/UX
- Do NOT change working business logic

The next phase after this freeze is deployment/public-demo verification, not more feature work.

---

## Demo Screenshots

All screenshots below are from the live public deployment. Click any image to view the full interactive demo.

### Buyer

| Screen | Light | Dark |
|--------|-------|------|
| 🏠 Home / Kitchen Discovery | [View Live](https://sociomart-demo.onrender.com/#home) | [View Live](https://sociomart-demo.onrender.com/#home) · toggle 🌓 |
| 🍽️ Kitchen Detail | [View Live](https://sociomart-demo.onrender.com/#kitchen/1) | same · toggle 🌓 |
| 🛒 Order Confirm | [View Live](https://sociomart-demo.onrender.com/#home) · place order | same · toggle 🌓 |

### Seller

| Screen | Light | Dark |
|--------|-------|------|
| 📊 Dashboard | [View Live](https://sociomart-demo.onrender.com/seller.html#home) | same · toggle 🌓 |
| 📋 Orders | [View Live](https://sociomart-demo.onrender.com/seller.html#orders) | same · toggle 🌓 |
| 💰 Earnings | [View Live](https://sociomart-demo.onrender.com/seller.html#earnings) | same · toggle 🌓 |
| 🏪 Manage Kitchen | [View Live](https://sociomart-demo.onrender.com/seller.html#kitchen) | same · toggle 🌓 |
| ➕ Add Offering | [View Live](https://sociomart-demo.onrender.com/seller.html#add) | same · toggle 🌓 |
| 🔄 History | [View Live](https://sociomart-demo.onrender.com/seller.html#history) | same · toggle 🌓 |

### Admin

| Screen | Light | Dark |
|--------|-------|------|
| 🛡️ Dashboard | [View Live](https://sociomart-demo.onrender.com/admin.html#home) | same · toggle 🌓 |
| ⏳ Pending Approvals | [View Live](https://sociomart-demo.onrender.com/admin.html#pending) | same · toggle 🌓 |

> **Screenshot archives** (local QA captures at 360/768/1440 px in light + dark) are stored in `docs-tools/uiqa-shots/` and are not committed to git. They are regenerated on each QA run and serve as the verification baseline for the status table above.
**Home · Kitchen / Manage Kitchen · Orders · History · Earnings** (unchanged).