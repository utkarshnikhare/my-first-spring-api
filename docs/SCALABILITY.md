# SocioMart Enterprise Scalability & Capacity Planning Guide

## 1. Executive Summary & Workload Models
This document defines empirical and projected capacity models for SocioMart across 5 distinct growth tiers, ranging from the current pilot phase to 100,000 registered users.

### Key Architectural Baseline
- **Application Model:** Java 21 / Spring Boot 4.x Modular Monolith.
- **Connection Model:** HikariCP with Transaction Pool Routing (`RootTransactionPool` + `NestedTransactionPool`).
- **Database:** PostgreSQL on Supabase (with PgBouncer session/transaction pooler support).
- **Frontend / Static Layer:** High-performance PWA cached at Edge CDN (Netlify) and served in-memory by Spring Boot.
- **State Model:** Distributed Spring Session JDBC persisted in PostgreSQL.

---

## 2. Capacity Growth Forecast Matrix

| Metric / Parameter | Tier 1: Pilot / Baseline | Tier 2: 1,000 Concurrent (Target) | Tier 3: 5,000 Concurrent | Tier 4: 10,000 Concurrent | Tier 5: 100,000 Registered |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Registered Users** | 500 – 1,000 | 5,000 – 10,000 | 25,000 – 50,000 | 50,000 – 75,000 | 100,000+ |
| **Active Sellers** | 50 – 100 | 1,000 | 2,500 | 5,000 | 10,000 |
| **Concurrent Active Users** | 50 – 100 | **1,000** | 5,000 | 10,000 | 20,000 (peak) |
| **Peak Requests / Sec (RPS)** | 15 – 25 RPS | **150 – 250 RPS** | 750 – 1,250 RPS | 1,500 – 2,500 RPS | 3,000 – 5,000 RPS |
| **Transactions / Sec (TPS)** | 1 – 3 TPS | **15 – 25 TPS** | 75 – 125 TPS | 150 – 250 TPS | 300 – 500 TPS |
| **DB Connections (Hikari)** | 10 | **20 – 30** | 50 – 60 | 80 – 100 | 120 – 150 (PgBouncer) |
| **Backend Compute (vCPU / RAM)**| 0.5 vCPU / 512 MB | **1 – 2 vCPU / 2 GB** | 2 – 4 vCPU / 4 GB | 4 – 8 vCPU / 8 GB | 8 – 16 vCPU / 16 GB |
| **PostgreSQL Storage** | < 1 GB | **5 – 10 GB** | 25 – 50 GB | 50 – 100 GB | 200 – 500 GB |
| **Media / Image Storage** | < 2 GB | **20 – 50 GB** | 100 – 250 GB | 250 – 500 GB | 1 – 2 TB |
| **Monthly Est. Infra Cost** | **$0** (Free Tier) | **$25 – $50 / mo** | **$120 – $200 / mo**| **$300 – $500 / mo** | **$800 – $1,200 / mo**|

---

## 3. Workload Characterization & Traffic Breakdown

Under 1,000 concurrent active users:
- **85% Read Traffic:** Browsing marketplace, viewing kitchen detail, searching offerings, checking order status.
  - Sub-50ms latency achievable through PostgreSQL composite indexes (`V28__enterprise_scalability_indexes.sql`) and HTTP gzip caching (`Cache-Control: public, max-age=86400`).
- **10% Cart & Session Mutation:** Adding to cart, toggling favourites, submitting customer enquiries.
  - Handled cleanly via Spring Session JDBC with sub-5ms row updates.
- **5% Financial & Order Mutations:** Order creation, status advancement, seller payment verification, inventory decrements.
  - Atomic SQL execution ensures zero lock contention across different offerings.

---

## 4. Scaling Bottlenecks & Required Evolution

### Free Tier Limitations (Current State: Tier 1)
- **Render Free Web Service:** Spins down after 15 minutes of inactivity; 512 MB RAM limit.
  - *Sufficient for:* Initial user testing, developer staging, and non-continuous pilot traffic.
  - *Hard Limit:* Cannot sustain sustained 1,000 concurrent users without container memory pressure.
- **Supabase Free Tier:** 500 MB database storage, 2 active projects, 50 direct connections.
  - *Sufficient for:* Up to 10,000 orders and ~500 sellers.

### Required Evolution for Tier 2 (1,000 Concurrent Users / 1,000 Sellers)
- **Compute:** Upgrade Render Web Service from Free ($0) to **Starter / Standard ($7 – $25/mo)**:
  - 100% persistent uptime (zero cold starts, zero 504 timeouts).
  - 1–2 GB RAM for JVM heap headroom.
- **Database:** Supabase Pro Plan ($25/mo):
  - Dedicated compute resources, point-in-time recovery (PITR), up to 8 GB compute storage, 250 direct connections.
- **Total Minimum Production Cost for Tier 2:** **~$32 – $50 / month**.
