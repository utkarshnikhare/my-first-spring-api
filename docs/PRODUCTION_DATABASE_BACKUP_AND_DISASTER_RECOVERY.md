# SocioMart Production Database Backup and Disaster Recovery Runbook

**Document Version:** 1.0.0  
**Effective Date:** 2026-11-01  
**Classification:** Operational Security & Disaster Recovery  
**Target Database:** PostgreSQL 16 (Hosted on Supabase)  
**Target Application:** SocioMart Web Service (Hosted on Render)  

---

## 1. PURPOSE AND SCOPE

This runbook establishes authoritative, repeatable procedures for backing up, verifying, and recovering the SocioMart production database.

It is designed to be executable by any authorized systems engineer, database administrator, or DevOps engineer during a routine maintenance window or an active catastrophic outage, without requiring prior familiarity with the codebase.

---

## 2. RECOVERY TARGETS (TARGET VS. MEASURED VALUES)

> [!WARNING]
> **RPO and RTO Separation of Target vs. Operational Reality:**
> - Neither RPO of 24 hours nor RTO of 30 minutes may be claimed as proven operational status until end-to-end production runs are executed.
> - The values below represent **engineering targets**; operational status remains **TARGET ONLY / NOT TESTED ON PRODUCTION** pending live execution.

| Metric | Engineering Target | Measured Benchmark | Current Operational Status | Operational Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **Recovery Point Objective (RPO)** | **24 hours** (Daily Scheduled Backup)<br>*Target Future: <1 hour via Supabase Pro PITR* | **Measured toolchain dump time: ~1.2s** | **TARGET ONLY (NOT VERIFIED ON PROD)** | On Supabase Free tier, continuous WAL archiving is unavailable. Nightly snapshots bound data loss to 24 hours. Cannot claim operational RPO until live backups are scheduled and run. |
| **Recovery Time Objective (RTO)** | **< 30 minutes** (from confirmed disaster decision to live restored service) | **Measured engine restore time: ~1.5s** (isolated PostgreSQL target) | **TARGET ONLY (NOT VERIFIED ON PROD)** | Database restoration takes $<3$ seconds for current schema/data. However, full operational recovery (new DB provisioning, Render env updates, container cold-boot) has not been timed on live infrastructure. |

---

## 3. PRODUCTION DATABASE IDENTIFICATION PROCEDURE

To safely verify the database connection without exposing secrets:

