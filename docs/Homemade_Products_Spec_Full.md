SocioMart - Homemade Products

Detailed V1 Functional & Developer Specification

Covers seller model, mixed Kitchen + Homemade sellers, offerings, storefronts, orders, enquiries, location, notifications and monetization

1. Purpose

SocioMart will add Homemade Products as a separate buyer-facing category for products such as cakes, ghee, achar, papad, sweets, snacks, masala and other homemade or made-to-order food products. The feature must reuse the existing Kitchen platform wherever possible.

A seller may sell only Kitchen food, only Homemade Products, or both. The seller must use one SocioMart seller account; the system must not require a second account simply because the seller operates in both categories.

Core architecture: Seller is the person/account. Kitchen and Homemade are storefront/offering categories. Do not permanently classify the person as only one type.

2. Buyer Home Placement

Add a separate tile on the main Buyer Home:

Food & Kitchens - daily food, meals and pre-orders

Homemade Products - Cakes, Achar, Papad, Ghee, Sweets & more

Services - future category for beauty, laundry, tailoring, etc.

Homemade Products must not be mixed into Food & Kitchens discovery and must not be placed under Services.

3. Seller Account Model

One seller account can support the following combinations:

Seller Example

Enabled Categories

Buyer-facing Result

Aarti

Kitchen only

Aarti's Kitchen appears in Food & Kitchens.

Meena

Homemade only

Meena's Homemade appears in Homemade Products.

Sunita

Kitchen + Homemade

Kitchen storefront appears in Food & Kitchens; Homemade storefront appears in Homemade Products.

3.1 Registration / Onboarding

During seller onboarding, ask:

What would you like to sell?

Food / Kitchen

Homemade Products

Allow selection of one or both.

This selection defines the seller's initial setup but must NOT permanently lock the seller. A Kitchen-only seller can enable Homemade Products later, and a Homemade seller can enable Kitchen later, subject to any admin approval rules.

If Kitchen is enabled, collect Kitchen Name. If Homemade Products is enabled, collect Store Name / Homemade Store Name.

Example for a mixed seller:

Seller: Aarti Sharma

Kitchen storefront: Aarti's Kitchen

Homemade storefront: Aarti's Homemade

Both storefronts belong to the same seller account, dashboard, orders, earnings and profile.

4. Seller Navigation

Keep one common seller application. Do not build separate Kitchen and Homemade seller apps.

Recommended seller navigation remains:

Home / Dashboard

Manage Store / Kitchen

Orders

My Offerings

Earnings

For a seller with both categories, Manage Store can provide two sections/tabs: Kitchen Profile and Homemade Store Profile.

5. My Offerings - Common Management Page

My Offerings is the common place where the seller creates and manages everything they sell. This applies to Kitchen-only, Homemade-only and mixed sellers.

Page structure:

My Offerings+ Add OfferingFilters: All | Kitchen | HomemadeOffering History / Existing Offerings

Every offering card should visibly show its category, for example:

Poha - Kitchen

Homemade Cow Ghee - Homemade

Chocolate Cake - Homemade

The existing Add Offering functionality from the Seller Dashboard should be reused. Do not create two unrelated offering-management systems.

6. + Add Offering - First Step

When a seller who is enabled for both categories taps + Add Offering, do not immediately open the existing Kitchen form. First ask:

What are you listing?

Kitchen Offering - breakfast, lunch, dinner or snacks

Homemade Product - cakes, ghee, achar, papad, sweets and other homemade products

If the seller has only one category enabled, V1 may skip this selection and directly open the relevant form.

Implementation preference: Use one shared Create Offering component and show/hide/change fields based on Offering Type. Avoid duplicating the entire form and business logic.

7. Kitchen Offering Form

The current Create Offering form remains appropriate for Kitchen items. Keep fields such as:

Photos

Item Name

Short Description

Price

Unit

Offering For: Today / Tomorrow / Choose Date

Orders Open - optional

Orders Close

Delivery / Ready By

Quantity Available - optional

Breakfast / Lunch / Dinner / Snacks

Mark as Favourite / template where already supported

Kitchen offerings appear only under Food & Kitchens.

8. Homemade Product Offering Form

Use the same visual form/component but adapt the fields for products that may remain available for long periods or may be made on request.

