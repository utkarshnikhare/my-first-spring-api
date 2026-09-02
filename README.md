# 🏪 SocioMart — Buyer Application v1.0

**Discover. Connect.**

SocioMart is a hyper-local community marketplace. v1.0 ships the **Food & Kitchens** module: buyers discover
home kitchens inside their housing society, compare dishes, order or pre-order, pay by UPI (claim-based), and
track orders & enquiries — everything in one mobile-first SPA.

> Dark-purple storefront branding (🏪) with the tagline **"Discover. Connect."** applied across the app.

---

## ✨ Buyer features (v1.0)

| Screen | What it does |
|---|---|
| **1 · Home** | Location pill (`📍 Pride World City`), notification bell, hero with logo + tagline. Active **Food & Kitchens** tile (`● AVAILABLE NOW`); **Services / Real Estate / Buy & Sell / Products** shown greyed out as `COMING SOON`. |
| **2 · Food & Kitchens** | The **single search bar** (`Search food items...`), category tiles (Breakfast, Lunch, Dinner, Snacks + backend Special), `[By Items]` (e.g. *"Poha — 4 kitchens"*) vs `[By Kitchens]` toggle, and a ❤️ Favourite Kitchens carousel. |
| **2A · Category detail** | In-place capsule category switcher (no page reload), "Explore lunch — 10 items" count, same By Items/By Kitchens toggle. |
| **3 · Kitchen discovery** | Header with live counts (`8 Live · 3 Tomorrow · 5 Pre-order · 12 All`), tabs **Live Now (default) / Tomorrow / Pre-order / All**, kitchen cards with status pills (`🟢 Taking orders`, `⚪ Currently closed`, `🔵 Pre-orders open`), item previews (`+ N more`), `↩ Previously ordered` badge. No price/cart on cards (comparison-only). |
| **4 · Public kitchen** | Banner + avatar + tags (`Homemade · Fresh · Daily`), WhatsApp/Instagram + **✉️ Enquire**, expandable bio, gallery strip, strictly split **🍽️ Available Today** / **🔮 Pre-order** sections, sold-out items (`🔴 Sold out`, disabled button), live **demand bar** (`18 / 50 booked`), per-offering **cutoff + ready-by** text, sticky bottom cart bar (`🛒 N items · ₹Total → View Order`). |
| **4A · Ordering sheet** | Type 1 Today (qty `− N +`), Type 2 Fixed future (e.g. *"For tomorrow, cutoff 12 PM"*), Type 3 Flexible future (date selector limited to the offering window + time-slot chips) → `Add to Order`. |
| **5 · Order summary** | One kitchen per cart, quantity steppers, color-coded fulfilment badges (🟢/🟡/🔵/🟣), seller note field, sticky `PLACE ORDER — ₹Total →`. |
| **6 · Payment** | Order receipt (`#SMxxxx`), seller QR + UPI ID with one-tap **Copy**, `[I HAVE PAID]` → Confirmed/Paid, `[I'LL PAY LATER]` → Pending. Both submit the order and route back to Home. |
| **7 · Comparison** | One card per kitchen for a dish: price, `ORDER BY`, `READY BY`, status, and `Visit [Kitchen]'s Kitchen →`. |
| **8 · Favourites / Orders & Enquiries / Profile** | Favourites (Kitchens/Food/Services/Listings — pre-populated demo kitchens), orders with status badges (`🟠 Pending`, `🟢 Confirmed`, `🔵 Ready`, `✓ Completed`, `🔴 Cancelled`), enquiries (`🟠 Waiting for response`, `🟢 Seller responded`), Profile with logged-out OTP prompt and logged-in address management. |

## 🧠 Global business rules (implemented)

- **1.1 Deferred OTP login** — unauthenticated buyers browse *everything* (Home, search, categories, kitchens,
  item details). Login is only prompted on identity-bound actions: **placing an order, sending an enquiry, or
  toggling a favourite**, and the action resumes automatically after verification.
- **1.2 Single-kitchen cart** — one kitchen at a time. Adding from another kitchen shows
  *"You are moving to another kitchen. Your existing order will be cleared."* — clearing only on explicit
  confirmation; cancelling keeps the original cart.