1. **Locate Render Service Environment Variables:**
   - Navigate to [Render Dashboard](https://dashboard.render.com).
   - Select service: `sociomart-demo` (Type: `Web Service`, Plan: `Free`).
   - Open **Environment** tab.
   - Note the presence of:
     - `SPRING_PROFILES_ACTIVE=prod`
     - `SOCIOMART_DB_JDBC_URL` (Encrypted / sync: false)
     - `SOCIOMART_DB_USERNAME` (Encrypted / sync: false)
     - `SOCIOMART_DB_PASSWORD` (Encrypted / sync: false)
2. **Supabase Host Identification:**
   - The connection string format is:
     `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require`
   - Direct connection pooler (Session mode): Port `5432`.
   - Transaction connection pooler: Port `6543`.
3. **Live Health Verification:**
   - Execute:
     ```bash
     curl -s https://sociomart-demo.onrender.com/api/health
     ```
   - Expected response: `{"status":"UP","database":"UP"}`.

---

## 4. DATABASE INVENTORY & DATA CRITICALITY CLASSIFICATION

The SocioMart schema comprises **29 tables** (26 business entities, `flyway_schema_history`, and 2 `spring_session` tables). Data is categorized into three recovery tiers:

```
+---------------------------------------------------------------------------------------+
| TIER 1: CRITICAL TRANSACTIONAL DATA (IRRECOVERABLE IF LOST)                           |
| - users: Customer accounts, seller credentials, approval status                       |
| - kitchens: Verified UPI IDs, GPay identifiers, accepted payment modes, slugs         |
| - products: Food items, inventory (remaining_quantity), pricing, categories          |
| - orders: Real buyer orders, payment_status (UNPAID/AWAITING/PAID), total amounts     |
| - order_items: Order-to-product mapping, item quantities, historical unit prices      |
| - recurring_schedules & occurrences: Scheduled meal pre-order commitments             |
| - quick_posts: Live urgent broadcasts and fulfillment availability                    |
+---------------------------------------------------------------------------------------+
| TIER 2: OPERATIONAL & AUDIT DATA (RECONSTRUCTIBLE WITH DIFFICULTY)                    |
| - admin_audit_logs: Historical administrator state changes                            |
| - ledger_events: Internal event audit trail                                           |
| - password_reset_requests / tokens: Transient auth recovery state                     |
| - areas & societies: Society directory mapping (re-creatable via seeders)            |
| - flyway_schema_history: Authoritative schema version tracker (V1–V21)               |
+---------------------------------------------------------------------------------------+
| TIER 3: TRANSIENT / DISPOSABLE DATA (AUTOMATICALLY REGENERATED)                       |
| - spring_session / spring_session_attributes: Active HTTP sessions (users re-login)   |
| - analytics_events: Non-transactional UI clickstream tracking                         |
+---------------------------------------------------------------------------------------+
```

---

## 5. BACKUP ARCHITECTURE & RETENTION POLICY

```
+-------------------+       pg_dump (custom)       +-----------------------+
| Supabase Prod DB  | ---------------------------> | sociomart_backup.dump |
+-------------------+                              +-----------------------+
                                                               |
                                                   AES-256-GCM AEAD
                                                   (PBKDF2, 12-byte IV)
                                                               |
                                                               v
                                                   +-----------------------+
                                                   | sociomart_backup.enc  |
                                                   | + .sha256 + .meta.json|
                                                   +-----------------------+
                                                               |
                                                   GitHub Actions Artifact /
                                                   Off-site Encrypted Cloud Storage
```

- **Frequency:** Automated daily at 20:30 UTC (02:00 AM IST).
- **Format:** PostgreSQL Custom Archive (`--format=custom`, compressed binary, supports selective table restore).
- **Encryption:** Authenticated Encryption with Associated Data (AEAD) via **AES-256-GCM** (`BackupCrypto.java`).
- **Key Derivation:** PBKDF2WithHmacSHA256 with 100,000 iterations and unique 16-byte cryptographically secure salt.
- **Nonce/IV:** Unique 12-byte (96-bit) cryptographically secure random nonce generated per backup via `SecureRandom`.
- **Authentication Tag:** 128-bit authentication tag appended to payload; verified by GCM engine prior to any plaintext output. Any single-bit alteration causes immediate `AEADBadTagException` and restoration aborts.
- **Integrity Manifest:** SHA-256 hash manifest generated simultaneously as secondary defense.
- **File Permissions:** Enforces `chmod 0600` on raw and encrypted backup files.
- **Credential Protection:** Passwords are read via ephemeral `PGPASSFILE` (mode 0600) and NEVER appear in process command-line arguments.
- **Retention Schedule:**
  - Daily backups: Retained for **14 days**.
  - Weekly snapshots (Sunday): Retained for **30 days**.
  - Monthly snapshots: Retained for **90 days**.

---

## 6. BACKUP EXECUTION INSTRUCTIONS

### Method A: Automated GitHub Actions (Recommended)
1. Navigate to the repository's **Actions** tab.
2. Select workflow: **Scheduled Production Database Backup**.
3. Click **Run workflow** $\to$ Branch: `main` $\to$ Enter retention days (default: 14) $\to$ Click **Run**.
4. The workflow will:
   - Run `pg_dump` over TLS.
   - Encrypt the archive with `BACKUP_ENCRYPTION_PASSPHRASE`.
   - Calculate and verify SHA-256.
   - Upload the encrypted bundle to GitHub Artifacts with 14-day retention.

### Method B: Manual CLI Execution (Operator Terminal)
Prerequisites: `postgresql-client` (`pg_dump`), `openssl`, and `sha256sum`.

```bash
# 1. Set environment variables (NEVER put credentials in script arguments)
export SOCIOMART_DB_URL="postgresql://<user>:<password>@<host>:5432/<dbname>?sslmode=require"
export BACKUP_ENCRYPTION_PASSPHRASE="<strong-secret-passphrase>"

# 2. Run the verified backup script
./scripts/backup-database.sh --encrypt --output-dir ./backups/exports --retention-days 14
```

Output:
- `./backups/exports/sociomart_backup_<timestamp>.dump.enc`
- `./backups/exports/sociomart_backup_<timestamp>.dump.enc.sha256`
- `./backups/exports/sociomart_backup_<timestamp>.dump.enc.meta.json`

---

## 7. BACKUP VERIFICATION INSTRUCTIONS

Before storing or certifying a backup:

1. **Verify Integrity Checksum:**
   ```bash
   sha256sum -c sociomart_backup_<timestamp>.dump.enc.sha256
   ```
   *Expected Output:* `sociomart_backup_<timestamp>.dump.enc: OK`.
2. **Verify Non-Zero File Size:**
   - File size must exceed 50 KB (empty databases produce <10 KB headers).
3. **Inspect Metadata Manifest:**
   - Check `.meta.json` for matching timestamp, byte count, and table inventory.

---

## 8. ISOLATED RESTORATION PROCEDURE (DR DRILL & TEST)

> [!CAUTION]
> **NEVER** run a restoration script pointing to the live production database.
> Always verify that `TARGET_DB_URL` points to an isolated, temporary, or staging database.

### Step 1: Provision Isolated PostgreSQL Database
Use a local container, a dedicated Supabase staging project, or temporary instance:
```bash
docker run --name sociomart-restore-test -e POSTGRES_PASSWORD=recoverytest -e POSTGRES_DB=sociomart_restore -p 5439:5432 -d postgres:16-alpine
```

### Step 2: Execute Isolated Restore
```bash
export TARGET_DB_URL="postgresql://postgres:recoverytest@localhost:5439/sociomart_restore"
export CONFIRM_ISOLATED_RECOVERY_TARGET="true"
export BACKUP_ENCRYPTION_PASSPHRASE="<passphrase-used-for-backup>"

./scripts/restore-database.sh --backup-file ./backups/exports/sociomart_backup_<timestamp>.dump.enc
```

> [!IMPORTANT]
> **Hardened Safeguards in `restore-database.sh`:**
> - **Confirmation Required:** Fails with exit code 1 if `CONFIRM_ISOLATED_RECOVERY_TARGET="true"` is not provided.
> - **Production Host Rejection:** Automatically rejects targets with hosts matching `supabase.co`, `render.com`, or database names containing `prod`.
> - **Pre-Flight Connection Probe:** Probes the database using `psql` to check database name, user, and recovery status before any table drop or `pg_restore --clean` is attempted.
> - **Ephemeral Credential Cleanup:** Restores without passwords in command line arguments via temporary `PGPASSFILE` mode `0600`, cleanly removed upon completion or interruption.

### Step 3: Run Post-Restore Verification Suite
Connect to the isolated target and verify:
```sql
-- 1. Verify all migrations applied
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;

-- 2. Verify business records
SELECT count(*) AS total_users FROM users;
SELECT count(*) AS total_kitchens FROM kitchens;
SELECT count(*) AS total_products FROM products;
SELECT count(*) AS total_orders FROM orders;

-- 3. Verify payment status distribution
SELECT payment_status, count(*) FROM orders GROUP BY payment_status;

-- 4. Verify sequences are synchronized (not reset to 1)
SELECT last_value FROM orders_id_seq;
SELECT last_value FROM users_id_seq;
```

---

## 9. EMERGENCY DISASTER RECOVERY PROCEDURES (LIVE OUTAGE)

### Scenario: Production Database Corrupted, Accidentally Truncated, or Hosted Provider Lost

#### Step 1: Declare Incident & Enable Maintenance Gate
1. Post notification to customer/seller communication channels.
2. In Render dashboard for `sociomart-demo`:
   - Set environment variable `MAINTENANCE_MODE=true` (or spin down web service temporarily to prevent incoming partial orders).

#### Step 2: Retrieve Latest Verified Backup
1. Access the latest successful backup artifact from GitHub Actions Artifacts or secure off-site bucket.
2. Verify SHA-256 checksum:
   ```bash
   sha256sum -c sociomart_backup_<timestamp>.dump.enc.sha256
   ```

#### Step 3: Provision Fresh PostgreSQL Database Target
1. In Supabase Dashboard (or new cloud PostgreSQL provider):
   - Create a new project in the same region (`us-west` / `ap-south-1`).
   - Secure the connection credentials.

#### Step 4: Execute Restoration into Fresh Database Target
```bash
export TARGET_DB_URL="postgresql://postgres:<password>@<new-isolated-host>:5432/postgres?sslmode=require"
export CONFIRM_ISOLATED_RECOVERY_TARGET=true
export BACKUP_ENCRYPTION_PASSPHRASE="<backup-passphrase>"

./scripts/restore-database.sh --backup-file sociomart_backup_<timestamp>.dump.enc
```

> [!CAUTION]
> `restore-database.sh` unconditionally rejects production hostnames (`supabase.co`, `render.com`) and databases named `*prod*`.
> If an operator must perform an exceptional, manual in-place restore directly over an existing production database, this must be executed manually using native `pg_restore` with explicit dual-operator authorization, never via automated scripts.

#### Step 5: Repoint Render Application & Restart
1. In Render dashboard $\to$ `sociomart-demo` $\to$ **Environment**:
   - Update `SOCIOMART_DB_JDBC_URL` with the new target URL.
   - Update `SOCIOMART_DB_USERNAME` and `SOCIOMART_DB_PASSWORD`.
   - Remove `MAINTENANCE_MODE` if set.
2. Click **Manual Deploy** $\to$ **Deploy latest commit** (`74de3ad4e4e32a09a9b0802efbdd3f349d4281ef`).

#### Step 6: Validate Live Production Recovery
1. Query `https://sociomart-demo.onrender.com/api/health`:
   - Verify `{"status":"UP","database":"UP"}`.
2. Query `https://sociomart-demo.onrender.com/api/kitchens`:
   - Verify active kitchens render with their accepted payment modes (`UPI`, `GPAY`, `CASH_ONLY`).
3. Check seller login and order history:
   - Ensure existing buyer orders and payment statuses are intact.

---

## 10. RECOVERY DECISION CHECKLIST

```
[ ] 1. Is the data corruption confirmed to affect persistent tables?
[ ] 2. Has the live database connection been suspended to avoid partial writes?
[ ] 3. Has the latest backup artifact been downloaded and SHA-256 checksum verified?
[ ] 4. Has the backup passphrase been verified by test-decrypting?
[ ] 5. Has a replacement database instance or isolated staging target been provisioned?
[ ] 6. Has the restore script executed with exit code 0?
[ ] 7. Have Flyway migration history, record counts, and sequences been checked?
[ ] 8. Has the Render service environment variable been repointed?
[ ] 9. Has /api/health returned {"status":"UP","database":"UP"}?
[ ] 10. Have smoke tests confirmed seller login and buyer kitchen discovery?
```

---

## 11. ROLES AND RESPONSIBILITIES

- **Lead Application Engineer / Release Engineer:**
  - Maintains backup scripts, workflow definitions, and encryption keys.
  - Reviews isolated restore test results on a monthly cadence.
- **Repository Administrator / Project Owner:**
  - Holds Supabase project master credentials and Render team administration.
  - Authorizes live database failovers or emergency overwrites.
- **On-Call Engineer:**
  - Executes disaster recovery runbook during an outage.
  - Verifies RPO/RTO metrics post-incident.