8.1 Common Fields

Photo(s)

Product Name

Short Description

Price

Unit

Quantity Available - optional

Product Category

Availability Type

Suggested Unit values:

Per Piece / Box / Pack

250 g / 500 g / 1 kg

250 ml / 500 ml / 1 litre

Other

Suggested Product Category values:

Cakes

Ghee

Achar

Papad

Sweets

Snacks

Masala / Other

8.2 Availability Type

Replace Kitchen-specific Offering For behaviour with:

Always Available

Made on Request

Specific Date

8.3 Always Available

Use for products such as ghee, achar or papad that the seller generally keeps available. The product stays LIVE until the seller pauses it, sells out, or changes availability.

Orders Open / Orders Close are not required.

Delivery/Ready time can be expressed as preparation/fulfilment time if needed.

Example: Usually ready in 1 day.

8.4 Made on Request

Use for products such as custom cakes.

Ask for Minimum Preparation Time.

Suggested values: Same Day, 1 Day, 2 Days, 3 Days, Custom.

Buyer sees wording such as 'Made on Request - Order at least 1 day in advance'.

8.5 Specific Date

Use for festival/special products such as Diwali laddoos or seasonal mango items.

Reuse the existing time-window fields:

Orders Open - optional

Orders Close / buyer-facing label: Order By

Delivery / Ready By

Example: Order by 5 Nov; Delivery by 7 Nov.

9. Buyer Homemade Products Discovery

Clicking Homemade Products opens a dedicated discovery page.

Search by product name OR seller/store name.

Category filters: All, Cakes, Ghee, Achar, Papad, Sweets, Snacks, Other.

Show seller/store cards, similar to Kitchen discovery.

Each seller card can show store name, speciality, image, example products, service availability and View Products.

Only eligible stores for the buyer's society should be shown after buyer location is known.

10. Homemade Seller Storefront

Every Homemade seller gets a storefront similar to a Kitchen Page. For a mixed seller, this is separate from their Kitchen storefront.

Recommended order:

Store name, seller name, profile image and availability

Speciality / short description

Current product offerings

Send Enquiry section

About

Gallery / social information

Example:

250 g Chocolate Cake - Rs 250 - Order Now

250 g Mixed Fruit Cake - Rs 280 - Order Now

500 g Special Mango Cake - Rs 550 - Order Now

Do not mix Poha/Thali and Ghee/Achar on the same buyer-facing storefront for a mixed seller. Buyer discovery should remain category-specific.

11. Order Now Flow

Normal Homemade Product orders should reuse the existing SocioMart order flow:

Storefront -> Product -> Quantity -> Order Summary -> Payment Choice -> Order Placed

Reuse:

Buyer profile and OTP

One storefront/seller per order

Multiple products from the same Homemade storefront

Service-area validation

'I Have Paid' / 'I'll Pay Later'

Separate Order Status and Payment Status

Seller Orders and Earnings

Admin order visibility and recorded sales

12. Send Enquiry

Homemade storefronts additionally support Send Enquiry for custom requirements that do not match a listed product.

Example: 'Can you make a 1.5 kg eggless chocolate football-theme cake for Sunday?'

Recommended V1 enquiry form:

Requirement / Message - mandatory

Preferred date - optional

Quantity - optional

Reference image - optional; can move to V1.1 if upload adds unnecessary work

Submit Enquiry

Do not build live buyer-seller chat in V1.

Minimal enquiry states:

NEW

CONTACTED / RESPONDED

CLOSED

13. Location & Society Rules

Homemade Products must reuse the common SocioMart Area/Society system.

Admin defines Area -> Admin defines Societies -> Seller selects supported Societies -> Buyer selects home Society -> system matches Society.

For a mixed seller, service coverage can initially be shared or separately configurable per storefront. Recommended V1:

Allow Kitchen and Homemade storefronts to have their own supported-society selections.

Reason: a seller may deliver daily food only inside their own society but may be willing to supply packaged ghee/achar to nearby societies.

Eligibility rule:

Buyer Society is in Storefront Supported Societies -> show and allow ordering.

Otherwise hide from normal discovery and block ordering even through a direct shared URL.

14. Notifications - Critical for Homemade Products

