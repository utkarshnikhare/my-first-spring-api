# ADR 002: Dual-Backend Evaluation and Netlify Independence Architecture

## Status
Accepted

## Context
A key objective requested was evaluating a deployment architecture where Netlify operates completely independently without requiring Render at runtime, while simultaneously preserving Render for the existing Spring Boot backend.

We conducted a technical feasibility, consistency, and risk analysis of this requirement.

## Findings & Technical Constraints

1. **Hosting Platform Characteristics:**
   - **Netlify:** Designed exclusively for static frontend hosting, edge redirects, and short-lived, stateless serverless functions (Node.js, Go, Python via AWS Lambda). Netlify **cannot** run persistent JVM Spring Boot container services.
   - **Render:** Runs Dockerized Linux containers hosting persistent Java runtimes, thread pools, and long-lived connection pools.

2. **Dual-Backend Divergence Hazards:**
   - Re-implementing SocioMart's backend on Netlify Functions (Node.js/TypeScript) would mean maintaining **two parallel codebases** for 192 source files, complex business rules, order status state machines, inventory decrement locks, occurrence generators, and security filters.
   - Any divergence in business logic between the two backends leads to data corruption, subtle pricing/inventory discrepancies, and doubled maintenance cost.

3. **Database Connection Exhaustion Risk on Supabase:**
   - Serverless functions spawn on-demand. Under 1,000 concurrent users, Netlify Functions would initiate hundreds of ephemeral database connections, immediately exceeding PostgreSQL connection limits and saturating Supabase.
   - In contrast, the containerized Spring Boot backend uses HikariCP with connection pool routing (`RootTransactionPool` and `NestedTransactionPool`) to strictly cap connections to 10.

## Decision & Target Architecture

1. **Preserve Single Authoritative Backend Codebase:**
   - Do **NOT** duplicate business logic into a separate Node.js/Netlify Functions codebase. The Spring Boot backend remains the single source of truth for business logic and data integrity.

2. **Netlify-Independent Hosting Strategy (Zero-Codebase-Divergence Portability):**
   - SocioMart's repository already includes a standalone, production-tested `Dockerfile` at the repository root.
   - For true infrastructure independence and high availability, a secondary container instance can be hosted on a cloud container platform (such as Google Cloud Run, Fly.io, or Railway) executing the **identical Docker image**.
   - Netlify acts as the high-speed Global CDN for the static PWA assets (`/`, `/seller.html`, `/admin.html`), with its edge redirect configured to proxy `/api/*` to the active container backend.
   - When Netlify's free build minutes are exhausted (as encountered on the free tier), manual Drag-and-Drop deployment of `src/main/resources/static` to Netlify deploys in seconds using **0 build minutes**, or the application can be served directly from the container backend URL (`https://sociomart-demo.onrender.com`).

## Consequences
- Preserves 100% of existing functionality with zero risk of logic divergence.
- Eliminates database connection exhaustion.
- Provides true portability across any Docker-compatible cloud host without vendor lock-in.
