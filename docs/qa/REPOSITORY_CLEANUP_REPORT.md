# Repository Cleanup Report — SocioMart

**Date:** 2026-10-25
**Branch:** `remediation/launch-blockers` (based on `01b1b39`)
**Scope:** Safe, automated cleanup of clutter/documentation duplicates in the working tree **without** changing, committing, pushing, merging, or deploying code.

## 1. Files Scanned

| Category | Count |
|---|---|
| Tracked working-tree files | 1,143 |
| Recursive inventory (`docs`, module, e2e, docs-tools, backups, root) | ~2,000 files |
| Untracked candidates | 40+ files |
| Documentation (`docs/**`) | ~45 files |

SHA256 hash checks confirmed **no exact-duplicate documents** among `docs/`; overlapping reports are unique and classified for consolidation (category B) or preservation (category C).

## 2. Files Deleted (14 files)

All verified regenerable, non-referenced, and non-functional; none affect build/test.

| # | Path | Reason |
|---|---|---|
| 1 | `app-local.log` | Disposable runtime log |
| 2 | `ci_test_output.txt` | Test output, regenerable |
| 3 | `full_verify.txt` | Diagnostic output, regenerable |
| 4 | `maven_output.txt` | Test output, regenerable |
| 5 | `maven_test.txt` | Test output, regenerable |
| 6 | `maven_test_output.txt` | Test output, regenerable |
| 7 | `out.txt` | Test output, regenerable |
| 8 | `test_output.txt` | Test output, regenerable |
| 9 | `output.txt` | 0-byte scratch output |
| 10 | `tmp.vbs` | Scratch script |
| 11 | `my-first-spring-api/test_output.txt` | Test output, regenerable |
| 12 | `my-first-spring-api/test_results.txt` | Test output, regenerable |
| 13 | `my-first-spring-api/test-results/.last-run.json` | Test cache, regenerable |
| 14 | `my-first-spring-api/e2e/playwright-results.json` | Regenerable test output |

## 3. Regenerable Directories Removed (8)

| Directory | Regenerable via |
|---|---|
| `data/` | Root local runtime DB (gitignored; H2 in-memory for local) |
| `my-first-spring-api/data/` | Module local runtime DB (gitignored; H2 in-memory for local) |
| `my-first-spring-api/target/` | `mvn clean verify` |
| `docs-tools/node_modules/` | `npm install` |
| `docs/shots/` | `docs-tools/*.js` rerun |
| `my-first-spring-api/e2e/node_modules/` | `npm install` (e2e) |
| `my-first-spring-api/e2e/playwright-report/` | `npm test` |
| `my-first-spring-api/e2e/test-results/` | `npm test` |

## 4. Documents Consolidated / Removed (3 docs)

These were removed from the working tree (git `D`) as superseded/obsolete, with no in-repo links broken:

| Deleted doc | Reason |
|---|---|
| `docs/CONTINUATION-NOTE-recurring-schedules-v1.md` | Obsolete session scratch note |
| `docs/COPILOT-HANDOVER-2026-10-09.md` | Superseded handover (own note references FINAL-GITHUB-RENDER-STATUS.md + FINAL-HANDOVER.md) |
| `docs/runtime-validation-report.md` | Temporary validation artifact |

## 5. Quarantine (5 items — preserved, not deleted)

Moved to `docs/quarantine/` for decision, not deleted:

| Item | Notes |
|---|---|
| `.design-seller-dashboard-reference.png` | Design-tool screenshot (tracked) |
| `.design-seller-dashboard-reference.uyer.deployed.js` | Design-tool deployed JS artifact (untracked) |
| `docs/COPILOT-HANDOVER-2026-10-09.md` | Historical handover (user-created) |
| `docs/CONTINUATION-NOTE-recurring-schedules-v1.md` | Session scratch note (user-created) |
| `docs/runtime-validation-report.md` | Validation artifact (user-created) |

## 6. Files Intentionally Preserved