Homemade sellers may receive orders very infrequently and cannot be expected to open SocioMart every day. Therefore a new order/enquiry must actively alert the seller.

Recommended layered approach:

Stage

Action

Purpose

Immediately

Push notification + in-app unread badge

Primary alert.

If not acknowledged

Reminder push after configurable interval

Reduce missed orders.

Still not acknowledged

Optional SMS or WhatsApp fallback

Reach sellers who are not opening the app.

Seller acknowledges

Stop escalation

Avoid unnecessary messages/cost.

Push should be the primary low-cost channel. SMS/WhatsApp can be optional fallback because those channels can create usage/compliance costs.

14.1 Seller Acknowledgement

For Homemade Products, prefer explicit seller acknowledgement/confirmation of a new order. Do not assume that an infrequent seller saw the order.

New Order -> Confirm / Acknowledge

New Enquiry -> View / Acknowledge

Until acknowledged, keep the item visually highlighted and eligible for reminder/escalation.

15. Mixed Seller - End-to-End Example

Seller: Aarti Sharma

Kitchen storefront: Aarti's Kitchen

Homemade storefront: Aarti's Homemade

Aarti opens My Offerings and taps + Add Offering.

Scenario A:

Chooses Kitchen Offering

Creates Poha, Rs 40/plate, Today, closes 10:30 AM, ready 12 PM, Breakfast

Poha appears under Food & Kitchens -> Aarti's Kitchen

Scenario B:

Chooses Homemade Product

Creates Cow Ghee, Rs 550/500ml, Always Available, category Ghee

Ghee appears under Homemade Products -> Aarti's Homemade

Scenario C:

Creates Chocolate Cake, Rs 500/500g, Made on Request, minimum preparation 1 day

Cake appears under Homemade Products -> Aarti's Homemade

All three offerings are managed from Aarti's single My Offerings page and all orders appear in her common Orders area with a visible source/category.

16. Monetization

Normal Homemade Product orders should use the same monetization mechanism already designed for Kitchen orders wherever applicable.

Track recorded order value.

Apply the existing seller/platform-fee rules.

Do not call recorded order value verified revenue if SocioMart does not process the payment.

16.1 Enquiry Monetization

If enquiries are monetized, do not charge merely for tapping Send Enquiry. Record/charge only after the buyer successfully submits a valid enquiry.

Qualified Enquiry Submitted = billable event.

Lead fee should be configurable by Super Admin, including Rs 0 for the pilot.

Add enquiry charges to the seller ledger rather than building a separate micro-payment flow.

Future option: first N enquiries free or subscription/unlimited enquiries.

17. Admin / Super Admin

Reuse the existing admin application and add Homemade Products as a category/filter.

Approve/manage Homemade storefronts.

See whether seller is Kitchen, Homemade, or Both.

View/edit permitted storefront data.

Pause/suspend/remove storefront.

View offerings, orders, enquiries and recorded sales.

View service-area selections.

View enquiry counts and seller acknowledgement times.

Super Admin can configure enquiry lead fee and existing platform fee rules.

Audit admin changes.

18. Suggested Data Model / Developer Guidance

Prefer shared entities rather than duplicating the Kitchen backend.

Seller Account

Storefront / Business Profile

Storefront Type: KITCHEN or HOMEMADE_PRODUCTS

Offering

Offering Type: KITCHEN or HOMEMADE

Homemade Availability Type: ALWAYS_AVAILABLE / MADE_ON_REQUEST / SPECIFIC_DATE

Order

Payment Status

Service Area / Supported Societies

Favourite - storefront level

Enquiry - new entity

Notification Event / Acknowledgement

Seller Ledger / Platform Fee

Store and match IDs for areas/societies/storefronts rather than relying on display-name text.

19. UI Behaviour Rules

Do not display Kitchen ratings anywhere if ratings are removed from the current V1.

Do not show buyers how many units were booked. Show scarcity only when remaining quantity is 5 or less, e.g. 'Only 4 left'.

At zero remaining quantity, show Sold Out.

For dated/pre-order products, buyer-facing wording should use 'Order by' rather than 'Cut-off'.

Keep seller name available in addition to storefront/kitchen name.

My Offerings should clearly identify whether each item belongs to Kitchen or Homemade.

Use the same SocioMart visual design system for Kitchen and Homemade pages.

