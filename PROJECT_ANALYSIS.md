# 📊 SocioMart — Comprehensive Project Analysis

> **Last Updated**: 2026-09-11  
> **Repository**: https://github.com/utkarshnikhare/my-first-spring-api  
> **Live Demo**: https://sociomart-demo.onrender.com

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Domain Model](#4-domain-model)
5. [API Endpoints](#5-api-endpoints)
6. [Security Architecture](#6-security-architecture)
7. [Database Configuration](#7-database-configuration)
8. [Testing Strategy](#8-testing-strategy)
9. [Frontend Architecture](#9-frontend-architecture)
10. [Deployment & DevOps](#10-deployment--devops)
11. [Key Design Patterns](#11-key-design-patterns)
12. [Business Logic Highlights](#12-business-logic-highlights)
13. [File Inventory](#13-file-inventory)
14. [Configuration Profiles](#14-configuration-profiles)

---

## 1. Project Overview

**SocioMart** is a hyper-local community marketplace connecting home kitchens with buyers in their neighbourhood. It enables home cooks to sell homemade food to buyers in their housing society or local area.

| Attribute | Value |
|-----------|-------|
| **Project Name** | my-first-spring-api (SocioMart) |
| **Version** | 0.0.1-SNAPSHOT |
| **Java Version** | 21 |
| **Spring Boot** | 4.1.1 |
| **Build Tool** | Maven |
| **Database** | H2 (in-memory/file) |
| **Deployment** | Docker on Render.com |
| **License** | Proprietary |

### What is Included
- Buyers discover kitchens, view offerings, place orders, and track them
- Sellers manage their kitchen profile, offerings, orders, history, and earnings
- Admins review and approve sellers
- Super Admins manage platform configuration

### What is NOT Included
- Services marketplace
- Real Estate / Rentals
- Products / Buy & Sell marketplace
- OTP authentication
- Social feed, chat, live delivery tracking, real payments, wallet, subscriptions

---

## 2. Technology Stack

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 4.1.1 | Application framework |
| Spring MVC | 6.x | Web layer |
| Spring Security | 6.x | Authentication & authorization |
| Spring Data JPA | 3.x | Data access layer |
| Hibernate | 6.x | ORM |
| H2 Database | 2.x | Embedded database |
| SpringDoc OpenAPI | 3.1.0 | API documentation (Swagger UI) |
| Jakarta Bean Validation | 3.x | Input validation |
| BCrypt | - | Password encoding |
| Maven | 3.9.6 | Build tool |

### Frontend
| Technology | Purpose |
|------------|---------|
| HTML5 | Structure |
| CSS3 | Styling (light/dark themes) |
| Vanilla JavaScript | SPA logic (no framework) |
| Hash-based Routing | Client-side navigation |

### DevOps
| Technology | Purpose |
|------------|---------|
| Docker | Containerization |
| GitHub Actions | CI/CD |
| Render.com | Cloud hosting |
| Puppeteer | UI testing (docs-tools) |

---

## 3. Project Structure

```
my-first-spring-api/
├── .github/
│   └── workflows/
│       └── ci.yml                          # CI build verification
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties        # Maven wrapper config
├── docs-tools/                             # Puppeteer UI testing tools
│   ├── package.json
│   ├── node_modules/
│   └── uiqa-shots/                         # UI screenshots
├── my-first-spring-api/                    # Main application module
│   ├── pom.xml                             # Maven configuration
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/my_first_spring_api/
│   │   │   │   ├── MyFirstSpringApiApplication.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── DataInitializer.java
│   │   │   │   ├── DemoDataSeeder.java
│   │   │   │   ├── controller/           # 10 REST controllers
│   │   │   │   ├── service/              # 16 service classes
│   │   │   │   ├── model/                # 14 entities + 9 enums
│   │   │   │   ├── repository/           # 14 repository interfaces
│   │   │   │   ├── dto/                  # ~25 DTO classes
│   │   │   │   ├── security/             # Custom security filter
│   │   │   │   └── exception/            # Custom exceptions
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       ├── application-dev.properties
│   │   │       ├── application-prod.properties
│   │   │       ├── application-demo.properties
│   │   │       └── static/               # Frontend HTML/CSS/JS
│   │   └── test/
│   │       └── java/                      # 8 unit test classes
│   └── target/                            # Build output
├── Dockerfile                              # Multi-stage Docker build
├── render.yaml                             # Render deployment config
├── README.md                               # Project documentation
└── .gitignore                              # Git ignore rules
```

---

## 4. Domain Model

### Entities

| Entity | Table | Description |
|--------|-------|-------------|
| `User` | `users` | Platform users with roles (BUYER, SELLER, ADMIN, SUPER_ADMIN) |
| `Kitchen` | `kitchens` | Home kitchen storefront with seller relationship |
| `Product` | `products` | Menu items/offerings with pricing, availability, categories |
| `Order` | `orders` | Buyer orders with status tracking |
| `OrderItem` | `order_items` | Individual items within an order |
| `Enquiry` | `enquiries` | Buyer questions to kitchens |
| `Favourite` | `favourites` | User's saved kitchens/products |
| `PlatformSetting` | `platform_settings` | Key-value configuration store |
| `FeatureFlag` | `feature_flags` | Platform feature configuration |
| `SellerFeatureGrant` | `seller_feature_grants` | Per-seller feature access |
| `SellerTemplate` | `seller_templates` | Saved offering templates (max 3/seller) |
| `AnalyticsEvent` | `analytics_events` | Usage tracking events |
| `NotificationEvent` | `notification_events` | Seller notifications |
| `LedgerEvent` | `ledger_events` | Financial transaction records |

### Enumerations

| Enum | Values | Purpose |
|------|--------|---------|
| `UserRole` | BUYER, SELLER, ADMIN, SUPER_ADMIN | User role hierarchy |
| `SellerApprovalStatus` | PENDING, APPROVED, REJECTED, SUSPENDED | Seller lifecycle |
| `OrderStatus` | DRAFT, ORDERED, CONFIRMED, READY, DELIVERED, COMPLETED, CANCELLED | Order lifecycle |
| `PaymentStatus` | PENDING, PAID, WILL_PAY_LATER | Payment tracking |
| `SellerType` | KITCHEN, HOMEMADE_PRODUCTS | Kitchen classification |
| `Category` | BREAKFAST, LUNCH, DINNER, SNACKS, SPECIAL | Food categories |
| `PreorderType` | FIXED, FLEXIBLE | Pre-order availability model |
| `EnquiryStatus` | NEW, CONTACTED, CLOSED | Enquiry lifecycle |
| `FeaturePricingType` | FREE, PAID | Feature pricing model |

### Entity Relationships

```
User (1) ──── (N) Kitchen
Kitchen (1) ──── (N) Product
Kitchen (1) ──── (N) Order
Order (1) ──── (N) OrderItem
Product (1) ──── (N) OrderItem
User (1) ──── (N) Enquiry
Kitchen (1) ──── (N) Enquiry
User (1) ──── (N) Favourite
User (1) ──── (N) SellerTemplate
User (1) ──── (N) NotificationEvent
User (1) ──── (N) LedgerEvent
```

---

## 5. API Endpoints

### AuthController (`/api/auth`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/demo-login` | Mobile number authentication |
| GET | `/me` | Current user info |
| POST | `/become-seller` | Convert to seller |
| POST | `/logout` | End session |

### BuyerOrderController (`/api/buyer/orders`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/draft` | Create/update draft order |
| GET | `/draft` | Get current draft |
| DELETE | `/draft` | Clear draft |
| POST | `/place` | Place order |
| GET | `/my` | My orders |
| GET | `/{id}` | Order details |
| POST | `/{id}/rate` | Rate order |
| POST | `/{id}/reorder` | Reorder |
| PATCH | `/{id}/payment-status` | Update payment |

### SellerController (`/api/seller`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/kitchen` | Get my kitchen |
| POST | `/kitchen` | Create kitchen |
| PUT | `/kitchen/{id}` | Update kitchen |
| GET | `/products` | Get my products |
| POST | `/products` | Create product |
| PUT | `/products/{id}` | Update product |
| DELETE | `/products/{id}` | Delete product |
| GET | `/orders` | Get my orders |
| PATCH | `/orders/{id}/status` | Update order status |
| PATCH | `/orders/{id}/payment-status` | Mark as paid |
| POST | `/orders/{id}/acknowledge` | Acknowledge order |

### SellerAppController (`/api/seller-app`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/demo-login` | Demo seller auto-login |
| GET | `/dashboard` | Seller dashboard |
| POST | `/products/quick` | Quick product creation |
| GET | `/orders/summary` | Order summary by date |
| GET | `/orders/product/{id}` | Order item detail |
| GET | `/earnings` | Earnings breakdown |
| POST | `/templates` | Create template |
| POST | `/templates/{id}/publish` | Publish from template |
| POST | `/parse-message` | Parse quick post message |
| GET | `/history` | Recent offerings |
| POST | `/batch-republish` | Batch republish |

### AdminController (`/api/admin`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/dashboard` | Admin dashboard |
| GET | `/buyers` | List buyers |
| GET | `/sellers` | List sellers (filter by status) |
| GET | `/kitchens` | List kitchens |
| PATCH | `/kitchens/{id}/service-areas` | Update service areas |
| GET | `/offerings` | List offerings |
| GET | `/orders` | List orders (filter/search) |
| GET | `/orders/{id}` | Order detail |
| GET | `/enquiries` | List enquiries |
| GET | `/sellers/pending` | Pending sellers |
| POST | `/sellers/{id}/approve` | Approve seller |
| POST | `/sellers/{id}/reject` | Reject seller |
| POST | `/sellers/{id}/suspend` | Suspend seller |
| GET | `/analytics` | Analytics summary |
| GET | `/traffic` | Traffic data |

### SuperAdminController (`/api/superadmin`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admins` | List admins |
| POST | `/admins` | Create admin |
| DELETE | `/admins/{id}` | Demote admin |
| GET | `/features` | List features |
| PUT | `/features/{key}` | Update feature |
| PUT | `/sellers/{id}/features/{key}` | Set seller feature grant |
| GET | `/sellers/{id}/features` | Get seller features |
| GET | `/settings` | Get platform settings |
| PUT | `/settings/{key}` | Update setting |
| GET | `/analytics` | Platform analytics |

### MarketplaceController (`/api/marketplace`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/marketplace` | Marketplace home |
| GET | `/api/search` | Search kitchens/products |
| GET | `/api/kitchens/{name}` | Kitchen by name |
| GET | `/api/kitchens/{name}/products` | Kitchen products |
| GET | `/api/kitchens/id/{id}` | Kitchen by ID |
| GET | `/api/products/{id}` | Product detail |
| GET | `/api/kitchens` | All active kitchens |
| GET | `/api/items` | All available items |

### DiscoveryController (`/api/discovery`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/counts` | Kitchen counts |
| GET | `/kitchens` | Discovery cards (tab filter) |
| GET | `/categories` | Category tiles |
| GET | `/items` | Items by category |
| GET | `/category-kitchens` | Kitchens by category |
| GET | `/search` | Search items |
| GET | `/offers` | Comparison offers |
| GET | `/homemade` | Homemade stores |

### FavouriteController (`/api/favourites`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Get my favourites |
| POST | `/kitchen/{id}/toggle` | Toggle kitchen favourite |

### EnquiryController (`/api/enquiries`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/my` | My enquiries |
| POST | `/` | Submit enquiry |
| GET | `/seller/my` | Seller enquiries |
| POST | `/{id}/acknowledge` | Acknowledge enquiry |
| PATCH | `/{id}/status` | Update enquiry status |

---

## 6. Security Architecture

### Authentication Flow
1. User calls `POST /api/auth/demo-login` with mobile number
2. `BuyerService` creates/finds user and sets `BUYER_USER` session attribute
3. Spring Security context is populated with user authorities
4. `UserSessionAuthorizationFilter` translates session to Spring Security authorities on each request

### Authorization Rules
| Endpoint Pattern | Required Role |
|------------------|---------------|
| `/api/superadmin/**` | SUPER_ADMIN |
| `/api/admin/**` | ADMIN or SUPER_ADMIN |
| `/api/seller/**` | SELLER |
| `/api/buyer/**` | Authenticated user |
| `/api/favourites/**` | Authenticated user |
| `/api/enquiries/**` | Authenticated user |
| All others | Public |

### Security Configuration
- **CSRF**: Disabled (stateless REST API)
- **Session**: Always created, 30-minute timeout
- **Session Fixation**: Migrate session on authentication
- **Frame Options**: Same origin (for H2 console)
- **HTTP Basic**: Disabled
- **Form Login**: Disabled
- **Logout**: Disabled (custom endpoint)

---

## 7. Database Configuration

### Profiles

| Profile | Database | DDL Auto | H2 Console | SQL Init |
|---------|----------|----------|------------|----------|
| `default` | In-memory H2 | update | Enabled | never |
| `dev` | File H2 (`./data/sociomartdb`) | update | Enabled | never |
| `demo` | In-memory H2 | update | Enabled | never |
| `prod` | File H2 (configurable to PostgreSQL) | validate | Disabled | never |

### Connection Details
```properties
# Default (in-memory)
spring.datasource.url=jdbc:h2:mem:sociomartdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE

# Dev/Prod (file-based)
spring.datasource.url=jdbc:h2:file:./data/sociomartdb;DB_CLOSE_ON_EXIT=FALSE
```

### Demo Data Seeding
- **Trigger**: `DataInitializer` runs on startup for `demo`, `dev`, `default` profiles
- **Idempotency**: Uses `platform_settings` flags to prevent duplicate seeding
- **Contents**: Sample kitchens, products, buyers, sellers, orders, enquiries, favourites

---

## 8. Testing Strategy

### Unit Tests (8 test classes, Mockito-based)

| Test Class | Coverage Area |
|------------|---------------|
| `OrderServiceValidationTest` | Zero/negative quantity, stock limits, kitchen switching |
| `OrderServicePaymentTest` | Payment status transitions |
| `OrderServiceNotificationTest` | Seller notification on order placement |
| `EnquiryServiceNotificationTest` | Enquiry notification and ledger recording |
| `FavouriteServiceLimitTest` | 3-kitchen favourite limit enforcement |
| `HomemadeProductsTest` | Homemade store discovery and service area filtering |
| `OrderPriceImmutabilityTest` | Historical price preservation |
| `SellerServiceCategoryTest` | Product category handling |

### Test Infrastructure
- **Framework**: JUnit 5 (Jupiter)
- **Mocking**: Mockito
- **Assertions**: AssertJ
- **Coverage**: Service layer business logic

---

## 9. Frontend Architecture

### Three Single-Page Applications

1. **Buyer** (`index.html`)
   - Kitchen discovery and search
   - Product browsing and ordering
   - Favourites management
   - Order tracking
   - Profile management

2. **Seller** (`seller.html`)
   - Dashboard with metrics
   - Kitchen profile management
   - Product/offering management
   - Order processing
   - Earnings view
   - History and templates

3. **Admin** (`admin.html`)
   - Platform dashboard
   - Seller approval workflow
   - User management
   - Analytics and traffic
   - Platform console

### JavaScript Architecture
| File | Purpose |
|------|---------|
| `config.js` | API endpoint configuration |
| `common.js` | Shared utilities (fetch, DOM, theme) |
| `buyer.js` | Buyer app logic |
| `seller.js` | Seller app logic |
| `admin.js` | Admin app logic |
| `app.js` | Buyer app initialization |

### Features
- **Hash-based routing**: `#/home`, `#/kitchens`, `#/orders`, etc.
- **Light/dark themes**: CSS variables with localStorage persistence
- **Responsive design**: Mobile-first with bottom navigation
- **Modal/toast system**: User feedback components
- **System theme detection**: `prefers-color-scheme` media query

---

## 10. Deployment & DevOps

### Docker Configuration
```dockerfile
# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Render.com Configuration (`render.yaml`)
```yaml
services:
  - type: web
    name: sociomart-demo
    runtime: docker
    dockerfilePath: ./Dockerfile
    plan: free
    healthCheckPath: /api/kitchens
    autoDeploy: true
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: demo
```

### CI/CD Pipeline (GitHub Actions)
```yaml
name: CI Build Verification
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: mvn -B -DskipTests clean compile
```

### Deployment Characteristics
- **Free tier**: Sleeps after 15 min inactivity
- **Cold start**: 60-90 seconds wake time
- **Health check**: `/api/kitchens`
- **Auto-deploy**: Enabled on push to main

---

## 11. Key Design Patterns

### 1. DTO Pattern
Separate data transfer objects for API responses to decouple internal models from external contracts.

### 2. Repository Pattern
Spring Data JPA repositories for data access abstraction.

### 3. Service Layer
Business logic encapsulated in service classes with `@Transactional` boundaries.

### 4. Global Exception Handler
`@RestControllerAdvice` with typed exceptions for consistent error responses.

### 5. Feature Flags
Configuration-driven feature access via `FeatureFlag` and `SellerFeatureGrant` entities.

### 6. Idempotent Seeding
Platform settings flags prevent duplicate demo data on restart.

### 7. Session-based Authentication
Custom `UserSessionAuthorizationFilter` bridges HTTP session to Spring Security.

### 8. Template Method
Seller templates for quick product creation with independent publishing.

### 9. Scheduled Tasks
`@Scheduled` annotation for reminder notifications (every 5 minutes).

### 10. Visibility Rules
`KitchenVisibility` utility class as single source of truth for kitchen visibility.

---

## 12. Business Logic Highlights

### Order Lifecycle
```
DRAFT → ORDERED → CONFIRMED → READY → DELIVERED → CANCELLED
                                              ↓
                                         COMPLETED
```

### Payment Flow
- **PENDING**: Order placed, payment not received
- **PAID**: Payment confirmed (transitions ORDERED → CONFIRMED)
- **WILL_PAY_LATER**: Pay later option (does not auto-confirm)

### Seller Approval Workflow
```
PENDING → APPROVED
   ↓
REJECTED / SUSPENDED
```

### Stock Management
- Products track `remainingQuantity` and `maxQuantity`
- Stock consumed on order placement
- Stock restored on order cancellation
- Sold out when `remainingQuantity <= 0`

### Pre-order System
- **FIXED**: Single availability date with cutoff time
- **FLEXIBLE**: Buyer picks date range + time slot

### Service Area Filtering
- Kitchens visible only to buyers in matching society
- Supports comma-separated service areas

### Favourite Limit
- Maximum 3 kitchen favourites per buyer
- Enforced in `FavouriteService.toggleKitchen()`

### Price Immutability
- Historical orders preserve original product prices
- New orders always use current product price

---

## 13. File Inventory

### Java Source Files (~85 files)

| Category | Count | Files |
|----------|-------|-------|
| Controllers | 10 | AdminController, AuthController, BuyerOrderController, BuyerProfileController, BuyerPublicController, DiscoveryController, EnquiryController, FavouriteController, MarketplaceController, SellerAppController, SellerController, SuperAdminController |
| Services | 16 | AdminService, AnalyticsService, BuyerService, DiscoveryService, EnquiryService, FavouriteService, FeatureService, KitchenService, KitchenVisibility, LedgerService, MarketplaceService, NotificationReminderJob, NotificationService, OrderService, SellerAppService, SellerService |
| Models | 14 | User, Kitchen, Product, Order, OrderItem, Enquiry, Favourite, PlatformSetting, FeatureFlag, SellerFeatureGrant, SellerTemplate, AnalyticsEvent, NotificationEvent, LedgerEvent |
| Enums | 9 | UserRole, SellerApprovalStatus, OrderStatus, PaymentStatus, SellerType, Category, PreorderType, EnquiryStatus, FeaturePricingType |
| Repositories | 14 | UserRepository, KitchenRepository, ProductRepository, OrderRepository, OrderItemRepository, EnquiryRepository, FavouriteRepository, PlatformSettingRepository, FeatureFlagRepository, SellerFeatureGrantRepository, SellerTemplateRepository, AnalyticsEventRepository, NotificationEventRepository, LedgerEventRepository |
| DTOs | ~25 | ApiErrorDto, AuthResponseDto, BuyerProfileDto, DiscoveryDtos, EnquiryDto, FavouriteDto, KitchenCreateDto, KitchenDetailDto, KitchenDto, KitchenUpdateDto, MarketplaceDto, OrderDto, OrderItemDetailDto, OrderItemDto, and more |
| Security | 1 | UserSessionAuthorizationFilter |
| Exceptions | 7 | BuyerNotAuthenticatedException, InvalidKitchenSelectionException, KitchenNotFoundException, OrderNotFoundException, ProductNotFoundException, SellerNotAuthorizedException, TemplateNotFoundException |
| Config | 3 | SecurityConfig, GlobalExceptionHandler, DataInitializer |

### Frontend Files

| Category | Count | Files |
|----------|-------|-------|
| HTML | 3 | index.html, seller.html, admin.html |
| CSS | 3 | styles.css, seller.css, admin.css |
| JavaScript | 6 | config.js, common.js, buyer.js, seller.js, admin.js, app.js |

### Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven build configuration |
| `Dockerfile` | Docker image build |
| `render.yaml` | Render deployment config |
| `application.properties` | Default configuration |
| `application-dev.properties` | Development profile |
| `application-prod.properties` | Production profile |
| `application-demo.properties` | Demo profile |
| `.github/workflows/ci.yml` | CI pipeline |

### Test Files (8 test classes)

| Test | Coverage |
|------|----------|
| OrderServiceValidationTest | Input validation |
| OrderServicePaymentTest | Payment transitions |
| OrderServiceNotificationTest | Order notifications |
| EnquiryServiceNotificationTest | Enquiry notifications |
| FavouriteServiceLimitTest | Favourite limits |
| HomemadeProductsTest | Homemade discovery |
| OrderPriceImmutabilityTest | Price preservation |
| SellerServiceCategoryTest | Category handling |

---

## 14. Configuration Profiles

### Default Profile
- In-memory H2 database
- H2 console enabled
- Swagger UI enabled
- INFO level logging

### Dev Profile
- File-based H2 database (`./data/sociomartdb`)
- H2 console enabled
- DEBUG level logging
- Demo data seeded

### Demo Profile
- In-memory H2 database (fresh on each boot)
- H2 console enabled
- DEBUG level logging
- Demo data seeded
- Used for Render deployment

### Prod Profile
- File-based H2 database (configurable to PostgreSQL)
- H2 console disabled
- WARN level logging
- No demo data seeding
- Session cookie: SameSite=Strict

---

## Appendix: Quick Reference

### Running Locally
```bash
# Development mode with demo data
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Demo mode (in-memory)
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Build Docker image
docker build -t sociomart .

# Run Docker container
docker run -p 8081:8081 sociomart
```

### API Documentation
- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI Docs: http://localhost:8081/v3/api-docs
- H2 Console: http://localhost:8081/h2-console

### Demo Credentials
| Role | Access |
|------|--------|
| Buyer | Open the app — no login required to browse |
| Seller | Seller dashboard pre-loads demo kitchen |
| Admin | Open `admin.html` — accessible without authentication in demo mode |

---

*This document was auto-generated on 2026-09-11 for the SocioMart project.*