- **Application:** `pom.xml`, `mvnw*`, `.mvn/`, `Dockerfile`, `render.yaml`, `application*.properties`, Flyway `db/migration/*.sql`, `src/main/java/**`, static HTML/CSS/JS, `src/test/**`
- **Tests:** JUnit 5 suite + 4 new test classes (`RevenueReconciliationTest`, `SchedulerWiringAndRecoveryTest`, `MidnightExpiryJobTest`, `UKKitchenBuyerToSellerOrderWorkflowIntegrationTest`)
- **Configuration/IaC:** `.github/workflows/ci.yml`, `start.cmd/ps1/sh`, `Makefile`, `e2e/playwright.config.cjs`, `support.cjs`, specs
- **Documentation (canonical):** `docs/qa/{LAUNCH_GATE_VERIFICATION,REMEDIATION_REPORT,REMAINING_DEFECTS,DEEP_QA_EXECUTIVE_SUMMARY}.md`, `docs/MASTER_DEFECT_REGISTER.md`, `docs/RESPONSIVE_UI_AUDIT.md`, `docs/END_TO_END_VERIFICATION_REPORT.md`, plus all `FINAL-*` reports, `PROJECT-ANALYSIS.md`, `PRODUCTION-READINESS-PLAN.md`, `PRODUCTION-RELEASE-RUNBOOK.md`, `requirements_matrix.md`, READMEs, `REQ1/REQ2_*.txt`, `docs/qa/`
- **Agent tooling:** `.claude/`, `.kilo/`, `.vscode/`, `.github/`
- **Uncommitted-but-preserved:** `response.txt`, `pr_response.json` (referenced in QA summary)
- **Committed snapshots:** `backups/` (redundant with git history — preserved for safety)

## 7. Ambiguous Candidates (preserved — no decision required)

- `backups/` — committed full snapshots; redundant with Git history but user-created; recommend archiving externally.

## 8. Tests & Validation Results

| Check | Result |
|---|---|
| `mvnw -B clean verify` (authoritative CI gate) | **BUILD SUCCESS** |
| Tests | **724 run, 0 failures, 0 errors, 5 skipped** (5 Docker/Testcontainers skips, consistent with repo's own reports) |
| Total time | 1:52 |
| Jar packaged | `my-first-spring-api-0.0.1-SNAPSHOT.jar` |
| Frontend JS syntax (`node --check`) | All passed (admin.js, buyer.js, seller.js, common.js, config.js, app.js, e2e/*.cjs, support.cjs, playwright.config.cjs) |
| Markdown links | No broken references to deleted/quarantined docs |
| Playwright E2E | **NOT RUN** — environment lacks Playwright browser binaries (pre-existing limitation, same as Docker unavailability noted in repo's own reports) |

## 9. Remaining Clutter & Recommendations

- **Regenerable (gitignored, recreate anytime):** `data/`, `target/`, `node_modules/`, `docs/shots/`, e2e `node_modules/playwright-report/test-results` — none tracked in Git.
- **User decision:** move `docs/quarantine/` items out of the tree (or keep), and decide on `backups/`.
- **Remediation branch** should be reviewed/merged as documented; no commit/push/deploy performed by this cleanup.

---

### Terminated Git Status (after cleanup)

```
D .design-seller-dashboard-reference.png
 M .github/workflows/ci.yml
 D docs/CONTINUATION-NOTE-recurring-schedules-v1.md
 D docs/COPILOT-HANDOVER-2026-10-09.md
 D docs/runtime-validation-report.md
 M my-first-spring-api/e2e/marketplace-lifecycle.spec.cjs
 M my-first-spring-api/e2e/mobile-registration.mobile.spec.cjs
 M my-first-spring-api/e2e/playwright.config.cjs
 M my-first-spring-api/e2e/support.cjs
 M my-first-spring-api/src/main/resources/application-local.properties
 M my-first-spring-api/src/main/resources/static/admin.html
 M my-first-spring-api/src/main/resources/static/js/buyer.js
 M my-first-spring-api/src/main/resources/static/js/seller.js
 M my-first-spring-api/src/main/resources/static/seller.html
 M my-first-spring-api/src/test/java/.../OrderPriceImmutabilityTest.java
 M render.yaml
?? docs/END_TO_END_VERIFICATION_REPORT.md
?? docs/MASTER_DEFECT_REGISTER.md
?? docs/qa/
?? docs/quarantine/
?? docs/RESPONSIVE_UI_AUDIT.md
?? my-first-spring-api/README.md
?? my-first-spring-api/src/test/java/.../{MidnightExpiryJobTest,RevenueReconciliationTest,SchedulerWiringAndRecoveryTest,UKKitchenBuyerToSellerOrderWorkflowIntegrationTest}.java
?? pr_response.json
?? response.txt
```

**Deletion summary:** 14 files + 8 regenerable directories removed; 3 docs consolidated/removed; 5 items quarantined (preserved).