20. V1 Non-goals

No separate Homemade seller app.

No live buyer-seller chat.

No payment gateway requirement.

No delivery partner integration.

No separate Area/Society system for Homemade Products.

No permanent seller lock to only Kitchen or only Homemade.

No mixing Kitchen and Homemade products on one buyer storefront.

No advanced custom-product configurator for cakes in V1.

21. Developer Acceptance Checklist

? Buyer Home has a separate Homemade Products tile.

? One seller account can support Kitchen, Homemade, or Both.

? Seller is not permanently locked to the category selected during initial registration.

? Mixed seller has separate Kitchen and Homemade buyer storefronts.

? My Offerings is shared and supports All / Kitchen / Homemade.

? + Add Offering asks offering type when seller supports both.

? Kitchen offering continues using the current Kitchen form/logic.

? Homemade form supports Always Available, Made on Request and Specific Date.

? Homemade category replaces Breakfast/Lunch/Dinner/Snacks with product categories.

? Always Available products can remain live until paused/sold out.

? Made on Request supports minimum preparation time.

? Specific Date reuses Orders Open / Order By / Delivery By.

? Homemade storefront supports Order Now and Send Enquiry.

? Order Now reuses current order/payment/profile logic.

? Enquiry submission is stored and visible to seller.

? Normal Homemade orders use existing monetization rules.

? Enquiry fee is triggered only on successful submission and is configurable.

? Homemade storefront uses common Area/Society infrastructure.

? Kitchen and Homemade service coverage can be configured independently for mixed sellers.

? Seller gets immediate push for new Homemade order/enquiry.

? Unacknowledged Homemade orders/enquiries support reminder/escalation.

? Seller can acknowledge/confirm an order or enquiry.

? Admin can identify and manage Kitchen/Homemade/Both sellers.

? Buyer discovery never mixes Kitchen offerings into Homemade storefront or vice versa.

22. Final V1 Rule

One seller account. Separate buyer storefronts. Shared seller management.

Kitchen offerings appear under Food & Kitchens. Homemade offerings appear under Homemade Products. A seller may participate in either or both. My Offerings, Orders, Earnings, profile identity, admin management and core order infrastructure should be reused. Only category-specific fields, Homemade availability behaviour, Send Enquiry and stronger notification/acknowledgement handling need to be added.

ADDITIONAL REQUIRED SPECIFICATION

Homemade Product Publishing Lifecycle

Addendum to the existing SocioMart Homemade Products V1 developer specification

22. Product publishing lifecycle — required

The existing specification defines product fields and availability types but does not fully define how sellers save, preview, publish, pause and resume products. Implement the following lifecycle by extending the existing Add Offering and My Offerings components. Do not create a separate product-management application.

22.1 Seller eligibility

Only an approved seller with an active Homemade Products storefront may publish a product.

Pending, rejected, suspended sellers and paused/suspended Homemade storefronts cannot publish buyer-visible products.

Seller/storefront approval is separate from product publication. Unless current policy explicitly requires per-product admin moderation, a valid product should go live immediately after the seller selects Publish.

Kitchen-only sellers cannot publish Homemade products unless their account is enabled for Homemade Products or Both. Mixed sellers retain separate Kitchen and Homemade buyer storefronts.

22.2 Seller flow

Seller opens My Offerings ? + Add Offering. If the seller supports both categories, ask Kitchen Offering or Homemade Product; if only one category is enabled, open its form directly.

Seller selects Homemade Product and completes the existing adaptive form, including product name, description, photos, price, unit, category, stock mode and availability type.

Provide three clear actions: Save Draft, Preview and Publish. Publish is the primary action. Save Draft and Preview must not make the item visible to buyers.

Preview must show the buyer-facing appearance: product image, name, category, price/unit, seller/store name, availability message, fulfilment details and Order Now or Send Enquiry action as applicable.

When Publish is clicked, validate fields on both frontend and backend. If invalid, keep the form open, highlight missing/invalid fields and explain how to fix them.

After successful publication, persist the record, show a success message and provide View Product and Copy/Share Storefront Link actions. The link must use existing routing; do not invent a new URL scheme.

Buyer visibility must respect seller approval, storefront status, service-area/society eligibility and current availability rules.

