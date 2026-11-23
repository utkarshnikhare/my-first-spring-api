# SocioMart — Developer Onboarding & Local Setup Guide

This guide provides step-by-step instructions for setting up, building, running, and testing SocioMart in a local development environment.

---

## 1. Prerequisites

Ensure the following tools are installed on your workstation:
- **Java Development Kit (JDK) 21**: Required for compiling and running the backend (Temurin JDK 21 recommended).
- **Node.js (v20 or v22 LTS)**: Required for running Playwright end-to-end tests and static asset checks.
- **Git**: For version control.

*Note: Maven is bundled via the Maven Wrapper (`mvnw` / `mvnw.cmd`), so a standalone Maven installation is optional.*

---

## 2. Repository Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/utkarshnikhare/my-first-spring-api.git
   cd my-first-spring-api
   ```

2. **Environment Configuration**:
   Create a local `.env` or set environment variables if needed. By default, running under the `local` profile requires **zero external configuration** and runs against an embedded in-memory H2 database.
   ```bash
   cp .env.example .env
   ```

---

## 3. Running the Application Locally

### Running via Maven Wrapper
From the `my-first-spring-api` directory:

- **Windows (PowerShell / Command Prompt)**:
  ```powershell
  cd my-first-spring-api
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
  ```

- **Linux / macOS**:
  ```bash
  cd my-first-spring-api
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
  ```

Once started:
- **Buyer Application**: Open [http://localhost:8081/](http://localhost:8081/)
- **Seller Studio**: Open [http://localhost:8081/seller.html](http://localhost:8081/seller.html)
- **Admin Console**: Open [http://localhost:8081/admin.html](http://localhost:8081/admin.html)
- **H2 Database Console**: Open [http://localhost:8081/h2-console](http://localhost:8081/h2-console) (JDBC URL: `jdbc:h2:mem:sociomart;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`)
- **API Documentation (Swagger UI)**: Open [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

---

## 4. Running Tests

### Running Backend Tests (JUnit 5 & Spring Boot)
```bash
cd my-first-spring-api
.\mvnw.cmd test
```
*Expected result: 940 tests run, 923 passed, 17 skipped (PostgreSQL Docker container tests).*

### Running Playwright End-to-End Tests
From the `my-first-spring-api/e2e` directory:
```bash
cd my-first-spring-api/e2e
npm install
npx playwright install --with-deps chromium
npx playwright test master_portal_interactive_audit.spec.cjs deep_mobile_and_theme_audit.spec.cjs --project=desktop
```

---

## 5. Development Guidelines

1. **Frontend Assets**:
   - All frontend files live in `my-first-spring-api/src/main/resources/static/`.
   - Modifying HTML, CSS, or JS files does not require restarting Spring Boot if static resource caching is disabled in local mode.
2. **Database Migrations**:
   - New database changes must be added as sequential Flyway migration files in `src/main/resources/db/migration/`.
   - Never modify or renumber existing migrations (`V1` through `V27`).
3. **Safety Checklist**:
   - Never commit API keys, tokens, or production passwords to Git.
   - Always verify that `.\mvnw.cmd test` passes before pushing feature branches.
