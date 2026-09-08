# SocioMart — Client Demo Runbook

## Public URLs

| App | URL |
|---|---|
| **Buyer** | https://sociomart-demo.onrender.com/ |
| **Seller** | https://sociomart-demo.onrender.com/seller.html |
| **Admin** | https://sociomart-demo.onrender.com/admin.html |

## Demo Login Credentials

Use the in-app OTP flow (any 4-digit code works in demo mode):

| Role | Mobile |
|---|---|
| Buyer | `9999999999` (any 4-digit OTP) |
| Seller | Use "Demo Login" button on seller page |
| Admin | `9000000002` (any 4-digit OTP) |

## Recommended Demo Sequence

### 1. Buyer Home
Open Buyer URL. Show kitchen grid (15 kitchens), ratings, availability badges, search.

### 2. Kitchen Detail
Tap any kitchen (e.g., Aarti Kitchen). Show:
- Kitchen info, Instagram link, UPI ID
- "Available Today" products with live inventory ("only 4 left")
- "Pre-order" section (Aarti Kitchen has a Monday Puran Poli pre-order)
- Service area message

### 3. Favourite Kitchens (Add + Remove)
1. Open All Kitchens (`/#/kitchens`).
2. Tap the heart on 2–3 kitchens. Verify hearts fill (❤️).
3. Open Favourite Kitchens. Verify all favourited kitchens appear.
4. Return to All Kitchens, tap a filled heart. Verify it unfavourites (🤍) and disappears from Favourite Kitchens.
5. Refresh the page. Verify the removed kitchen remains unfavourited.
6. Favourite 3 kitchens, attempt a 4th. Verify the message: "You can favourite up to 3 kitchens only."
7. Remove one favourite, favourite a different kitchen. Verify slot reuse.

### 4. Add to Cart & Place Order
- Add a product (e.g., Poha ×1)
- Show Order Summary with two payment options:
  - **Place Order (Paid)** — PAID → CONFIRMED immediately
  - **Place Order (Will Pay Later)** — WILL_PAY_LATER → ORDERED
- Select "Place Order (Paid)"
- Show CONFIRMED order

### 5. Buyer Orders
Show placed order with correct payment and status.

### 6. Seller Dashboard
Login as seller. Show:
- Real analytics (views today)
- Live inventory steppers
- Order queue

### 7. Seller Orders
Walk through order status transitions (PENDING → CONFIRMED → DELIVERED).

### 8. Admin Dashboard
Login as admin. Show:
- Dashboard totals (orders, paid/WPL breakdown, buyers, sellers)
- Buyers, Sellers, Kitchens, Offerings, Orders, Enquiries views
- Seller approval workflow

## Known Limitations

### Render Cold Start
Render free tier sleeps after ~15 minutes of inactivity. First request after sleep takes ~60–120 seconds to wake up. This is normal behavior for the free tier. Use an uptime pinger to keep warm.

### H2 In-Memory Reset
The app uses H2 in-memory database. **Restarting the Spring Boot application resets all data.** Demo data is automatically re-seeded on every boot. Custom orders, buyers, or data created during a demo are lost on restart. This is expected for a client demo.

### No Real Payment
No actual payment gateway is integrated. PAID orders are simulated via the "Place Order (Paid)" button. WILL_PAY_LATER creates orders in ORDERED status. No real money is processed.

### No Real OTP
OTP is simulated — any 4-digit code works during demo login.

## Quick Reference

- **Payment Statuses:** PENDING → CONFIRMED; PAID → CONFIRMED; WILL_PAY_LATER → ORDERED
- **Order Statuses:** PENDING → CONFIRMED → DELIVERED (or CANCELLED)
- **Favourites:** Kitchen-only, max 3
- **Inventory:** Server-authoritative; never goes negative