22.3 Publish validation

At minimum, validate these before Publish:

Product name: required; reject blank or whitespace-only values.

Product category: required; use the existing category list (Cakes, Ghee, Achar, Papad, Sweets, Snacks, Masala/Other).

Price: required, numeric and greater than zero.

Unit: required (e.g. per piece/box/pack, 250 g, 500 g, 1 kg, 250 ml, 500 ml, 1 litre or Other).

Availability type: required.

Stock mode: explicitly choose Limited Quantity or No Limit. Limited Quantity requires a positive integer. Do not silently treat an empty quantity as unlimited.

Availability-specific required fields must be valid.

Product photo: follow the existing product-image requirement. If photos are mandatory, require at least one valid uploaded image; if optional, use a consistent placeholder rather than failing unexpectedly.

Seller and Homemade storefront must be approved, active and eligible to publish.

22.4 Availability-specific publication behaviour

A. Always Available

Use for products normally kept in stock, such as ghee, achar or papad.

After Publish, status becomes LIVE. The listing remains visible/orderable until the seller pauses it, marks it sold out, finite stock reaches zero, or the storefront is disabled.

Orders Open and Orders Close are not required. An optional fulfilment estimate can be shown, e.g. “Usually ready in 1 day”.

Finite stock is validated and decremented by the backend on successful order placement; prevent overselling.

Sold Out blocks new orders but does not delete the product. Seller may replenish stock and explicitly mark it available again.

B. Made on Request

Use for products prepared after an order, such as custom cakes.

Minimum Preparation Time is required: Same Day, 1 Day, 2 Days, 3 Days or Custom. Custom requires a valid value.

After Publish, status becomes LIVE and the buyer sees wording such as “Made on Request — order at least 1 day in advance”.

Never promise a fulfilment date earlier than the minimum preparation time. If checkout supports selecting a requested date, validate it; otherwise show the earliest expected ready date/estimate.

If a buyer's requirement cannot be fulfilled through the normal Order Now flow, use the existing Send Enquiry flow. Do not build live chat as part of this feature.

C. Specific Date

Use for festival, seasonal or date-limited products.

Require the relevant product/availability date, Order By (buyer-facing label for Orders Close), and Ready/Delivery By date/time as applicable. Validate the order of dates and reject past dates.

Orders Open is optional; if blank, ordering opens immediately after publication, subject to other rules.

After Publish, show an eligible future item as PRE-ORDER and display “Order by [date/time]” and “Ready/Delivery by [date/time]”.

After Order By passes, reject new orders and exclude the item from orderable counts. Same-day display may show ORDERS CLOSED if consistent with the agreed buyer UX.

After the relevant date passes, hide the expired occurrence from active buyer discovery while preserving orders and history. Do not delete a reusable product/template needed for future dates.

22.5 Statuses and seller actions

Status

Meaning

Seller action

DRAFT

Seller-only; not visible to buyers.

Edit, Preview, Publish.

LIVE

Published and currently available or accepting eligible pre-orders.

Edit, Preview, Pause; Mark Sold Out where applicable.

PAUSED

Temporarily hidden from orderable buyer discovery.

Edit, Resume/Publish.

SOLD OUT

Finite stock exhausted or seller marks unavailable due to stock.

Replenish stock and explicitly mark available, or Pause.

ORDERS CLOSED / EXPIRED

Order window/date passed; no new orders.

Configure a future date/occurrence if supported; never reopen a past occurrence accidentally.

REMOVED / ARCHIVED

No longer actively offered.

Preserve records referenced by orders/history; avoid hard deletion.

Use the existing My Offerings filters (All | Kitchen | Homemade) and show a clear status badge. Do not add a second offering-management system or a cluttered collection of extra tabs.

22.6 Edit, pause, resume and order integrity

Editing a published product must not create a duplicate listing or silently change its public URL/identity.

Changes to description, image, category, unit and future availability must persist and appear on the buyer storefront after refresh.

Price changes apply to new orders only. Existing orders retain a snapshot of the agreed product name, unit, quantity and price at order time.

Edits must not silently invalidate existing orders. Date-specific changes must not rewrite existing orders' dates/fulfilment terms without an explicit, audited correction process.

Pause prevents new orders and removes the listing from orderable discovery; it does not cancel existing orders.

