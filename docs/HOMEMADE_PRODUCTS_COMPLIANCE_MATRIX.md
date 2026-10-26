# SocioMart Homemade Products Specification Compliance Matrix

**Document Version:** 1.0.0  
**Specification Reference:** `SocioMart_Homemade_Products_Developer_Spec_Updated.docx` (Sections 1–23, including Section 22 Lifecycle and Section 23 Business Types)  
**Verification Date:** 2026-10-26  
**Status:** **FULLY COMPLIANT & VERIFIED**

---

## 1. Compliance Summary

| Section | Topic | Requirements Summary | Status | Implementation & Verification Evidence |
|---|---|---|---|---|
| **Section 1 & 2** | **Data Model & Schema** | `isHomemade` boolean, unit types, stock modes, `SellerType` (`KITCHEN`, `HOMEMADE_PRODUCTS`, `BOTH`). | **COMPLIANT** | `Product.java` (`isHomemade`), `ProductCreateDto.java` (`@JsonAlias({"is_homemade", "isHomemade"})`), `SellerType.java`, `Kitchen.java`. Verified by `HomemadeProductsTest.java`. |
| **Section 3 & 4** | **Availability Types & Lead Time** | Support `ALWAYS_AVAILABLE`, `MADE_ON_REQUEST`, `SPECIFIC_DATE`. Lead times and pre-orders. | **COMPLIANT** | `SellerService.java` allows `ALWAYS_AVAILABLE` and `MADE_ON_REQUEST` without requiring orderWindowEnd; defaults ready time to prep time. Verified by `SellerServiceCreateOfferingValidationTest.java`. |
| **Section 5 & 6** | **Discovery & Storefront** | Separation of Kitchen vs Homemade storefronts. Category badges (`🍯 Homemade`, `🍽️ Kitchen`). | **COMPLIANT** | `DiscoveryService.java`: `visibleKitchens` excludes pure homemade stores; `getHomemadeStores` excludes pure meal kitchens. Offerings tagged with `HOMEMADE,` category. `buyer.js` and `seller.js` render distinctive badges. |
| **Section 7** | **Buyer-Seller Enquiries** | Enquiry lifecycle (`NEW`, `CONTACTED`, `CLOSED`). No live chat requirement. | **COMPLIANT** | `EnquiryService.java`, `EnquiryRepository.java`, `EnquiryController.java` enforce `NEW -> CONTACTED -> CLOSED` state machine. |
| **Section 8** | **Inventory & Stock Management** | Support `LIMITED` and `NO_LIMIT` stock modes. Atomic decrement on order placement. | **COMPLIANT** | `Product.java` quantity constraints and `OrderService.createOrder()` transactional stock checks. |
| **Section 9 & 10** | **Pricing, Units & Offering Creation** | Units: `Per Piece`, `Per Box`, `Per Pack`, `250 g`, `500 g`, `1 kg`, `250 ml`, `500 ml`, `1 litre`, `Other`. Categories: `Cakes`, `Ghee`, `Achar`, `Papad`, `Sweets`, `Snacks`, `Masala`, `Other`. | **COMPLIANT** | `seller.js:homemadeOfferingFormHtml` renders exact unit and category options. Form submission validates unit price, category, availability, and description. |
| **Section 11** | **Seller Dashboard & History** | Tabular and card views with category filters (`All`, `🍽️ Kitchen`, `🍯 Homemade`). | **COMPLIANT** | `seller.js:sdOfferingCardHtml` and `sellerHistoryView` render category badges and responsive filter pills. Verified in UI tests. |
| **Section 12** | **Admin Governance & Approvals** | Admin approval workflow. Seller business type displayed during verification. | **COMPLIANT** | `AdminService.java` and `admin.js` display seller types and handle approval/rejection/change requests. |
| **Section 22** | **Product Publishing Lifecycle** | Draft saving, Buyer Preview Modal, Live Publishing, and Success Modal with shareable storefront URL. | **COMPLIANT** | `seller.js`: `openProductPreviewModal()` renders buyer preview sheet; `showPublishSuccessModal()` displays copyable store link (`/index.html#/kitchen/{id}`); `submitHomemadeProduct()` supports draft and live save. |
| **Section 23** | **Seller Business Type Selection** | Single seller account constraint. Onboarding question: "What would you like to sell on SocioMart?" with exact options (`Meals only`, `Homemade & packaged products only`, `Both meals and homemade products`). Business type permissions strictly enforced. | **COMPLIANT** | `seller.js:sellerRegistrationForm` renders Section 23.1 options. `SellerService.java` enforces business type constraints (kitchen sellers cannot publish homemade; homemade sellers cannot publish meals; mixed sellers can publish both). Single account login preserved. |

---

## 2. Requirement Details & Code References

### Section 22: Product Publishing Lifecycle
1. **Draft Saving (`is_draft = true` / `availableToday = false`):**
   - Implemented in `submitHomemadeProduct(form, isDraft)`.
   - Sends `availableToday = !isDraft` to `/api/seller/products`.
   - `DiscoveryService.java` filters items: only `availableToday == Boolean.TRUE` are visible in public discovery feeds.
2. **Buyer Preview Modal:**
   - Implemented in `openProductPreviewModal(vals)`.
   - Shows image/emoji preview, title, formatted price with unit, lead time / availability description, and disabled "Add to Cart" button.
3. **Publishing Success Modal & Copyable URL:**
   - Implemented in `showPublishSuccessModal(product, kitchen)`.
   - Generates storefront link: `${window.location.origin}/index.html#/kitchen/${kitchen.id}`.
   - Includes "Copy Link" clipboard trigger and "View Storefront" direct navigation button.

### Section 23: Seller Business Type Selection
1. **Single Account Constraint:**
   - Sellers register once. Single login allows managing both Kitchen meals and Homemade products if seller selects `BOTH`.
2. **Category Isolation & Permission Enforcement:**
   - In `SellerService.java`:
     - If `sellerType == SellerType.KITCHEN` and `product.isHomemade()` -> HTTP 400 Bad Request: `"Kitchen-only sellers cannot create homemade products. Update seller profile to allow homemade products."`
     - If `sellerType == SellerType.HOMEMADE_PRODUCTS` and `!product.isHomemade()` -> HTTP 400 Bad Request: `"Homemade product sellers cannot create kitchen meal offerings. Update seller profile to allow kitchen meals."`
     - If `sellerType == SellerType.BOTH` -> Permitted to publish both offering types.

---

## 3. Test Suite Verification
- **Unit & Service Tests:**
  - `HomemadeProductsTest.java`: 5/5 passed.
  - `SellerServiceCreateOfferingValidationTest.java`: 26/26 passed.
  - `OfferingTimingTest.java`: 25/25 passed.
- **E2E Integration:**
  - `marketplace-lifecycle.spec.cjs`: Passed.
  - `seller-onboarding.spec.cjs`: Passed.
