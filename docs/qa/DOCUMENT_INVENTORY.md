# SocioMart Comprehensive Document Inventory

**Audit Date:** 2026-10-26  
**Status:** COMPLETE & VERIFIED  

---

## 1. Inventory Summary

This inventory catalogues every requirement document, defect specification, architectural addendum, and user-provided visual artifact across the SocioMart project history. Each document has been inspected in its entirety, with full text extracted and analyzed.

---

## 2. Master Document Table

| Document ID | Filename / Identifier | Version / Date | Scope & Primary Focus | Review Status | Key Sections & Acceptance Criteria | Conflicts & Resolution |
|---|---|---|---|---|---|---|
| **DOC-01** | `media_1792823563572_53966006.docx`<br>*(Duplicates: `media_1792895885103_6f7ccabf.docx`, `media_1792996626271_513242e0.docx`; mirrored in `docs/Homemade_Products_Spec_Full.md`)* | V1.0 (Oct 2026) | Complete Homemade Products Developer Spec (Seller model, categories, storefronts, orders, enquiries, lifecycle, monetization) | **FULL REVIEW** (33,555 chars) | **Sections 1–23**:<br>• Sec 1–4: Multi-category seller model (Kitchen, Homemade, Both).<br>• Sec 5–6: Admin review & business type change workflow.<br>• Sec 7–9: Unified seller dashboard & adaptive Add Offering.<br>• Sec 10–13: Product attributes, availability modes & stock controls.<br>• Sec 14–16: Buyer discovery & separate storefronts.<br>• Sec 17–20: Enquiries, lead fees, notifications.<br>• Sec 22: Addendum — Publishing lifecycle (Draft, Preview, Live, Pause).<br>• Sec 23: Addendum — Structured business types & category permissions. | Authoritative specification for Homemade Products. Supersedes earlier partial notes. Confirmed no structural conflicts. |
| **DOC-02** | `media_1792895902676_99d3f035.docx` | V1.0 (Oct 2026) | Order & Payment Status Framework | **FULL REVIEW** (5,447 chars) | **Sections 1–6**:<br>• Sec 1: Independent Order Status (`CONFIRMED`, `DELIVERED`, `CANCELLED`) & Payment Status (`AWAITING_SELLER_VERIFICATION`, `UNPAID`, `PAID`).<br>• Sec 2: Buyer payment choice ("I've paid" vs "I'll pay later").<br>• Sec 3: Seller actions (Mark Payment Received, Mark Delivered, Cancel Order).<br>• Sec 4: Combined display labels.<br>• Sec 5: Concurrency, data preservation, audit trail.<br>• Sec 6: Acceptance Tests 1–13. | Authoritative framework for order lifecycle and seller payment tracking without claiming automated external refunds. |
| **DOC-03** | `media_1792895902677_de4f6871.docx` | V1.0 (Oct 2026) | Buyer Expired Items, Availability Counts & Midnight Refresh Brief | **FULL REVIEW** (9,107 chars) | **Sections 1–11**:<br>• Sec 2: Past-date items excluded from buyer discovery shortcuts & counts.<br>• Sec 3: Sold out / cutoff closed UX excluded from orderable counts.<br>• Sec 6: Idempotent midnight background refresh job.<br>• Sec 7: Authoritative query-time availability pinned to `Asia/Kolkata`.<br>• Sec 8: Occurrence retention & non-destructive storage.<br>• Sec 9: Acceptance Tests 1–14. | Establishes that midnight scheduled job is an auxiliary refresh; query-time filtering must independently guard availability. |
| **DOC-04** | `media_1792992861845_29c75142.docx` | P1 Defect (Oct 2026) | Critical Defect: Pre-Order Becomes Unavailable Before Configured Cutoff | **FULL REVIEW** (9,367 chars) | **Sections 1–8**:<br>• Sec 1–4: Pre-orders configured with tomorrow's cutoff (e.g. 5:00 PM IST) must remain orderable across midnight.<br>• Sec 5: Root cause in boundary calculation.<br>• Sec 6: Single shared availability rule across discovery, cart, checkout, dashboard.<br>• Sec 7: Formal Acceptance Tests A through H. | Pinned to `AppTimeZone.ZONE` (`Asia/Kolkata`). Requires deterministic tests with synthetic clock. |
| **DOC-05** | `media_1793008479434_48e651a7.png`<br>`media_1793008484821_85d76de0.png` | Live Defect Artifact (Oct 2026) | Registration rate-limit lockout screenshot on `/seller.html#/home` | **FULL REVIEW** (Visual & Code) | • Error: `"Registration failed: Too many failed attempts. Please try again in 784 seconds."`<br>• Premature hit recording before validation.<br>• Proxy IP extraction missing `CF-Connecting-IP`.<br>• Misleading exception message. | Resolved in SM-022 via post-validation quota accounting, Cloudflare header parsing, and ticking retry countdown. |
| **DOC-06** | `media_1792996637341_b9b9b172.png` | Live Defect Artifact (Oct 2026) | Seller dashboard offering UI layout anomaly | **FULL REVIEW** (Visual & Code) | • Giant orange `.sd-add` block displacing earnings card.<br>• Layout breaking on mobile/tablet viewports.<br>• Stray syntax in photo rendering. | Resolved in SM-015 via responsive CSS grid (`minmax(280px, 1fr)`), header CTA relocation, and isolated earnings card. |
| **DOC-07** | `docs/qa/PROJECT_QUALITY_RULES.md`<br>`docs/qa/MASTER_TEST_CHECKLIST.md` | V1.0 (Oct 2026) | Mandatory Quality, Concurrency & Security Engineering Rules | **FULL REVIEW** | • Rules against destructive Git commands.<br>• Concurrency isolation on inventory deduction.<br>• Secret leakage prevention.<br>• Full automated regression requirements. | Rules enforced across all PRs and local test execution. |

---

## 3. Unresolved Document Questions or Blockers
- **None.** All 7 source documents and artifacts are present, completely readable, and mutually consistent under the defined versions.