Resume is allowed only when seller/storefront status, required fields, availability window and stock are valid.

Replenishing finite stock must use an explicit seller action and persist to the backend.

Product records and seller order history remain available even when a listing is hidden from buyers.

22.7 Buyer discovery and shareability

After Publish, the product appears in Homemade Products discovery and the correct Homemade storefront, subject to service-area/society eligibility.

Never mix Homemade products into the Kitchen storefront for a mixed seller.

Search by product name and seller/store name must not present Draft, Paused, past-date or otherwise unavailable products as orderable.

Show Order Now only when ordering is permitted. Show preparation time or Order By/Ready By details for Made on Request and Specific Date products.

Provide Copy/Share Storefront Link using the existing storefront route. Direct links must not bypass availability validation.

Pause, sold-out and expired states must be reflected consistently in storefronts, search, counts and checkout.

22.8 Persistence and technical safeguards

Persist draft/published status, availability type, quantity mode, stock, dates/times, images and seller/storefront association in the database.

Store image files in configured persistent/object storage and store image references in the database; do not rely on an ephemeral application filesystem.

Enforce Save Draft, Publish, Pause, Resume, stock changes and edit permissions on the backend.

Publish must be idempotent; repeated clicks or retries must not create duplicate products.

Refresh/invalidate buyer caches after publish, pause, stock changes, edits and expiry.

Use Asia/Kolkata unless the existing application explicitly configures another business timezone.

Never hard-delete products referenced by orders, enquiries, audit records or seller history.

22.9 Publishing acceptance tests

Pending, rejected or suspended sellers cannot publish buyer-visible Homemade products.

An approved seller can save a draft; buyers and buyer search cannot see it.

Preview does not publish the product.

Missing required fields block Publish with clear inline errors.

A valid Always Available product becomes LIVE and remains available until paused, sold out or disabled.

A valid Made on Request product displays its minimum preparation time; invalid too-early dates are rejected where date selection exists.

A valid Specific Date product appears as a dated pre-order with correct Order By and Ready/Delivery By information.

After Order By passes, new orders are rejected and the product is excluded from orderable counts.

Pause hides a product without deleting it or cancelling existing orders.

Resume works only when seller status, required fields, availability and stock permit it.

Finite stock cannot be oversold; No Limit does not use finite-stock decrement logic.

Editing price/details does not change the snapshot saved on existing orders.

Publish status and product data survive refresh, re-login and application restart.

Buyer discovery, search, storefronts, counts and checkout agree on availability.

Seller can copy/share the correct Homemade storefront URL and open it while logged out.

Existing Kitchen offering creation and publishing still work.

22.10 Developer implementation instructions

Inspect the existing specification and code to determine whether Draft, Preview, Publish and status transitions already exist.

Reuse Add Offering, My Offerings, storefront, inventory, seller approval and order components wherever possible.

Do not create a second offering system or duplicate Kitchen logic unnecessarily.

Report conflicts between existing implementation and this addendum before changing schema or API contracts.

Make the smallest safe changes, run tests, and report files changed, migrations, actual test results and unresolved assumptions. Never claim tests passed unless they were executed.

ADDITIONAL REQUIRED SPECIFICATION

Seller Business Type Selection & Buyer-Side Publishing

Clarification to the Homemade Products V1 publishing flow

23. Seller business type selection — required

Use a hybrid approach: ask the seller what they intend to sell during registration, then use that saved selection to simplify the Add Offering experience. The selection enables business categories for the seller account; it does not decide the category of every individual offering.

23.1 Registration question

Ask: “What would you like to sell on SocioMart?”

Kitchen / Food — breakfast, lunch, dinner and snacks.

Homemade Products — cakes, ghee, achar, papad, sweets and other homemade products.

Both — Kitchen / Food and Homemade Products.

Save the selected business type(s) as structured values linked to the seller account/storefronts, not as free-text labels. The admin review screen must show the seller's selected type(s) before approval.

23.2 Add Offering behaviour

If only Kitchen / Food is enabled, + Add Offering opens the existing Kitchen Offering form directly.

If only Homemade Products is enabled, + Add Offering opens the Homemade Product form directly.

If Both are enabled, + Add Offering first asks “What are you listing?” with two options: Kitchen Offering and Homemade Product.

