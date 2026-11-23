# SocioMart — Project Structure & Directory Layout

This document describes the directory structure of the SocioMart repository and the purpose of every important file and directory.

---

## Repository Tree

```
SocioMart/
├── .github/
│   └── workflows/
│       ├── ci.yml                     # Authoritative GitHub Actions CI pipeline (build, test, E2E)
│       └── database-backup.yml        # PostgreSQL backup and snapshot automation workflow
├── .vscode/                           # Recommended editor configurations
├── docs/                              # Architecture, operational, security, and API documentation
│   ├── ARCHITECTURE.md                # End-to-end system and portal architecture
│   ├── PROJECT_STRUCTURE.md           # This directory structure specification
│   ├── API_REFERENCE.md               # Complete REST API reference across all 182 endpoints
│   ├── DATABASE.md                    # Database schema, entities, migrations, and indexing
│   ├── DEVELOPMENT.md                 # Local developer onboarding, environment setup, and debugging
│   ├── DEPLOYMENT.md                  # Netlify (frontend) and Render (backend) deployment guide
│   ├── SECURITY.md                    # Authentication, RBAC, session isolation, and audit policies
│   ├── TESTING.md                     # Maven, JUnit, Testcontainers, and Playwright test architecture
│   ├── DEFECT_REGISTER.md             # Authoritative master defect and resolution registry
│   └── qa/                            # Quality assurance audits and cleanup reports
├── my-first-spring-api/               # Spring Boot Application Module
│   ├── e2e/                           # Playwright end-to-end integration and mobile test suites
│   │   ├── package.json               # E2E test runner dependencies (@playwright/test)
│   │   ├── playwright.config.cjs      # Playwright multi-viewport and webServer configuration
│   │   ├── support.cjs                # Authentication and workflow test helpers
│   │   └── *.spec.cjs                 # E2E specification test suites
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/my_first_spring_api/
│   │   │   │   ├── config/            # Spring configuration beans
│   │   │   │   ├── controller/        # REST API controllers (Buyer, Seller, Admin, Discovery)
│   │   │   │   ├── dto/               # Data Transfer Objects (Requests & Responses)
│   │   │   │   ├── exception/         # Domain exceptions and error models
│   │   │   │   ├── model/             # JPA entity domain models
│   │   │   │   ├── repository/        # Spring Data JPA repositories
│   │   │   │   ├── security/          # Security filters and authorization helpers
│   │   │   │   ├── service/           # Business logic and domain services
│   │   │   │   ├── transaction/       # Routing and isolation transaction management
│   │   │   │   ├── GlobalExceptionHandler.java  # Centralized REST error interceptor
│   │   │   │   ├── SecurityConfig.java          # Spring Security filter chain and RBAC
│   │   │   │   └── MyFirstSpringApiApplication.java # Spring Boot application entry point
│   │   │   └── resources/
│   │   │       ├── application.properties       # Core Spring Boot configuration
│   │   │       ├── application-prod.properties  # Production PostgreSQL & Hibernate configuration
│   │   │       ├── db/migration/                # Flyway versioned SQL migrations (V1–V27)
│   │   │       └── static/                      # Static PWA Frontend Assets
│   │   │           ├── index.html               # Buyer Single Page Application
│   │   │           ├── seller.html              # Seller Studio Single Page Application
│   │   │           ├── admin.html               # Admin Console Single Page Application
│   │   │           ├── manifest.webmanifest     # PWA Web Application Manifest
│   │   │           ├── sw.js                    # PWA Service Worker (precache & runtime cache)
│   │   │           ├── _headers                 # Netlify caching and HTTP security headers
│   │   │           ├── _redirects               # Netlify API proxy and SPA route rewrites
│   │   │           ├── css/                     # CSS stylesheets (styles.css, seller.css, admin.css)
│   │   │           ├── js/                      # JavaScript modules (app.js, buyer.js, seller.js, admin.js)
│   │   │           └── icons/                   # PWA application and maskable icons
│   │   └── test/                              # JUnit 5 & Testcontainers integration tests
│   │       ├── java/com/example/my_first_spring_api/  # Comprehensive unit and integration test suites
│   │       └── resources/                     # Test configuration profiles (application-test.properties)
│   ├── mvnw / mvnw.cmd                        # Cross-platform Maven wrapper scripts
│   └── pom.xml                                # Project Object Model and dependency definitions
├── scripts/                                   # Database backup and administrative shell utilities
├── Dockerfile                                 # Multi-stage production container build definition
├── netlify.toml                               # Authoritative Netlify build, redirect, and header configuration
├── render.yaml                                # Render service definition (Docker web service, manual deploy)
├── CHANGELOG.md                               # Project changelog and release history
└── README.md                                  # Repository landing page and quick-start guide
```
