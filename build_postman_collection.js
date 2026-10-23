const fs = require('fs');
const path = require('path');

function createRequest(name, method, urlPath, options = {}) {
  const parts = urlPath.replace(/^\//, '').split('/');
  const pathSegments = [];
  const queryParams = [];
  const variables = [];

  // Parse path & path variables
  for (const part of parts) {
    if (part.startsWith('{') && part.endsWith('}')) {
      const varName = part.substring(1, part.length - 1);
      pathSegments.push(`:${varName}`);
      variables.push({
        key: varName,
        value: options.variables && options.variables[varName] ? String(options.variables[varName]) : "1",
        description: `ID or slug for ${varName}`
      });
    } else {
      pathSegments.push(part);
    }
  }

  // Parse query params
  if (options.queryParams) {
    for (const [k, v] of Object.entries(options.queryParams)) {
      queryParams.push({
        key: k,
        value: String(v),
        description: ""
      });
    }
  }

  const req = {
    name: name,
    request: {
      method: method,
      header: [
        {
          key: "Accept",
          value: "application/json",
          type: "text"
        }
      ],
      url: {
        raw: `{{baseUrl}}/${pathSegments.join('/')}${queryParams.length ? '?' + queryParams.map(q => `${q.key}=${encodeURIComponent(q.value)}`).join('&') : ''}`,
        host: ["{{baseUrl}}"],
        path: pathSegments,
        query: queryParams.length ? queryParams : undefined,
        variable: variables.length ? variables : undefined
      },
      description: options.description || ""
    },
    response: []
  };

  if (options.body) {
    req.request.header.push({
      key: "Content-Type",
      value: "application/json",
      type: "text"
    });
    req.request.body = {
      mode: "raw",
      raw: JSON.stringify(options.body, null, 2),
      options: {
        raw: {
          language: "json"
        }
      }
    };
  }

  return req;
}

const collection = {
  info: {
    _postman_id: "sociomart-all-controllers-collection-v1",
    name: "SocioMart API - Complete Controller Collection",
    description: "Complete Postman Import Collection covering all 16 @RestController controllers across SocioMart Spring Boot application.\nIncludes endpoints for Auth, Health, Marketplace, Discovery, Buyer Profile & Orders, Favourites, Enquiries, Notifications, Analytics, Seller Kitchen & Products, Seller App / Dashboard V3, Recurring Schedules, Admin Console, Password Recovery, and Super Admin.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  variable: [
    {
      key: "baseUrl",
      value: "http://localhost:8080",
      type: "string"
    }
  ],
  item: [
    {
      name: "1. Authentication (AuthController)",
      description: "Endpoints under /api/auth: registration, credential login, password reset/recovery, session info and logout.",
      item: [
        createRequest("Get Auth Config", "GET", "api/auth/config", {
          description: "Returns frontend flags: directAuthEnabled, demoRegistrationEnabled, adminLoginConfigured, superAdminLoginConfigured"
        }),
        createRequest("Get Registration Options", "GET", "api/auth/registration-options", {
          description: "Returns coverage options (active areas & societies) for buyer & seller registration"
        }),
        createRequest("Register Buyer", "POST", "api/auth/register/buyer", {
          description: "Registers a new Buyer account and establishes session.",
          body: {
            name: "Pooja Sharma",
            mobileNumber: "9876543210",
            password: "Password123!",
            flatHouseNumber: "Flat 402",
            building: "Tower B, Whispering Palms",
            areaId: 1,
            societyId: 1
          }
        }),
        createRequest("Register Seller", "POST", "api/auth/register/seller", {
          description: "Registers a seller and creates pending storefront awaiting admin approval.",
          body: {
            sellerName: "Anita Sharma",
            mobileNumber: "9823456789",
            password: "Password123!",
            whatsappNumber: "9823456789",
            alternateContact: "",
            kitchenName: "Annapurna Kitchen",
            kitchenSlug: "annapurna-kitchen",
            speciality: "North Indian Thali & Parathas",
            sellerCategory: "KITCHEN",
            shortDescription: "Fresh homemade meals delivered hot every lunch and dinner",
            instagramLink: "https://instagram.com/annapurna_kitchen",
            primaryAreaId: 1,
            primarySocietyId: 1,
            building: "Tower A, Flat 101",
            serviceAreaId: 1,
            serviceSocietyIds: [1, 2]
          }
        }),
        createRequest("Login (Credential Login)", "POST", "api/auth/login", {
          description: "Password login for Buyer, Seller, Admin, or Super Admin.",
          body: {
            mobileNumber: "9876543210",
            password: "Password123!"
          }
        }),
        createRequest("Forgot Password (Request Reset)", "POST", "api/auth/forgot-password", {
          description: "Initiates admin-mediated recovery by mobile or token recovery by email.",
          body: {
            mobileNumber: "9823456789",
            email: ""
          }
        }),
        createRequest("Change Password", "POST", "api/auth/change-password", {
          description: "Forced or voluntary password change for current session user.",
          body: {
            currentPassword: "Password123!",
            newPassword: "NewSecretPassword123!",
            confirmPassword: "NewSecretPassword123!"
          }
        }),
        createRequest("Reset Password with Token", "POST", "api/auth/reset-password", {
          description: "Reset password using token from reset flow.",
          body: {
            token: "sample-reset-token-here",
            newPassword: "NewSecretPassword123!"
          }
        }),
        createRequest("Get Current User (/me)", "GET", "api/auth/me", {
          description: "Returns currently authenticated user profile, role, and storefront info."
        }),
        createRequest("Become Seller (Guidance)", "POST", "api/auth/become-seller", {
          description: "Deprecated route; returns guidance 409 prompting full seller registration."
        }),
        createRequest("Logout", "POST", "api/auth/logout", {
          description: "Invalidates the current HTTP session."
        })
      ]
    },
    {
      name: "2. Health (HealthController)",
      description: "Service liveness endpoint.",
      item: [
        createRequest("Health Check", "GET", "api/health", {
          description: "Liveness probe returning {\"status\": \"UP\"}"
        })
      ]
    },
    {
      name: "3. Marketplace & Kitchens (MarketplaceController)",
      description: "Public browse and discovery of kitchens, offerings, and search.",
      item: [
        createRequest("Marketplace Home", "GET", "api/marketplace", {
          description: "Returns the marketplace home model tailored for buyer's society."
        }),
        createRequest("Search Marketplace", "GET", "api/search", {
          queryParams: { q: "paneer" },
          description: "Search kitchens and menu offerings."
        }),
        createRequest("Suggest Kitchen Slug", "GET", "api/kitchens/slug-suggestion", {
          queryParams: { name: "Annapurna Kitchen" },
          description: "Generates an available, deduplicated public URL slug."
        }),
        createRequest("Check Slug Availability", "GET", "api/kitchens/slug-check", {
          queryParams: { slug: "annapurna-kitchen" },
          description: "Verifies whether a slug is available or taken."
        }),
        createRequest("Get Kitchen by Slug", "GET", "api/kitchens/by-slug/{slug}", {
          variables: { slug: "annapurna-kitchen" },
          description: "Public kitchen storefront resolved by slug."
        }),
        createRequest("Get Kitchen by Name", "GET", "api/kitchens/{kitchenName}", {
          variables: { kitchenName: "Annapurna Kitchen" },
          description: "Storefront detail by kitchen name."
        }),
        createRequest("Get Offerings by Kitchen Name", "GET", "api/kitchens/{kitchenName}/products", {
          variables: { kitchenName: "Annapurna Kitchen" },
          description: "List of products for a kitchen name."
        }),
        createRequest("Get Kitchen Detail by ID", "GET", "api/kitchens/id/{id}", {
          variables: { id: "1" },
          description: "Public kitchen storefront by ID (with Today and Pre-order offerings)."
        }),
        createRequest("Get Product / Offering by ID", "GET", "api/products/{productId}", {
          variables: { productId: "1" },
          description: "Detailed offering information."
        }),
        createRequest("List All Active Kitchens", "GET", "api/kitchens", {
          description: "List all active kitchens visible to buyer."
        }),
        createRequest("Global Browse (All Menu Items)", "GET", "api/items", {
          description: "All available menu items across active kitchens."
        })
      ]
    },
    {
      name: "4. Discovery (DiscoveryController)",
      description: "Dynamic discovery tabs, categories, dish grouping, and price comparisons.",
      item: [
        createRequest("Dynamic Header Counts", "GET", "api/discovery/counts", {
          description: "Live counts: Live now, Tomorrow, Pre-order, and All."
        }),
        createRequest("Discovery Cards by Tab", "GET", "api/discovery/kitchens", {
          queryParams: { tab: "LIVE_NOW" },
          description: "Discovery cards for tab: LIVE_NOW, TOMORROW, PREORDER, ALL."
        }),
        createRequest("Category Tiles with Counts", "GET", "api/discovery/categories", {
          description: "Categories (Breakfast, Lunch, Dinner, Snacks, Special) with live counts."
        }),
        createRequest("Items Grouped by Dish Name", "GET", "api/discovery/items", {
          queryParams: { category: "LUNCH" },
          description: "Items grouped by dish name for a category."
        }),
        createRequest("Kitchens Having Category Items", "GET", "api/discovery/category-kitchens", {
          queryParams: { category: "LUNCH" },
          description: "Kitchens offering items in the selected category."
        }),
        createRequest("Search Dish Names", "GET", "api/discovery/search", {
          queryParams: { q: "biryani" },
          description: "Live search over dish item groups."
        }),
        createRequest("Compare Offers for Dish", "GET", "api/discovery/offers", {
          queryParams: { item: "Paneer Butter Masala" },
          description: "Comparison offers for one dish across multiple kitchens."
        }),
        createRequest("Homemade Product Stores", "GET", "api/discovery/homemade", {
          description: "List of homemade artisan product stores."
        })
      ]
    },
    {
      name: "5. Buyer Profile (BuyerProfileController)",
      description: "Buyer profile management, addresses, and community selection.",
      item: [
        createRequest("Get Buyer Profile", "GET", "api/buyer/profile", {
          description: "Returns currently authenticated buyer profile."
        }),
        createRequest("Get Selectable Societies", "GET", "api/buyer/profile/societies", {
          description: "List of selectable societies from directory."
        }),
        createRequest("Get Approved Areas with Societies", "GET", "api/buyer/profile/areas", {
          description: "Areas and their societies for dropdown selection."
        }),
        createRequest("Update Buyer Profile", "PUT", "api/buyer/profile", {
          description: "Updates buyer delivery details, flat number, and society.",
          body: {
            name: "Pooja Sharma",
            mobileNumber: "9876543210",
            flatHouseNumber: "Flat 402",
            building: "Tower B, Whispering Palms",
            societyName: "Whispering Palms",
            societyId: 1,
            areaId: 1
          }
        })
      ]
    },
    {
      name: "6. Buyer Orders (BuyerOrderController)",
      description: "Draft cart, order placement, order history, ratings, and reorders.",
      item: [
        createRequest("Create or Update Draft Order (Cart)", "POST", "api/buyer/orders/draft", {
          queryParams: { kitchenId: "1" },
          description: "Sets or updates draft items for a single kitchen.",
          body: [
            {
              productId: 1,
              quantity: 2
            }
          ]
        }),
        createRequest("Get Current Draft Order", "GET", "api/buyer/orders/draft", {
          description: "Returns current cart draft (204 if empty)."
        }),
        createRequest("Clear Draft Order", "DELETE", "api/buyer/orders/draft", {
          description: "Empties the buyer draft cart."
        }),
        createRequest("Place Order", "POST", "api/buyer/orders/place", {
          description: "Places order from current draft, reserves stock, validates delivery coverage.",
          body: {
            paymentStatus: "PENDING",
            buyerDetails: {
              name: "Pooja Sharma",
              mobileNumber: "9876543210",
              flatHouseNumber: "Flat 402",
              building: "Tower B, Whispering Palms",
              societyName: "Whispering Palms"
            },
            customInstructions: "Please make it less spicy."
          }
        }),
        createRequest("Get My Orders", "GET", "api/buyer/orders/my", {
          description: "Returns active and past orders for current buyer."
        }),
        createRequest("Get Order Details", "GET", "api/buyer/orders/{orderId}", {
          variables: { orderId: "1" },
          description: "Detailed order breakdown."
        }),
        createRequest("Rate Order", "POST", "api/buyer/orders/{orderId}/rate", {
          variables: { orderId: "1" },
          queryParams: { rating: "5" },
          description: "Submits 1-5 star rating for completed order."
        }),
        createRequest("Reorder", "POST", "api/buyer/orders/{orderId}/reorder", {
          variables: { orderId: "1" },
          description: "Loads past order items into active draft cart."
        }),
        createRequest("Cancel Order", "POST", "api/buyer/orders/{orderId}/cancel", {
          variables: { orderId: "1" },
          description: "Cancels order if permitted by seller workflow."
        }),
        createRequest("Buyer Update Payment Status", "PATCH", "api/buyer/orders/{orderId}/payment-status", {
          variables: { orderId: "1" },
          description: "Buyer marks order as paid.",
          body: {
            paymentStatus: "PAID"
          }
        })
      ]
    },
    {
      name: "7. Favourites (FavouriteController)",
      description: "Buyer favourite kitchen toggles and list.",
      item: [
        createRequest("Get My Favourites", "GET", "api/favourites", {
          description: "Returns list of buyer's favourited kitchens."
        }),
        createRequest("Toggle Kitchen Favourite", "POST", "api/favourites/kitchen/{kitchenId}/toggle", {
          variables: { kitchenId: "1" },
          description: "Toggles favourite status of kitchen for current buyer."
        })
      ]
    },
    {
      name: "8. Enquiries (EnquiryController)",
      description: "Custom pre-order enquiries between buyers and sellers.",
      item: [
        createRequest("Buyer: Get My Enquiries", "GET", "api/enquiries/my", {
          description: "List of enquiries submitted by logged in buyer."
        }),
        createRequest("Buyer: Submit Enquiry", "POST", "api/enquiries", {
          description: "Submits a party or custom bulk food enquiry to a kitchen.",
          body: {
            kitchenId: 1,
            message: "Need catering for 15 guests next Sunday evening.",
            preferredDate: "2026-11-01",
            quantity: "15 portions",
            referenceImageUrl: ""
          }
        }),
        createRequest("Seller: Get My Store Enquiries", "GET", "api/enquiries/seller/my", {
          description: "Enquiries received by current seller's kitchen."
        }),
        createRequest("Seller: Acknowledge Enquiry", "POST", "api/enquiries/{enquiryId}/acknowledge", {
          variables: { enquiryId: "1" },
          description: "Seller acknowledges customer enquiry."
        }),
        createRequest("Seller: Update Enquiry Status", "PATCH", "api/enquiries/{enquiryId}/status", {
          variables: { enquiryId: "1" },
          description: "Updates enquiry status (PENDING, ACCEPTED, REJECTED, COMPLETED).",
          body: {
            status: "ACCEPTED"
          }
        })
      ]
    },
    {
      name: "9. Notifications (NotificationController)",
      description: "In-app notifications for order status and reminders.",
      item: [
        createRequest("Get Unread Notifications", "GET", "api/notifications", {
          description: "Returns unread notifications for current user."
        }),
        createRequest("Mark Notification as Read", "PATCH", "api/notifications/{eventId}/read", {
          variables: { eventId: "1" },
          description: "Marks a notification event as read."
        })
      ]
    },
    {
      name: "10. Analytics (AnalyticsController)",
      description: "Frontend interaction reporting (intent vs completion).",
      item: [
        createRequest("Record Interaction Event", "POST", "api/analytics/event", {
          description: "Records whitelisted event: ORDER_NOW_CLICK, ENQUIRY_CLICK, PRODUCT_VIEW, HOMEMADE_STOREFRONT_VIEW.",
          body: {
            type: "ORDER_NOW_CLICK",
            kitchenId: "1",
            detail: "Home Screen Banner Click"
          }
        })
      ]
    },
    {
      name: "11. Seller Storefront & Products (SellerController)",
      description: "Seller portal: kitchen configuration, menu CRUD, and order management.",
      item: [
        createRequest("Get My Kitchen", "GET", "api/seller/kitchen", {
          description: "Returns seller's own kitchen profile."
        }),
        createRequest("Get Known Societies", "GET", "api/seller/societies", {
          description: "List of existing societies for service area selection."
        }),
        createRequest("Get Coverage Options", "GET", "api/seller/coverage-options", {
          description: "Available areas and societies with IDs for coverage picker."
        }),
        createRequest("Create Kitchen", "POST", "api/seller/kitchen", {
          description: "Creates storefront for seller.",
          body: {
            name: "Annapurna Kitchen",
            kitchenSlug: "annapurna-kitchen",
            description: "Authentic homely food with love and hygiene.",
            speciality: "Gujarati & North Indian Thalis",
            sellerCategory: "KITCHEN",
            serviceAreas: "Whispering Palms, Raheja Heights",
            serviceAreaId: 1,
            serviceSocietyIds: [1, 2],
            deliveryCharge: 20.00,
            freeDeliveryThreshold: 300.00
          }
        }),
        createRequest("Update Kitchen", "PUT", "api/seller/kitchen/{kitchenId}", {
          variables: { kitchenId: "1" },
          description: "Updates kitchen details and service area coverage.",
          body: {
            name: "Annapurna Kitchen",
            description: "Authentic homemade meals made fresh every day.",
            speciality: "Gujarati & North Indian Thalis",
            sellerCategory: "KITCHEN",
            serviceAreas: "Whispering Palms, Raheja Heights",
            serviceAreaId: 1,
            serviceSocietyIds: [1, 2],
            deliveryCharge: 25.00,
            freeDeliveryThreshold: 350.00
          }
        }),
        createRequest("Pause Kitchen", "POST", "api/seller/kitchen/{kitchenId}/pause", {
          variables: { kitchenId: "1" },
          description: "Temporarily pause taking new orders for the kitchen."
        }),
        createRequest("Resume Kitchen", "POST", "api/seller/kitchen/{kitchenId}/resume", {
          variables: { kitchenId: "1" },
          description: "Resume taking orders for the kitchen."
        }),
        createRequest("List My Products", "GET", "api/seller/products", {
          description: "List all offerings created by current seller."
        }),
        createRequest("Get Offering for Edit Screen", "GET", "api/seller/products/{productId}", {
          variables: { productId: "1" },
          description: "Authoritative state of offering and whether orders freeze protected fields."
        }),
        createRequest("Create Product / Offering", "POST", "api/seller/products", {
          queryParams: { kitchenId: "1" },
          description: "Creates an offering with window timing, date, and inventory.",
          body: {
            name: "Special Paneer Thali",
            description: "Paneer butter masala, 3 phulkas, dal tadka, jeera rice, salad & sweet.",
            price: 180.00,
            priceUnit: "thali",
            imageUrl: "https://images.unsplash.com/photo-1546833999-b9f581a1996d",
            availableToday: true,
            availableDate: "2026-10-18",
            orderWindowStart: "08:00",
            orderWindowEnd: "11:30",
            readyByTime: "13:00",
            maxQuantity: 25,
            isPreorder: false,
            categories: ["LUNCH", "SPECIAL"]
          }
        }),
        createRequest("Update Product / Offering", "PUT", "api/seller/products/{productId}", {
          variables: { productId: "1" },
          description: "Updates an existing offering.",
          body: {
            name: "Special Paneer Thali (Deluxe)",
            description: "Paneer butter masala, 4 phulkas, dal tadka, jeera rice, salad & gulab jamun.",
            price: 200.00,
            priceUnit: "thali",
            orderWindowEnd: "12:00",
            readyByTime: "13:30",
            maxQuantity: 30,
            categories: ["LUNCH", "SPECIAL"]
          }
        }),
        createRequest("Delete Product", "DELETE", "api/seller/products/{productId}", {
          variables: { productId: "1" },
          description: "Deletes an offering (allowed when no active orders exist)."
        }),
        createRequest("List Seller Orders", "GET", "api/seller/orders", {
          description: "All orders placed with the seller's kitchen."
        }),
        createRequest("Get Order Detail for Seller", "GET", "api/seller/orders/{orderId}", {
          variables: { orderId: "1" },
          description: "Detailed view of an order with customer contact details."
        }),
        createRequest("Update Order Status", "PATCH", "api/seller/orders/{orderId}/status", {
          variables: { orderId: "1" },
          description: "Updates status: ACCEPTED, PREPARING, READY_FOR_PICKUP, DELIVERED, CANCELLED.",
          body: {
            orderStatus: "ACCEPTED"
          }
        }),
        createRequest("Mark Order as Paid", "PATCH", "api/seller/orders/{orderId}/payment-status", {
          variables: { orderId: "1" },
          description: "Seller confirms cash/UPI receipt and marks order PAID."
        }),
        createRequest("Acknowledge Order", "POST", "api/seller/orders/{orderId}/acknowledge", {
          variables: { orderId: "1" },
          description: "Seller acknowledges receipt of order notification."
        })
      ]
    },
    {
      name: "12. Seller App & Dashboard V3 (SellerAppController)",
      description: "Seller Dashboard V3: live inventory, templates, quick posts, bulk republish, order delivery tracking, and earnings.",
      item: [
        createRequest("Seller Dashboard Overview", "GET", "api/seller-app/dashboard", {
          description: "Live stats, active offerings, recurring summaries for Seller Dashboard."
        }),
        createRequest("Adjust Inventory Delta", "PATCH", "api/seller-app/products/{productId}/inventory", {
          variables: { productId: "1" },
          description: "Increments or decrements remaining inventory (e.g. delta: +5 or -2).",
          body: {
            delta: 5
          }
        }),
        createRequest("Mark Offering Sold Out", "POST", "api/seller-app/products/{productId}/sold-out", {
          variables: { productId: "1" },
          description: "Immediately sets remaining inventory to 0."
        }),
        createRequest("Pause Orders for Product", "POST", "api/seller-app/products/{productId}/pause", {
          variables: { productId: "1" },
          description: "Pauses ordering on an offering without changing stock."
        }),
        createRequest("Resume Orders for Product", "POST", "api/seller-app/products/{productId}/resume", {
          variables: { productId: "1" },
          description: "Resumes ordering on a paused offering."
        }),
        createRequest("Get Saved Offering Templates", "GET", "api/seller-app/templates", {
          description: "List up to 3 saved recurring menu templates."
        }),
        createRequest("Add Offering Template", "POST", "api/seller-app/templates", {
          description: "Save a template for quick 1-click publishing.",
          body: {
            name: "Sunday Rajma Chawal Special",
            description: "Delicious slow cooked Punjabi Rajma with aromatic Jeera Rice",
            price: 150.00,
            priceUnit: "plate",
            orderWindowStart: "08:00",
            orderWindowEnd: "12:00",
            readyByTime: "13:30",
            maxQuantity: 20,
            categories: ["LUNCH", "SPECIAL"]
          }
        }),
        createRequest("Delete Template", "DELETE", "api/seller-app/templates/{templateId}", {
          variables: { templateId: "1" },
          description: "Deletes a saved offering template."
        }),
        createRequest("Publish from Template", "POST", "api/seller-app/templates/{templateId}/publish", {
          variables: { templateId: "1" },
          description: "Publishes a new live offering from a template for target date.",
          body: {
            availableDate: "today"
          }
        }),
        createRequest("Create Quick Post", "POST", "api/seller-app/quick-posts", {
          description: "Publishes a today-only quick broadcast offering.",
          body: {
            message: "Fresh Gajar Ka Halwa available today 500g boxes at Rs 200. Only 8 boxes ready!",
            imageData: "",
            requestId: "qp-req-101",
            postedDate: "2026-10-18"
          }
        }),
        createRequest("List Quick Posts", "GET", "api/seller-app/quick-posts", {
          description: "List recent quick posts."
        }),
        createRequest("Parse Free-Text / WhatsApp Message", "POST", "api/seller-app/parse-message", {
          description: "AI-style parser extracting dish name, price, quantity, and timings from free-text.",
          body: {
            message: "Making fresh Methi Thepla 10 pcs pack Rs 120 ready by 11am today max 15 packs"
          }
        }),
        createRequest("Get Offerings History", "GET", "api/seller-app/history", {
          description: "Recent offerings available for 1-click republishing."
        }),
        createRequest("Batch Republish Offerings", "POST", "api/seller-app/batch-republish", {
          description: "Republishes multiple past offerings for a given date.",
          body: {
            productIds: [1, 2],
            availableDate: "tomorrow"
          }
        }),
        createRequest("Orders Summary Buckets", "GET", "api/seller-app/orders/summary", {
          queryParams: { date: "today" },
          description: "Aggregated order summary cards by status and society."
        }),
        createRequest("Offering Orders Drill-down", "GET", "api/seller-app/orders/product/{productId}", {
          variables: { productId: "1" },
          queryParams: {
            date: "today",
            society: "Whispering Palms",
            status: "ACCEPTED",
            delivery: "not_delivered"
          },
          description: "Detailed list of orders for one offering with 3 independent filter axes."
        }),
        createRequest("Mark Order Delivery Status", "PATCH", "api/seller-app/orders/{orderId}/delivery-status", {
          variables: { orderId: "1" },
          description: "Toggles or updates delivery status checkbox on an order.",
          body: {
            deliveryStatus: "DELIVERED"
          }
        }),
        createRequest("Bulk Mark All Delivered", "POST", "api/seller-app/orders/product/{productId}/mark-all-delivered", {
          variables: { productId: "1" },
          queryParams: { date: "today" },
          description: "Bulk marks all active orders for an offering on that date as delivered."
        }),
        createRequest("Seller Earnings Summary", "GET", "api/seller-app/earnings", {
          description: "Detailed financial breakdown: today, this week, total revenue and order count."
        })
      ]
    },
    {
      name: "13. Recurring Schedules (RecurringScheduleController)",
      description: "Manage recurring dish schedules, weekly repeat rules, and per-day overrides.",
      item: [
        createRequest("List My Recurring Schedules", "GET", "api/seller/schedules", {
          description: "Lists all recurring schedule cards for the seller."
        }),
        createRequest("Get Occurrences for Schedule", "GET", "api/seller/schedules/{scheduleId}/occurrences", {
          variables: { scheduleId: "1" },
          description: "Resolved calendar day rows for a schedule."
        }),
        createRequest("Get Schedule Card", "GET", "api/seller/schedules/{scheduleId}", {
          variables: { scheduleId: "1" },
          description: "Single schedule card for Manage Schedule modal."
        }),
        createRequest("Create Recurring Schedule for Product", "POST", "api/seller/schedules/{productId}", {
          variables: { productId: "1" },
          description: "Attaches a recurring repeating schedule to an offering.",
          body: {
            pattern: "WEEKLY",
            daysOfWeek: ["MON", "WED", "FRI"],
            defaultQuantity: 20,
            orderCloseTime: "11:00",
            readyByTime: "13:00",
            startDate: "2026-10-19",
            endDate: "2026-11-30"
          }
        }),
        createRequest("End Recurring Schedule", "POST", "api/seller/schedules/{scheduleId}/end", {
          variables: { scheduleId: "1" },
          description: "Ends schedule while preserving past history."
        }),
        createRequest("Extend Recurring Schedule", "POST", "api/seller/schedules/{scheduleId}/extend", {
          variables: { scheduleId: "1" },
          description: "Extends an ongoing schedule forward in time."
        }),
        createRequest("Update Schedule Defaults", "PUT", "api/seller/schedules/{scheduleId}", {
          variables: { scheduleId: "1" },
          description: "Updates repeating rule and future un-overridden occurrences.",
          body: {
            daysOfWeek: ["MON", "TUE", "WED", "THU", "FRI"],
            defaultQuantity: 25,
            orderCloseTime: "11:30",
            readyByTime: "13:30"
          }
        }),
        createRequest("Get Resolved Occurrence", "GET", "api/seller/schedules/occurrences/{occurrenceId}", {
          variables: { occurrenceId: "1" },
          description: "Fetches one specific selling date row."
        }),
        createRequest("Per-Day Override for Occurrence", "PATCH", "api/seller/schedules/occurrences/{occurrenceId}", {
          variables: { occurrenceId: "1" },
          description: "Modifies quantity or timing for ONE single date only.",
          body: {
            quantity: 35,
            orderCloseTime: "12:00",
            readyByTime: "14:00",
            soldOut: false,
            ordersPaused: false,
            clearQuantity: false
          }
        }),
        createRequest("Occurrence Sold Out Toggle", "POST", "api/seller/schedules/occurrences/{occurrenceId}/sold-out", {
          variables: { occurrenceId: "1" },
          description: "Toggles sold-out state for this single date.",
          body: {
            soldOut: true
          }
        }),
        createRequest("Occurrence Pause Orders Toggle", "POST", "api/seller/schedules/occurrences/{occurrenceId}/pause", {
          variables: { occurrenceId: "1" },
          description: "Toggles close-orders state for this single date.",
          body: {
            paused: true
          }
        })
      ]
    },
    {
      name: "14. Admin Console (AdminController)",
      description: "Admin & Super Admin operations: approvals, storefront controls, buyers, orders monitoring, coverage master, exports, and analytics.",
      item: [
        createRequest("Admin Dashboard Counters", "GET", "api/admin/dashboard", {
          queryParams: { date: "today" },
          description: "Overview statistics for chosen date window."
        }),
        createRequest("List & Search Buyers", "GET", "api/admin/buyers", {
          queryParams: { search: "", areaId: "1", societyId: "1" },
          description: "List registered buyers filtered by name, phone, area, or society."
        }),
        createRequest("Buyer Detail", "GET", "api/admin/buyers/{id}", {
          variables: { id: "1" },
          description: "Full profile, order history, and notes for a buyer."
        }),
        createRequest("Block Buyer", "POST", "api/admin/buyers/{id}/block", {
          variables: { id: "1" },
          description: "Blocks a buyer account.",
          body: {
            reason: "Repeated fraudulent orders."
          }
        }),
        createRequest("Unblock Buyer", "POST", "api/admin/buyers/{id}/unblock", {
          variables: { id: "1" },
          description: "Unblocks a buyer account.",
          body: {
            note: "Identity verified over phone."
          }
        }),
        createRequest("Buyer Support Note", "POST", "api/admin/buyers/{id}/support-note", {
          variables: { id: "1" },
          description: "Saves internal admin support note on buyer.",
          body: {
            note: "Requested special delivery instructions via support hotline."
          }
        }),
        createRequest("Visibility Diagnostic", "GET", "api/admin/diagnostics/visibility", {
          queryParams: { buyerId: "1", kitchenId: "1" },
          description: "Explains why a buyer can or cannot see a kitchen based on rule engine."
        }),
        createRequest("System Health Check", "GET", "api/admin/system-health", {
          description: "Factual runtime status of database, jobs, and memory."
        }),
        createRequest("List Sellers by Status", "GET", "api/admin/sellers", {
          queryParams: { status: "APPROVED" },
          description: "Filter sellers by PENDING, APPROVED, REJECTED, SUSPENDED."
        }),
        createRequest("List Pending Sellers", "GET", "api/admin/sellers/pending", {
          description: "Pending seller registrations awaiting admin review."
        }),
        createRequest("Seller Detail", "GET", "api/admin/sellers/{id}/detail", {
          variables: { id: "1" },
          description: "Full seller profile, storefront, coverage, documents, and contact."
        }),
        createRequest("Approve Seller", "POST", "api/admin/sellers/{id}/approve", {
          variables: { id: "1" },
          description: "Approves seller and activates their kitchen."
        }),
        createRequest("Reject Seller", "POST", "api/admin/sellers/{id}/reject", {
          variables: { id: "1" },
          description: "Rejects seller application with reason.",
          body: {
            reason: "Incomplete kitchen hygiene information."
          }
        }),
        createRequest("Request Seller Changes", "POST", "api/admin/sellers/{id}/request-changes", {
          variables: { id: "1" },
          description: "Asks seller to update their submission.",
          body: {
            reason: "Please update your menu with realistic preparation times."
          }
        }),
        createRequest("Suspend Seller", "POST", "api/admin/sellers/{id}/suspend", {
          variables: { id: "1" },
          description: "Temporarily suspends seller.",
          body: {
            reason: "Temporary kitchen renovation."
          }
        }),
        createRequest("Block Seller", "POST", "api/admin/sellers/{id}/block", {
          variables: { id: "1" },
          description: "Account-level block on seller.",
          body: {
            reason: "Terms of service violation."
          }
        }),
        createRequest("Unblock Seller", "POST", "api/admin/sellers/{id}/unblock", {
          variables: { id: "1" },
          description: "Restores blocked seller account.",
          body: {
            note: "Issue resolved."
          }
        }),
        createRequest("Seller Support Note", "POST", "api/admin/sellers/{id}/support-note", {
          variables: { id: "1" },
          description: "Internal admin note on seller.",
          body: {
            note: "Assisted seller in setting up recurring schedules."
          }
        }),
        createRequest("List Storefronts", "GET", "api/admin/kitchens", {
          queryParams: { sellerType: "KITCHEN" },
          description: "List of all kitchen storefronts."
        }),
        createRequest("Pause Storefront", "POST", "api/admin/storefronts/{kitchenId}/pause", {
          variables: { kitchenId: "1" },
          description: "Admin pauses a kitchen storefront.",
          body: {
            note: "Paused on seller phone request."
          }
        }),
        createRequest("Resume Storefront", "POST", "api/admin/storefronts/{kitchenId}/resume", {
          variables: { kitchenId: "1" },
          description: "Admin resumes an inactive storefront."
        }),
        createRequest("Remove Storefront", "POST", "api/admin/storefronts/{kitchenId}/remove", {
          variables: { kitchenId: "1" },
          description: "Soft-removes a storefront.",
          body: {
            reason: "Seller discontinued business."
          }
        }),
        createRequest("Change Kitchen Slug", "POST", "api/admin/kitchens/{kitchenId}/slug", {
          variables: { kitchenId: "1" },
          description: "Updates kitchen public URL slug, keeping old slug as alias.",
          body: {
            slug: "annapurna-delights"
          }
        }),
        createRequest("Update Kitchen Service Areas", "PATCH", "api/admin/kitchens/{kitchenId}/service-areas", {
          variables: { kitchenId: "1" },
          description: "Updates coverage area and societies for a storefront.",
          body: {
            areaId: 1,
            societyIds: [1, 2]
          }
        }),
        createRequest("List Societies", "GET", "api/admin/societies", {
          description: "All assignable societies."
        }),
        createRequest("List Active Coverage Options", "GET", "api/admin/coverage-options", {
          description: "Active areas & societies for coverage picker."
        }),
        createRequest("Master Locations List", "GET", "api/admin/locations", {
          description: "Full Area -> Society tree including inactive records."
        }),
        createRequest("Create Area", "POST", "api/admin/areas", {
          description: "Creates a new delivery geographic area.",
          body: {
            name: "Powai"
          }
        }),
        createRequest("Update Area", "PATCH", "api/admin/areas/{areaId}", {
          variables: { areaId: "1" },
          description: "Renames or enables/disables an Area.",
          body: {
            name: "Kandivali East",
            active: true
          }
        }),
        createRequest("Create Society", "POST", "api/admin/societies", {
          description: "Adds a residential society to an Area.",
          body: {
            areaId: 1,
            name: "Hiranandani Gardens"
          }
        }),
        createRequest("Update Society", "PATCH", "api/admin/societies/{societyId}", {
          variables: { societyId: "1" },
          description: "Renames or enables/disables a Society.",
          body: {
            name: "Whispering Palms Phase 1",
            active: true
          }
        }),
        createRequest("List All Offerings", "GET", "api/admin/offerings", {
          description: "Master list of menu items and offerings across all kitchens."
        }),
        createRequest("Orders Monitoring List", "GET", "api/admin/orders", {
          queryParams: {
            date: "today",
            status: "",
            delivery: "",
            payment: ""
          },
          description: "Read-only orders monitoring with composite filters."
        }),
        createRequest("Order Detail", "GET", "api/admin/orders/{id}", {
          variables: { id: "1" },
          description: "Admin order detail view."
        }),
        createRequest("Export Orders CSV", "GET", "api/admin/exports/orders.csv", {
          queryParams: { date: "today" },
          description: "Filter-aware CSV download with formula injection protection."
        }),
        createRequest("Needs Attention Items", "GET", "api/admin/attention", {
          description: "Consolidated items needing attention (pending sellers, disputes, etc.)."
        }),
        createRequest("List Enquiries", "GET", "api/admin/enquiries", {
          description: "List of all customer enquiries in platform."
        }),
        createRequest("Marketplace Links", "GET", "api/admin/marketplace-links", {
          description: "Public landing page and promotion links."
        }),
        createRequest("View Audit Log", "GET", "api/admin/audit-log", {
          queryParams: { limit: "200" },
          description: "Append-only system audit log."
        }),
        createRequest("Audit Entries for Target", "GET", "api/admin/audit-log/{targetType}/{targetId}", {
          variables: { targetType: "SELLER", targetId: "1" },
          description: "Audit trail for specific entity."
        }),
        createRequest("Retention Policy Preview", "GET", "api/admin/retention", {
          description: "Preview of data retention window and candidate purge count."
        }),
        createRequest("Set Retention Window", "POST", "api/admin/retention", {
          description: "Sets data retention window in days.",
          body: {
            retentionDays: 90
          }
        }),
        createRequest("Arm/Disarm Retention Purge", "POST", "api/admin/retention/purge-enabled", {
          description: "Enables or disables destructive purge execution.",
          body: {
            enabled: true
          }
        }),
        createRequest("Execute Retention Purge", "POST", "api/admin/retention/purge", {
          description: "Runs guarded retention purge.",
          body: {
            confirmed: true,
            exported: true,
            reason: "Scheduled quarterly data purge approved by Super Admin."
          }
        }),
        createRequest("Seller Analytics", "GET", "api/admin/seller-analytics", {
          queryParams: { date: "today" },
          description: "Per-seller commercial performance and order volumes."
        }),
        createRequest("Recorded Order Value Summary", "GET", "api/admin/recorded-order-value", {
          queryParams: { date: "today" },
          description: "Gross merchandise value and order breakdowns."
        }),
        createRequest("Admin Analytics Summary", "GET", "api/admin/analytics", {
          description: "Platform summary analytics."
        }),
        createRequest("Traffic Summary", "GET", "api/admin/traffic", {
          queryParams: { period: "today" },
          description: "Visitor and conversion traffic overview."
        })
      ]
    },
    {
      name: "15. Password Recovery Admin (PasswordRecoveryAdminController)",
      description: "Admin & Super Admin mediated password recovery workflow for sellers and users.",
      item: [
        createRequest("List Password Reset Requests", "GET", "api/admin/password-recovery", {
          queryParams: { status: "PENDING" },
          description: "Lists password recovery queue (PENDING, APPROVED, REJECTED)."
        }),
        createRequest("Generate Temporary Password", "POST", "api/admin/password-recovery/{requestId}/generate-temp-password", {
          variables: { requestId: "1" },
          description: "Super Admin action: generates a one-time temporary password."
        }),
        createRequest("Reject Password Reset Request", "POST", "api/admin/password-recovery/{requestId}/reject", {
          variables: { requestId: "1" },
          description: "Super Admin rejects a reset request.",
          body: {
            reason: "Could not verify identity of caller."
          }
        })
      ]
    },
    {
      name: "16. Super Admin Controls (SuperAdminController)",
      description: "Platform governance: Admin accounts, feature flags, seller limits, and global settings.",
      item: [
        createRequest("List Admin Accounts", "GET", "api/superadmin/admins", {
          description: "Lists all platform administrator accounts."
        }),
        createRequest("Create Admin Account", "POST", "api/superadmin/admins", {
          description: "Provisions an Admin account.",
          body: {
            name: "Rajesh Kumar",
            mobileNumber: "9811122233"
          }
        }),
        createRequest("Demote Admin Account", "DELETE", "api/superadmin/admins/{id}", {
          variables: { id: "2" },
          description: "Demotes an Admin account back to Buyer (Super Admin cannot be demoted)."
        }),
        createRequest("List Feature Flags", "GET", "api/superadmin/features", {
          description: "All platform feature flags with pricing models and limits."
        }),
        createRequest("Configure Feature Flag", "PUT", "api/superadmin/features/{featureKey}", {
          variables: { featureKey: "RECURRING_SCHEDULES" },
          description: "Sets feature flag status, pricing, and limits.",
          body: {
            displayName: "Recurring Schedules",
            description: "Allows home chefs to set up weekly repeating menus",
            enabled: true,
            pricingType: "FREE",
            limitValue: 5
          }
        }),
        createRequest("Grant / Revoke Seller Feature", "PUT", "api/superadmin/sellers/{sellerId}/features/{featureKey}", {
          variables: { sellerId: "1", featureKey: "RECURRING_SCHEDULES" },
          description: "Grants or overrides feature limits for a specific seller.",
          body: {
            granted: true,
            limitOverride: 10
          }
        }),
        createRequest("Get Feature Grants for Seller", "GET", "api/superadmin/sellers/{sellerId}/features", {
          variables: { sellerId: "1" },
          description: "List all custom feature grants for a seller."
        }),
        createRequest("Get Platform Settings", "GET", "api/superadmin/settings", {
          description: "Key-value platform configuration dictionary."
        }),
        createRequest("Update Platform Setting", "PUT", "api/superadmin/settings/{key}", {
          variables: { key: "platform.maintenance.mode" },
          description: "Updates a global platform setting.",
          body: {
            value: "false"
          }
        }),
        createRequest("Platform Global Analytics", "GET", "api/superadmin/analytics", {
          description: "Highest level multi-area platform analytics."
        })
      ]
    }
  ]
};

const outputPath = path.join(__dirname, 'sociomart_postman_collection.json');
fs.writeFileSync(outputPath, JSON.stringify(collection, null, 2), 'utf-8');

let totalEndpoints = 0;
for (const folder of collection.item) {
  totalEndpoints += folder.item.length;
}

console.log(`Generated Postman Collection at: ${outputPath}`);
console.log(`Total Folders: ${collection.item.length}`);
console.log(`Total Endpoints: ${totalEndpoints}`);
