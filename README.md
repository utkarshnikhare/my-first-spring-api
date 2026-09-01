# 🥘 SocioMart — Community Food Marketplace

A full-stack hyperlocal food-ordering web application. Home cooks ("sellers") run a digital kitchen inside
their housing society; neighbours ("buyers") discover dishes, add them to a cart, place orders, pay, track
them to delivery and rate them — the complete hyperlocal food-ordering loop in one product.

> 📄 **Full project report + step-by-step setup & troubleshooting guide:**
> [`SocioMart-Project-Report.pdf`](SocioMart-Project-Report.pdf) (also see [`docs/`](docs/))

## ✨ Features

- 🔐 **OTP login** — demo mode: the OTP is shown on screen, no SMS provider needed
- 🛒 **Buyer** — browse marketplace, search, category chips, cart, checkout, place order, mark paid, track, cancel (PENDING), reorder, rate ★
- 🏪 **Seller** — one-tap upgrade, create kitchen, manage offerings (dishes), live order lifecycle: confirm → ready → delivered
- 📱 **Mobile-first SPA** — vanilla HTML/CSS/JS with hash routing, zero build step, served by Spring Boot itself
- 📚 **Swagger UI** — every REST endpoint documented live
- 🗄 **H2 file database** — zero-install persistence, data survives restarts

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 4.1.1 (Web MVC · Security 7 · Validation · Data JPA/Hibernate) |
| Database | H2 in file mode (`jdbc:h2:file:./data/sociomartdb`) |
| API docs | springdoc-openapi 3.1.0 |
| Frontend | Vanilla HTML5 / CSS3 / ES6 SPA (no framework, no build) |
| Build | Maven wrapper (`mvnw`) — no Maven installation needed |

## 🚀 Quick Start

**Prerequisites (nothing else):**

1. **JDK 21** — [adoptium.net](https://adoptium.net) (tick *Set JAVA_HOME*). Verify: `java -version` → `21.x`
2. **Git** — [git-scm.com](https://git-scm.com)
3. **VS Code** + extensions: *Extension Pack for Java* and *Spring Boot Extension Pack*

```bash
git clone https://github.com/utkarshnikhare/my-first-spring-api.git
cd my-first-spring-api/my-first-spring-api

# Windows (PowerShell)
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The first run downloads dependencies (2–5 min). Wait for `Started MyFirstSpringApiApplication`.

## 🌐 Open the app

| URL | What it is |
|---|---|
| `http://localhost:8081` | The app — boots on `#/home` with an **empty** marketplace (expected) |
| `http://localhost:8081/swagger-ui.html` | Live REST API documentation |
| `http://localhost:8081/h2-console` | DB console — JDBC URL `jdbc:h2:file:./data/sociomartdb`, user `sa`, blank password |

**5-minute demo:** register with any 10-digit number (OTP appears in the dialog) → Profile → *Start Selling* →
create a kitchen → add 2–3 dishes → in a second browser window register as a buyer → add to cart → checkout →
place order → *I HAVE PAID* → from the seller window: confirm → ready → deliver.

## 📁 Project Structure

```
my-first-spring-api/                 repo root
├─ SocioMart-Project-Report.pdf     full report + setup guide (start here)
├─ docs/                            HTML versions of the report
└─ my-first-spring-api/             Maven project
   ├─ pom.xml                       Spring Boot 4.1.1 · Java 21 · dependencies
   ├─ mvnw / mvnw.cmd               Maven wrapper
   └─ src/main/
      ├─ java/com/example/my_first_spring_api/
      │    controller · service · repository · model · dto · exception
      └─ resources/
           application.properties  (port 8081, H2 file DB, Swagger)
           static/                 the SPA: index.html, css/styles.css, js/app.js
```

## 👥 Team workflow

- Default branch is **`main`** — before starting work: `git pull origin main`
- Keep commits small with clear messages; push to `main` when your change is tested
- Local-only files (`data/`, `bin/`, logs, test scripts) are git-ignored — **never force-add them**
- Factory reset of your local data: stop the app and delete the `data/` folder

## 🩺 Common issues

| Problem | Fix |
|---|---|
| `java -version` shows 17/11/8 | Install Temurin 21 with *Set JAVA_HOME* ticked, restart VS Code |
| Port 8081 already in use | Stop the other instance, or change `server.port` in `application.properties` |
| `mvnw` execution policy error (PowerShell) | Use `.\mvnw.cmd spring-boot:run` |
| Marketplace is empty | Expected on a fresh database — create a seller kitchen first |

Full troubleshooting table: page 6 of [`SocioMart-Project-Report.pdf`](SocioMart-Project-Report.pdf).
