# SocioMart — Security Architecture & Hardening Specification

This document details the security posture, authentication architecture, role-based access controls (RBAC), data sanitization, and vulnerability defenses implemented in SocioMart.

---

## 1. Authentication & Session Architecture

- **Stateful JDBC Sessions**:
  - User sessions are stored in PostgreSQL (`SPRING_SESSION`) via Spring Session JDBC.
  - Cookies are marked `HttpOnly` to eliminate client-side JavaScript access and mitigate XSS session hijacking.
  - SameSite is enforced (`SameSite=Lax` or `Strict`) to prevent Cross-Site Request Forgery (CSRF).
- **Session Isolation & Destruction**:
  - Logout explicitly invokes `session.invalidate()` on the server and purges `SecurityContextHolder`.
  - Logging out immediately terminates server-side session validity and clears client-side UI tokens.
- **Demo Login Shielding**:
  - All direct demo login shortcuts (`/api/auth/demo-login`, `/api/seller-app/demo-login`) are explicitly blocked (`denyAll()`) in `SecurityConfig.java`.

---

## 2. Role-Based Access Control (RBAC)

Spring Security enforces strict role boundaries:

| Role Name | Authority Identifier | Allowed Scopes & Endpoints |
| :--- | :--- | :--- |
| **Anonymous** | Unauthenticated | Public storefronts, `/api/discovery/**`, `/api/marketplace`, `/api/auth/login`, `/api/health`, `/api/version` |
| **Buyer** | `ROLE_BUYER` | `/api/buyer/**`, `/api/orders/**`, `/api/favourites/**`, personal cart and profile updates |
| **Seller** | `ROLE_SELLER` | `/api/seller/**`, `/api/seller-app/**` (strictly scoped to seller's owned kitchen & offerings) |
| **Admin** | `ROLE_ADMIN` | `/api/admin/**`, user moderation, kitchen approvals, order reviews, and CSV exports |
| **Super Admin** | `ROLE_SUPER_ADMIN` | `/api/superadmin/**`, area & society creation, role elevations, full system audit |

### Horizontal Privilege Escalation Defenses (IDOR)
- **Seller Ownership Checks**: Every endpoint updating an offering, occurrence, or order checks `sellerService.requireCurrentSeller(session)` and verifies that `entity.getSeller().getId().equals(currentUser.getId())`. Unauthorized updates return HTTP 403 Forbidden.
- **Buyer Privacy Protection**: Orders belong to the placing buyer (`order.getBuyer().getId()`). Browsing or modifying another buyer's order history is blocked at the service layer.

---

## 3. Data Protection & Vulnerability Defenses

### A. Spreadsheet Formula Injection (CSV Injection)
- Exported Admin CSV files (`/api/admin/exports/{domain}.csv`) process user-submitted text fields through `neutraliseFormula(String s)` in `AdminService.java`.
- Any field starting with `=`, `+`, `-`, `@`, `\t`, or `\r` is automatically prepended with a single quote (`'`), rendering it inert when opened in Microsoft Excel, Google Sheets, or LibreOffice Calc.
- Fully validated by the `AdminExportTest.java` integration test suite.

### B. Cross-Site Scripting (XSS)
- Client-side template rendering uses strict character escaping (`esc()`) before inserting dynamic content into the DOM.
- `innerHTML` injections are sanitized.
- Server returns `X-Content-Type-Options: nosniff` on all responses.

### C. SQL Injection
- Data access is implemented exclusively using Spring Data JPA with parameterized Hibernate HQL/JPQL queries and repository query methods.
- Dynamic concatenated string SQL is strictly prohibited.

### D. Security Headers
The following headers are emitted on all production responses:
- `X-Frame-Options: SAMEORIGIN` (or `DENY` on static assets)
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Cache-Control: no-cache, no-store, must-revalidate` on all sensitive authentication and HTML routes.