- **1.3 Persistent bottom nav** — fixed across all screens: **Home · Favourites · Orders & Enquiries · Profile**.
- **1.4 Offering-level availability vs cutoff** — cutoffs belong **only to offerings** (never a kitchen), stored
  per product, enforced at order time (today cutoff, day-before cutoff for pre-orders), and **revealed only in
  offering details / the ordering flow** — never on discovery cards.
- **1.5 V1 exclusions** — no star ratings/reviews, coupons, live GPS tracking, recommendation engines, or cart
  actions on discovery cards.

## 🛠 Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 4.1.1 (Web MVC · Security 7 · Validation · JPA) |
| Database | H2 (in-memory fresh demo DB on each boot; `ddl-auto=update`) |
| API docs | springdoc-openapi 3.1.0 |
| Frontend | Vanilla HTML5 / CSS3 / ES6 SPA (hash routing, zero build step) |
| Build | Maven wrapper (`mvnw.cmd`) |

## 🚀 Quick start

Prerequisites: **JDK 21** (Temurin, *Set JAVA_HOME* ticked), Git, VS Code.

```bash
git clone https://github.com/utkarshnikhare/my-first-spring-api.git
cd my-first-spring-api/my-first-spring-api

# Windows (PowerShell)
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The **implicit `default` profile** auto-seeds 8 demo kitchens, ~50 offerings (breakfast/lunch/dinner/snacks,
a sold-out item, fixed + flexible pre-orders). Boot the app and browse immediately.

## 🌐 Open the app

| URL | What it is |
|---|---|
| `http://localhost:8081` | Buyer app (boots on `#/home`) |
| `http://localhost:8081/swagger-ui.html` | Live REST API docs |
| `http://localhost:8081/h2-console` | DB console (`jdbc:h2:mem:sociomartdb`, user `sa`, blank password) |

**Demo login:** any 10-digit mobile; the OTP is returned inside the login dialog (demo mode).

## ✅ Testing procedure (Section 3 E2E suite)

1. **Authentication gate** — browse everything logged-out; tap a ❤️ heart or `PLACE ORDER` / `✉️ Enquire` →
   the OTP modal appears, and after verification the action completes automatically.
2. **Single-kitchen cart logic** — add from Kitchen A, add from Kitchen B → confirmation modal; cancel keeps A's
   cart, confirm clears A and switches to B.
3. **Ordering & slot validation** — today items blocked after their cutoff; fixed pre-orders blocked when the
   day-before cutoff passed; flexible pre-orders accept only dates inside `[availableDate … availableUntilDate]`
   and only the offering's listed time slots.
4. **UI/UX** — responsive mobile-first layout, zero console errors (JS is `node --check` clean), smooth
   state/navigation transitions.

Run API-level E2E with PowerShell:
```powershell
$b = 'http://localhost:8081'
Invoke-RestMethod "$b/api/discovery/counts"            # 8 Live · 3 Tomorrow · ...
Invoke-RestMethod "$b/api/discovery/items"             # grouped "By Items" grid
Invoke-RestMethod "$b/api/discovery/kitchens?tab=LIVE_NOW"
Invoke-RestMethod "$b/api/discovery/offers?item=Poha"  # Screen 7 comparison
Invoke-RestMethod "$b/api/kitchens/id/1"               # Screen 4 storefront
```

## 📁 Architecture

```
my-first-spring-api/            repo root
└─ my-first-spring-api/          Maven project
   ├─ pom.xml                    Spring Boot 4.1.1 · Java 21
   ├─ mvnw / mvnw.cmd            Maven wrapper
   └─ src/main/
      ├─ java/com/example/my_first_spring_api/
      │   controller/   Marketplace, Discovery, BuyerOrder, Favourites, Enquiries, Auth...
      │   service/      DiscoveryService (tabs/categories/comparison), OrderService
      │                 (cutoff & slot enforcement), FavouriteService, EnquiryService...
      │   model/        Kitchen, Product (category · cutoffTime · preorderType · timeSlots),
      │                 Order/OrderItem (scheduledDate/scheduledSlot), Favourite, Enquiry
      │   dto/          ProductDto, KitchenDetailDto, DiscoveryDtos, OrderDto...
      └─ resources/
           application.properties   (port 8081, H2)
           static/                  SPA: index.html · css/styles.css · js/{config,common,buyer,app}.js
```

