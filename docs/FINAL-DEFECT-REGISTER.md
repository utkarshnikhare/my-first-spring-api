# SocioMart Final Defect and Blocker Register

Verification snapshot: 2026-10-09. A blocker is not necessarily an application-code defect. No live service
was restarted or changed to investigate these items.

| ID | Severity | Item / reproduction | Root cause | Fix / regression evidence | Status |
|---|---|---|---|---|---|
| QA-001 | Medium (test reliability) | Running the full suite after the test helper's 22:00 cutoff caused three `DemoMarketplaceE2ETest` errors | Late-hour fixture fallback created a future pre-order whose feature was disabled for the test seller | Stabilized the isolated test helper as same-day, 23:59 cutoff; targeted class 3/3 and full suite 589/0/0/1 passed | FIXED in this PR; CI pending |
| OPS-001 | High (release blocker) | A redeploy/restart of the existing Render service may lose non-seed demo data | Render uses in-memory H2; remote H2 Console is disabled; no complete supported export/restore has been verified | No runtime change attempted. Owner must provide a complete non-restarting export and verified restore before deployment | BLOCKED |
| OPS-002 | High (production readiness) | Production identity and payment cannot be safely used as real-customer services | Demo mobile-number authentication and simulated/manual payment state; no gateway/OTP/account recovery | No production migration or external payment changes authorized | OPEN / OUT OF SCOPE |
| OPS-003 | Medium (production hardening) | Public OpenAPI/Swagger paths are permitted by current demo security configuration | Documentation endpoints are enabled and not gated for production use | API docs are omitted from public Quick Links; restrict/disable before production | OPEN |
| QA-002 | Low (live evidence gap) | Buyer pre-order/order flow cannot be replayed against current public data | No preorder/recurring item was visible in the observed live Buyer Food & Kitchens view | Integration coverage passes; no live order or schedule was created | NOT TESTED |
| QA-003 | Low (live evidence gap) | Admin operational workflows were not exercised live | No separately authorized Admin session was used | Admin route smoke reached the sign-in page; service/security tests cover role boundaries | NOT TESTED |

No other reproducible application-code defect was confirmed in the checks recorded here. Browser evidence
included an external example image request blocked by the browser; no live data or image URL was changed.
