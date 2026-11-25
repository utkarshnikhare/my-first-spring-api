# ADR 001: Modular Monolith vs. Microservices Architecture

## Status
Accepted

## Context
SocioMart is an apartment/hyperlocal food and homemade goods commerce platform connecting home kitchens, home bakers, and makers with residents in residential societies.
The platform currently supports:
- Buyer portal (discovery, cart, multi-step checkout, payment status reporting, order history, favourites, enquiries)
- Seller portal (storefront management, offering creation/editing, recurring schedule engine, inventory controls, order tracking, payment verification, earnings dashboard)
- Admin / Super Admin console (user moderation, seller onboarding approval, area & society master management, catalog oversight, exportable ledger audits)

We assessed whether to decompose the application into distributed microservices or maintain and harden the modular monolith to support the scaling goal of **1,000+ concurrent active users and 1,000+ registered sellers**.

## Decision
We decided to **maintain and harden the modular monolith architecture** within Java 21 and Spring Boot rather than migrating to distributed microservices.

### Logical Module Boundaries Enforced
The modular monolith enforces strict logical and package boundaries:
1. **Identity & Auth Module** (`AuthService`, `UserSessionAuthorizationFilter`, `AuthRateLimiter`): Session-based credentials, rate-limiting, and security principals.
2. **Location & Society Module** (`LocationService`, `SocietyDirectory`): Multi-area hierarchy, society master data, and delivery coverage mapping.
3. **Seller Storefront Module** (`SellerService`, `SellerAppService`, `KitchenService`): Kitchen profiles, offering lifecycles, and inventory stepper.
4. **Buyer Discovery Module** (`DiscoveryService`, `MarketplaceService`, `BuyerService`): Society-scoped offering filtering and search.
5. **Order & Checkout Module** (`OrderService`, `RecurringScheduleService`): Cart draft, order placement, status state machine, and stock atomicity.
6. **Payment & Ledger Module** (`SellerPaymentConfiguration`, `LedgerService`): UPI/QR verification workflows and financial audit records.
7. **Administration & Moderation Module** (`AdminService`, `AdminAuditService`): Approvals, suspensions, master data governance, and CSV exports.

## Consequences & Evaluation

### Positive Consequences
1. **ACID Transaction Boundaries:** Complex multi-step operations (e.g., placing an order, consuming atomic inventory, and recording ledger snapshots) execute within a single PostgreSQL transaction with zero distributed 2PC overhead or eventual consistency lag.
2. **Zero Network Serialization Latency:** Inter-module calls execute in-memory with sub-microsecond latency, avoiding HTTP/gRPC overhead between microservices.
3. **Operational Cost Efficiency:** Runs efficiently on lightweight infrastructure ($0 to low-cost container plans) without requiring API gateways, service meshes (Istio/Envoy), or distributed tracing collectors.
4. **Simplified Developer Workflow:** A single repository, single build command (`mvn test`), and unified CI/CD pipeline verifiable in minutes.

### Alternatives Considered & Rejected
- **Distributed Microservices (Separate Auth, Catalog, Order, and Payment Services):**
  - *Reason for Rejection:* Introduces distributed transaction failures, network latency between services, cross-service data inconsistencies, and substantially higher cloud costs ($200+/month minimum) with zero benefit for the target scale of 1,000 concurrent users.
