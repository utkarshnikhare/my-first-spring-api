# SocioMart Operational Incident Response Runbooks

## Runbook 1: HTTP 504 Gateway Timeout / Render Cold Start

### Symptoms
- Frontend displays: `"Seller service unavailable - HTTP 504"`.
- Requests through Netlify proxy timeout after 26 seconds.

### Root Cause
Render Free tier containers spin down after 15 minutes of inactivity. When a request arrives, the JVM cold start takes 30–50 seconds, exceeding Netlify's 26-second timeout.

### Immediate Mitigation (Zero Downtime)
1. **User Action:** Tap the **"Retry"** button on the error screen. Render will have completed waking up by the time of the retry.
2. **Direct Access Fallback:** Users can access the app directly on the Render URL:
   - Seller: `https://sociomart-demo.onrender.com/seller.html`
   - Buyer: `https://sociomart-demo.onrender.com/`
3. **Manual Warmup:** Trigger a manual health check:
   ```bash
   curl -s -i https://sociomart-demo.onrender.com/api/health
   ```

### Permanent Fix (Scale-Out)
- Upgrade the Render Web Service to the Starter tier ($7/mo), which guarantees 24/7 continuous operation with zero spin-down.

---

## Runbook 2: Database Connection Pool Exhaustion

### Symptoms
- Backend logs show `HikariPool - Connection is not available, request timed out after 10000ms`.
- API calls return HTTP 500.

### Diagnostic Steps
1. Inspect active connections on Supabase:
   ```sql
   SELECT count(*), state FROM pg_stat_activity GROUP BY state;
   ```
2. Identify long-running queries or uncommitted transactions:
   ```sql
   SELECT pid, now() - xact_start AS duration, query 
   FROM pg_stat_activity 
   WHERE state <> 'idle' 
   ORDER BY duration DESC;
   ```

### Remediation
1. Terminate runaway locks:
   ```sql
   SELECT pg_terminate_backend(<PID>);
   ```
2. SocioMart's `TransactionAdmissionConfiguration` automatically isolates root and nested transaction pools to prevent deadlocks.
3. In `application-prod.properties`, adjust `spring.datasource.hikari.maximum-pool-size=20` if traffic demands higher concurrency.

---

## Runbook 3: Emergency Production Rollback Procedure

### Netlify Rollback
1. Open the Netlify Deploys Dashboard: [app.netlify.com/projects/sociomart-frontend/deploys](https://app.netlify.com/projects/sociomart-frontend/deploys).
2. Locate the last known good deployment.
3. Click the options menu (`...`) and select **"Publish deploy"**.
4. Rollback takes effect globally across the edge CDN within 5 seconds.

### Render Rollback
1. Open the Render Dashboard: [dashboard.render.com](https://dashboard.render.com).
2. Select `sociomart-demo` (`srv-dad5lfajnfac73ei06s0`).
3. Under **Deploys**, click on the previous successful commit.
4. Select **Rollback to this deploy**.
5. Render deploys the previous container image in ~60 seconds.

---

## Runbook 4: Compromised Credential Rotation

### Admin & Database Credentials
1. If database credentials or admin passwords are leaked:
2. Update the secret in Supabase: Settings -> Database -> Reset Database Password.
3. Update environment variables in Render:
   - `SOCIOMART_DB_PASSWORD`
   - `SOCIOMART_ADMIN_PASSWORD`
   - `SOCIOMART_SUPER_ADMIN_PASSWORD`
4. Trigger a Manual Deploy on Render to load the updated secrets into the runtime environment.