Key buyer endpoints: `GET /api/discovery/counts|kitchens|categories|items|offers`,
`GET /api/kitchens/id/{id}`, `POST /api/buyer/orders/{draft,place}`,
`POST /api/favourites/{kitchen,product}/{id}/toggle`, `POST /api/enquiries`.

## 🤝 Team workflow

- Default branch **`main`** — `git pull origin main` before starting work.
- Small feature commits; push to `main` when tested.
- Never force-add ignored local files (`data/`, `bin/`, logs, test scripts).

## 🚀 Quick Start — one command

**Only prerequisite: JDK 21** ([adoptium.net](https://adoptium.net), tick *Set JAVA_HOME*). Verify: `java -version` → `21.x`

```bash
git clone https://github.com/utkarshnikhare/my-first-spring-api.git
cd my-first-spring-api
```

Then run the single command for your OS:

| OS | Command | What it does |
|---|---|---|
| macOS / Linux / WSL / Git Bash | `./start.sh` | Verifies JDK 21+, boots the app |
| macOS / Linux (alt) | `make` | Same as `./start.sh` |
| Windows Command Prompt | `start.cmd` | Verifies JDK 21+, boots the app |
| Windows PowerShell | `.\start.ps1` | Verifies JDK 21+, boots the app |

Each runner:
1. Checks JDK 21+ (the **only** prerequisite — clear error if missing)
2. Applies optional `.env` overrides (copy `.env.example` → `.env` to change the port)
3. Boots Spring Boot via the Maven wrapper — **downloads Maven + all dependencies automatically on first run** (2–5 min)
4. Creates the H2 database schema and seeds the demo marketplace on startup

Wait for `Started MyFirstSpringApiApplication`, then open [http://localhost:8081](http://localhost:8081).

> First run downloads dependencies (2–5 min). Subsequent runs start in ~7 seconds.

## 🌐 Open the app

| URL | What it is |
|---|---|
| `http://localhost:8081` | The app — boots on `#/home` with an **empty** marketplace (expected) |
| `http://localhost:8081/swagger-ui.html` | Live REST API documentation |
| `http://localhost:8081/h2-console` | DB console — JDBC URL `jdbc:h2:file:./data/sociomartdb`, user `sa`, blank password |

**5-minute demo:** register with any 10-digit number (OTP appears in the dialog) → Profile → *Start Selling* →
create a kitchen → add 2–3 dishes → in a second browser window register as a buyer → add to cart → checkout →
place order → *I HAVE PAID* → from the seller window: confirm → ready → deliver.

## 📁 Project Structure

```
my-first-spring-api/                 repo root
├─ SocioMart-Project-Report.pdf     full report + setup guide (start here)
├─ docs/                            HTML versions of the report
└─ my-first-spring-api/             Maven project
   ├─ pom.xml                       Spring Boot 4.1.1 · Java 21 · dependencies
   ├─ mvnw / mvnw.cmd               Maven wrapper
   └─ src/main/
      ├─ java/com/example/my_first_spring_api/
      │    controller · service · repository · model · dto · exception
      └─ resources/
           application.properties  (port 8081, H2 file DB, Swagger)
           static/                 the SPA: index.html, css/styles.css, js/app.js
```

## 👥 Team workflow

- Default branch is **`main`** — before starting work: `git pull origin main`
- Keep commits small with clear messages; push to `main` when your change is tested
- Local-only files (`data/`, `bin/`, logs, test scripts) are git-ignored — **never force-add them**
- Factory reset of your local data: stop the app and delete the `data/` folder

## 🩺 Common issues

| Problem | Fix |
|---|---|
| `java -version` shows 17/11/8 | Install Temurin 21 with *Set JAVA_HOME* ticked, restart your terminal |
| "Java was not found" when running `./start.sh` / `start.ps1` | Install JDK 21 and ensure `java` is on your PATH (or set `JAVA_HOME`) |
| Port 8081 already in use | Stop the other instance, or set `PORT=8082` in a `.env` file |
| PowerShell blocks `.\start.ps1` (execution policy) | Run `powershell -ExecutionPolicy Bypass -File .\start.ps1` |
| Marketplace is empty | Expected on a fresh database — the demo data seeds automatically on startup |

Full troubleshooting table: page 6 of [`SocioMart-Project-Report.pdf`](SocioMart-Project-Report.pdf).