After the seller selects a type, open the matching form and apply that type's fields, validation, availability rules and publishing lifecycle.

Do not make the seller choose a business type on every login or repeat the choice unnecessarily when only one type is enabled.

Use one seller account, one seller dashboard and the existing shared My Offerings section with All | Kitchen | Homemade filters. Do not build separate seller accounts or unrelated offering-management systems.

23.3 Change business type later

Provide a clear Manage Store / Business Types setting so the seller can request a change after registration. Do not permanently lock a seller to the original choice.

A seller may request to enable an additional category later, for example changing Kitchen-only to Both.

For safety and consistency with the existing admin approval model, a newly added business category/storefront must not become public until any required admin review is approved. Keep the existing approved category active while the new category is under review.

A seller may request to stop using a category. Hide or deactivate that category's storefront from new buyer discovery after confirmation, but preserve existing orders, enquiries and history.

Do not automatically delete products or cancel existing orders when a category is disabled. Show the seller a warning if open orders or enquiries remain.

If the existing implementation supports immediate category changes under an approved policy, reuse that policy; do not introduce a second conflicting approval workflow. The final behaviour must be consistent and documented.

23.4 Separate buyer storefronts and correct publishing destination

The offering type determines where the product is published. A seller's account may support both categories, but Kitchen and Homemade Products remain separate buyer experiences.

Kitchen Offering ? publish only to Food & Kitchens discovery, Kitchen storefront, meal/category results and relevant Kitchen listings.

Homemade Product ? publish only to Homemade Products discovery, the seller's Homemade storefront, product category/search results and relevant Homemade listings.

A mixed seller must have separate Kitchen and Homemade buyer storefronts. Never display Poha/Thali in the Homemade storefront or Ghee/Cake in the Kitchen storefront unless the product is genuinely created as the corresponding offering type.

Each offering must persist its offering type and storefront association in the database. Buyer routes and queries must filter by these fields, not by matching product names or guessing from categories.

After Publish succeeds, refresh the correct buyer discovery and storefront views. Do not show the offering in the wrong section or require the seller to create it twice.

The seller can share the appropriate public storefront URL. For mixed sellers, provide distinct Kitchen and Homemade storefront links.

23.5 Admin approval and permissions

The seller application and admin review must show whether Kitchen, Homemade Products or Both were requested.

Admin approval must approve the selected category/storefront capability, not silently enable categories the seller did not request.

If a seller later requests an additional category, show that request to an authorised admin and record the decision and timestamp.

A pending or rejected category must not expose a public storefront or allow buyer-visible publishing for that category.

The seller must not be able to bypass category permissions by directly calling an API or changing frontend state. Enforce category and storefront permissions on the backend.

23.6 Acceptance tests

During registration, seller can select Kitchen / Food, Homemade Products or Both.

The selected business type(s) are visible to admin and persist after logout and application restart.

Kitchen-only seller sees one Add Offering path and creates a Kitchen offering without an unnecessary type-selection screen.

Homemade-only seller sees one Add Offering path and creates a Homemade Product without an unnecessary type-selection screen.

A Both seller sees the two-option selection and can create each type from the same dashboard.

Publishing a Kitchen offering makes it appear in Food & Kitchens only.

Publishing a Homemade Product makes it appear in Homemade Products only.

A mixed seller has separate buyer storefronts and the offerings do not leak into the wrong storefront, discovery page or search results.

Seller can request a business type change after registration; approval/visibility follows the defined admin policy.

Disabling a category does not delete products, orders, enquiries or seller history.

Backend blocks publishing when the seller lacks permission for the selected offering type.

Existing Kitchen offering creation, publication and buyer ordering continue to work.

23.7 Developer implementation instructions

Inspect the existing seller registration model, seller approval flow, storefront model, Add Offering component and buyer discovery queries before coding.

Reuse existing components and schema where possible; do not duplicate seller accounts, offering tables or buyer flows unnecessarily.

Identify and report any schema migration required to persist enabled business types and storefront associations.

Implement the registration choice, Add Offering routing, category-specific publishing destinations and later-change flow as one coherent feature.

Run tests for both seller categories and mixed sellers. Report actual test results and remaining assumptions; do not claim tests passed unless executed.
