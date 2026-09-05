# SocioMart — Technical Deployment Report (2026-09-05)

## Hosting provider selected
**Render.com — free web service (Docker runtime)**, configured via `render.yaml` (Blueprint) + existing `Dockerfile`.

## Why this architecture
The project is a **frontend + backend + database monolith**:
- Frontend: static HTML/CSS/vanilla JS served by Spring Boot (`src/main/resources/static`)
- Backend: Spring Boot 4.1.1 (Java 21), session-cookie auth, REST API under `/api`
 - Database: **H2** (reseeded with demo data on every fresh boot via `DataInitializer` + `DemoDataSeeder`)
Static hosts (Netlify/GitHub Pages/Vercel) can serve only the frontend — every feature (login, kitchens, orders, favourites, admin) needs the JVM API, so a container host is mandatory.

## Deployment steps (manual, one-time, ~5 min)
1. `git push origin main` (repo already has remote `origin`)
2. https://render.com → Sign in with GitHub (free)
3. New + → Blueprint → select `my-first-spring-api`
4. Render builds the Dockerfile and returns a permanent URL (e.g. `https://sociomart-demo.onrender.com`)

## Public URLs (single Render service)
One Docker web service serves **both** Buyer and Seller from the same Spring Boot
process and the **same** H2 database — no data is split across instances:
- Buyer view: `https://sociomart-demo.onrender.com/`
- Seller dashboard: `https://sociomart-demo.onrender.com/seller.html`
- Health probe: `https://sociomart-demo.onrender.com/api/kitchens`
- API root: `https://sociomart-demo.onrender.com/api`

> The hostname above is the *expected* one for the `sociomart-demo` service name,
> but Render allocates the actual subdomain when the service is first created.
> If `sociomart-demo.onrender.com` is already taken, Render picks a different
> suffix — copy the real URL from the Render dashboard after deploying.

## Services required
1 (single Docker web service). No separate DB service — H2 runs in-process.

## Free-tier limits
- Sleeps after ~15 min without traffic; cold start ~60–90 s
- 750 instance-hours/month (enough for one always-on service)
- 512 MB–1 GB RAM; no credit card required

## Expected demo capacity
H2 with full demo reseed on each fresh boot — hundreds of concurrent demo users are fine; data resets on container restart (acceptable for a demo).

## Performance considerations
- Single jar (63.8 MB) with all assets served from the jar; no CDN needed for a demo
- H2 in-memory = sub-millisecond queries; health check `/api/kitchens` returns 15 seeded kitchens
- Session auth = no JWT bundle overhead; vanilla JS frontend (no framework payload)

## Security considerations
- Spring Security session-based auth, role gating (BUYER/SELLER/ADMIN/SUPER_ADMIN)
- Demo login accepts any 10-digit mobile (no password) — intentional for client demo; **do not use this build for real users**
- H2 console enabled (`/h2-console`) — acceptable for demo, should be disabled for production

## Remaining risks / payment situations
- Free-tier sleep means the first visitor after 15 idle minutes waits ~60–90 s (can look "down") — a free uptime pinger (e.g. cron-job.org hitting `/api/kitchens` every 10 min) prevents sleep
- Render free tier has monthly bandwidth limits (~100 GB) — far above demo needs
- Payment would only be needed if you want: no sleep (paid plan), a custom domain is still free, or persistent DB (paid Postgres) — none required for the demo

## Code audit (2026-09-04)
- OTP: fully removed — no OTP screens, fields, APIs, routes, validation or state; all comments referencing OTP reworded (source scan = 0 functional hits)
- Services / Real Estate / Products: removed (source scan = 0 hits)
- localhost references: only in `config.js` auto-detection (dev convenience, same-origin in production)
- No hardcoded tunnel/dev URLs in source; no dead cloudflared config in the app
- Demo data: 15 kitchens, Food & Kitchen fully active

## Production verification (2026-09-04)
- **Database**: the `demo` profile now inherits the in-memory H2 URL from
  `application.properties` (`jdbc:h2:mem:sociomartdb`) — no `./data` directory,
  no lock files, no ephemeral-disk dependency; the seed runs on every fresh boot
  via `DemoDataSeeder` (idempotent, guarded by a platform_settings flag).
- **Real seller metrics restored**: dashboard views/followers/orders/earnings and
  the Orders summary, item drill-down and Earnings screens were returning
  hardcoded demo numbers; they now aggregate the actual `Order`/`OrderItem` rows
  from the shared H2 database (cancelled orders excluded from revenue/plates).
- **Cancel correctness**: cancelling an order now also decrements the offering's
  `bookedQuantity` (it previously only restored `remainingQuantity`), so the
  buyer-side demand bar and seller aggregates return to their true levels.
- **Local production run** (`java -jar` with `PORT=8082`, `SPRING_PROFILES_ACTIVE=demo`):
  boots in ~9–16 s on H2 in-memory; `/`, `/seller.html`, `/api/kitchens` (15 kitchens),
  seller demo-login, dashboard all return 200.
- **Regression suite** (`e2e-deep-test.ps1`, 45 assertions, delta-based): **PASS=45 FAIL=0**
  against the production jar, covering seller login, live views metric, inventory
  stepper, templates & publish, quick-create parse (never auto-publishes), buyer
  order placement → seller summary/drill/earnings deltas → cancel → aggregates return
  to baseline, sold-out, and validation/404 hardening.
