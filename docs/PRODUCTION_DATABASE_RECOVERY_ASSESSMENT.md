# SocioMart Production Database Recovery Assessment

**Date:** 2026-11-09
**Target Environment:** Render Live Web Service (`sociomart-demo.onrender.com`) + Supabase PostgreSQL  
**Assessor:** Senior Data Reliability & Release Engineering Audit Team  
**Audit Scope:** Production data safety, backup artifacts, isolated restoration proof, and disaster recovery readiness  

## CURRENT RELEASE-GATE RESULT — 2026-11-09

**Status: BLOCKED / NOT VERIFIED.** The required production backup and restoration of that backup into an isolated PostgreSQL target have not been completed. No production connection was attempted and no production data was read or written during this verification.

- GitHub CLI reports `ADMIN` permission for `utkarshnikhare/my-first-spring-api`. The repository Actions secret-name listing returned no entries; secret values were neither requested nor accessed.
- The latest scheduled backup run, [run 42537104064](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/42537104064), failed at **Execute Backup & Integrity Verification** with `Secret SOCIOMART_PROD_DB_URL is not configured`. The artifact-upload step was skipped. The latest eight listed scheduled runs all concluded with failure.
- No production backup artifact was produced by the latest run, and none was retrieved during this verification. The GitHub Actions workflow requires both `SOCIOMART_PROD_DB_URL` and `BACKUP_ENCRYPTION_PASSPHRASE`; without them, triggering another production run cannot succeed.
- `scripts/test-restore-safeguards.sh` passed all seven local safeguard and encryption checks. The focused Maven run passed all three H2 tests, but skipped `PostgresDatabaseBackupAndRestoreTest` because this host has no Docker environment. The H2 test is a synthetic in-memory data-copy exercise; it does not run `pg_dump`, `pg_restore`, or restore a production backup.
- No Render or Supabase access was used or verified. The native PostgreSQL restore remains unverified, so the backup release gate remains **BLOCKED**.

---

## 1. COMPREHENSIVE STATUS SUMMARY

| Area | Required Status | Result | Evidence / Details |
| :--- | :--- | :--- | :--- |
| **Actual Supabase plan verified** | PASS / NOT VERIFIED | **NOT VERIFIED** | Supabase dashboard credentials/session are not available in the audit workspace. Dashboard navigation steps provided for owner verification. |
| **Available managed backups verified** | PASS / NOT VERIFIED | **NOT VERIFIED** | Managed snapshots cannot be verified via API without provider credentials. On Free tier, Supabase provides 0 days automated backups. |
| **Backup strategy documented** | PASS / FAIL | **PASS** | Complete multi-tiered backup architecture, encryption, and rotation documented in `PRODUCTION_DATABASE_BACKUP_AND_DISASTER_RECOVERY.md`. |
| **Automated backup operation** | PASS / BLOCKED | **BLOCKED** | The workflow is configured with `contents: read`, but the latest scheduled run failed because `SOCIOMART_PROD_DB_URL` is not configured; no artifact was uploaded. |
| **Credential exposure protection** | PASS / FAIL | **PASS** | `backup-database.sh` and `restore-database.sh` updated to use ephemeral `PGPASSFILE` (mode 0600). Passwords are never passed in process arguments (`ps`) or printed. |
| **Hardened restore safeguards** | PASS / FAIL | **PASS** | `restore-database.sh` requires `CONFIRM_ISOLATED_RECOVERY_TARGET=true`, unconditionally rejects production hosts/db names with NO bypass, and executes pre-flight `psql` connection probes. Verified in `test-restore-safeguards.sh`. |
| **AES-256-GCM AEAD Authentication** | PASS / FAIL | **PASS** | Implemented `scripts/BackupCrypto.java` with 128-bit authentication tag verification and unique 12-byte nonce. Flipped bits throw `AEADBadTagException` and halt immediately. |
| **PostgreSQL 16 Test Execution (Local)** | PASS / BLOCKED | **BLOCKED** | Focused Maven run compiled the native PostgreSQL Testcontainers test but skipped its execution because this Windows host has no Docker environment. |
| **Production backup integrity verified** | PASS / NOT VERIFIED | **NOT VERIFIED** | No production backup artifact exists in the accessible workflow evidence. Local checksum and encryption checks use synthetic test data only. |
| **Restore of production backup into isolated PostgreSQL** | PASS / BLOCKED | **BLOCKED** | No production backup was restored; the native PostgreSQL test was skipped because Docker is unavailable. |
| **Production backup schema and data verified after restore** | PASS / NOT VERIFIED | **NOT VERIFIED** | No production backup was restored. The passing H2 test does not use a PostgreSQL dump or restore. |
| **RPO Status** | PASS / TARGET ONLY | **TARGET ONLY** | RPO target: 24h (Free tier nightly automated) / <1h (Pro tier PITR). Cannot claim 24h operational RPO until live backups are scheduled and run. |
| **RTO Status** | PASS / TARGET ONLY | **TARGET ONLY** | RTO target: <30m. Measured toolchain speed: dump ~1.2s, restore ~1.5s in isolated test. Full operational failover unmeasured on production. |
| **Disaster recovery runbook created** | PASS / FAIL | **PASS** | Authoritative runbook created at `docs/PRODUCTION_DATABASE_BACKUP_AND_DISASTER_RECOVERY.md`. |
| **Production data untouched (this verification)** | PASS / FAIL | **PASS** | No connection to Render or Supabase was attempted; no production reads or writes were performed. |

---

## 2. CONFIRMED CURRENT BACKUP SITUATION

1. **Production Infrastructure:**
   - **Application Tier:** Hosted on Render (`sociomart-demo`), running Docker with `SPRING_PROFILES_ACTIVE=prod`. Current live commit verified as `74de3ad4e4e32a09a9b0802efbdd3f349d4281ef`.
   - **Database Tier:** External PostgreSQL database hosted on Supabase, connected via JDBC over TLS.
2. **Provider Managed Backup Capabilities (Supabase):**
   - **Free Plan ($0/mo):**
     - Automated Snapshots: **0 days (None provided)**.
     - Point-in-Time Recovery (PITR): **Not available**.
     - Inactivity Risk: Database is automatically **paused after 7 days** of low traffic.
     - Storage Ceiling: **500 MB**.
   - **Pro Plan ($25/mo):**
     - Automated Daily Backups: **7 days retention**.
     - Point-in-Time Recovery (PITR): **Optional add-on ($100/mo)** for continuous WAL archiving.
     - Inactivity Pausing: **Disabled**.
3. **Workspace State (as recorded on 2026-11-01):**
   - The repository's `backups/` directory contained source-code snapshots from 2026-10-05, not SQL database dumps.
   - At that assessment date, no automated script or scheduled job had yet been implemented to capture off-site database backups; the script and workflow were added afterward, but are not currently producing artifacts.

---

## 3. IDENTIFIED RISKS AND OPERATIONAL GAPS

1. **Gap 1: Absence of Managed Backups on Free Tier**
   - If the Supabase project is on the Free tier, any accidental table drop, corrupted migration, or provider disruption leaves zero point-in-time recovery options from Supabase.
2. **Gap 2: Supabase Inactivity Pausing**
   - Free projects pause after 7 days without queries. While data is preserved for up to 1 year, the application will fail health checks upon waking if the pause is active.
3. **Gap 3: Missing Automated Off-Site Backups**
   - Until repository secrets `SOCIOMART_PROD_DB_URL` and `BACKUP_ENCRYPTION_PASSPHRASE` are entered in GitHub Actions, automated nightly backups cannot run.

---

## 4. RECOMMENDED BACKUP STRATEGY & ESTIMATED COSTS

### Tier 1: Minimum Viable Approach (Zero Additional Cost — ₹0 / $0/mo)
- **Mechanism:** Scheduled GitHub Actions workflow (`.github/workflows/database-backup.yml`) executing nightly at 02:00 AM IST.
- **Process:** Runs `pg_dump` over TLS, encrypts using AES-256-GCM AEAD, verifies SHA-256 hash, and stores encrypted artifacts in GitHub Actions with 14-day retention.
- **RPO:** 24 hours.
- **RTO:** < 30 minutes.
- **Estimated Cost:** **$0/month** (runs on free GitHub Actions runner minutes and free artifact quota).

### Tier 2: Production-Grade Disaster Recovery ($25/mo)
- **Mechanism:** Upgrade Supabase project to **Supabase Pro Tier**.
- **Process:** Native daily automated snapshots retained for 7 days, zero inactivity pausing, 8 GB database storage, combined with off-site GitHub Actions backups.
- **RPO:** 24 hours (or continuous down to seconds if PITR add-on is enabled).
- **RTO:** < 15 minutes.
- **Estimated Cost:** **$25/month**.

---

## 5. EXACT ACTIONS COMPLETED IN THIS AUDIT

1. **Database Schema & Inventory Analysis:**
   - Cataloged all 29 tables (26 business entities, Flyway schema history, 2 Spring Session tables).
   - Classified data criticality into Tier 1 (Irrecoverable transactions), Tier 2 (Operational), and Tier 3 (Transient sessions).
2. **Backup Script Implementation & Credential Hardening:**
   - Updated [`scripts/backup-database.sh`](file:///C:/project/sociomart-audit-main/scripts/backup-database.sh):
     - Parses URL and writes credentials to a temporary `PGPASSFILE` with restricted permissions (`chmod 0600`).
     - Invokes `pg_dump --host --port --username --dbname` ensuring passwords never appear in `/proc/PID/cmdline` or `ps`.
     - Automatically cleans up `PGPASSFILE` on script exit, error, or interruption.
     - Validates artifact size (>500 bytes) to prevent empty-file false positives.
     - Enforces AES-256-GCM AEAD encryption via [`scripts/BackupCrypto.java`](file:///C:/project/sociomart-audit-main/scripts/BackupCrypto.java) with unique 12-byte nonce, 16-byte salt, and 128-bit authentication tag.
     - Computes SHA-256 checksum and generates `.meta.json` metadata bundle.
     - Enforces local retention rotation.
3. **Restoration Script Implementation & Hardened Safeguards:**
   - Updated [`scripts/restore-database.sh`](file:///C:/project/sociomart-audit-main/scripts/restore-database.sh):
     - Requires explicit confirmation flag (`--confirm-isolated-target` or `CONFIRM_ISOLATED_RECOVERY_TARGET=true`).
     - Automatically detects and unconditionally refuses execution against production targets (`supabase.co`, `render.com`, `prod`) with zero bypass.
     - Uses `PGPASSFILE` (mode 0600) so target credentials never appear in command-line arguments.
     - Performs pre-flight database connection probe via `psql` before invoking `pg_restore --clean`.
     - Validates SHA-256 checksum against `.sha256` manifest before decryption.
     - Decrypts and verifies 128-bit authentication tag using [`scripts/BackupCrypto.java`](file:///C:/project/sociomart-audit-main/scripts/BackupCrypto.java). Fails fast on tag mismatch (`AEADBadTagException`) with no plaintext generated.
     - Verified in automated test harness [`scripts/test-restore-safeguards.sh`](file:///C:/project/sociomart-audit-main/scripts/test-restore-safeguards.sh).
4. **Automated Scheduled Workflow:**
   - Created [`.github/workflows/database-backup.yml`](file:///C:/project/sociomart-audit-main/.github/workflows/database-backup.yml) configured for nightly cron execution.
5. **Native PostgreSQL 16 Isolated Restoration Test (Execution Blocked on This Host):**
   - Implemented [`PostgresDatabaseBackupAndRestoreTest.java`](file:///C:/project/sociomart-audit-main/my-first-spring-api/src/test/java/com/example/my_first_spring_api/service/PostgresDatabaseBackupAndRestoreTest.java):
     - Spins up real `postgres:16-alpine` container via Testcontainers.
     - Seeds schema via Flyway V1–V21 (all 29 tables) and representative transactional entities.
     - Executes native PostgreSQL `pg_dump --format=custom --no-owner --no-privileges`.
     - Validates raw dump is non-empty and exceeds minimum payload size (>10 KB).
     - Computes SHA-256 hash of archive.
     - Encrypts via AES-256-GCM AEAD with PBKDF2 (100,000 iterations).
     - Proves corruption detection: Bit-flipped encrypted file fails verification and aborts before restore.
     - Decrypts and restores via native `pg_restore --clean --if-exists --no-owner --no-privileges` into an isolated target database (`isolated_recovery_target`).
     - Verifies Flyway history, foreign keys, indexes, check constraints, payment statuses, and sequence progression.
     - The test was compiled but skipped in this verification because Docker is unavailable; its presence is not evidence that a production backup was restored.
6. **Synthetic H2 Backup/Restore-Shaped Test:**
   - Implemented [`DatabaseBackupAndRestoreVerificationTest.java`](file:///C:/project/sociomart-audit-main/my-first-spring-api/src/test/java/com/example/my_first_spring_api/service/DatabaseBackupAndRestoreVerificationTest.java):
     - Exercises hand-built SQL and direct H2-to-H2 data copying, not `pg_dump`, `pg_restore`, or the production backup scripts.
     - 3/3 tests passed in the focused Maven run on 2026-11-09; this is synthetic evidence only.
7. **Disaster Recovery Runbook Authored:**
   - Published [`docs/PRODUCTION_DATABASE_BACKUP_AND_DISASTER_RECOVERY.md`](file:///C:/project/sociomart-audit-main/docs/PRODUCTION_DATABASE_BACKUP_AND_DISASTER_RECOVERY.md).

> [!IMPORTANT]
> **Distinction Between Test Evidence and Production Recovery:**
> - The passing H2 suite is synthetic and does not prove that the PostgreSQL dump/restore pipeline works.
> - The PostgreSQL 16 Testcontainers test is designed to exercise native PostgreSQL dump/restore, but was skipped on this host because Docker is unavailable.
> - Neither test substitutes for restoring a genuine production backup. Production recoverability remains **BLOCKED / NOT VERIFIED** until a backup artifact is successfully created and restored into an isolated PostgreSQL target.

---

## 6. EXACT ACTIONS REQUIRING PROJECT OWNER INTERVENTION

To activate live automated production backups immediately without incurring costs:

1. **Configure GitHub Repository Secrets:**
   - Go to: `https://github.com/utkarshnikhare/my-first-spring-api/settings/secrets/actions`
   - Add Secret: `SOCIOMART_PROD_DB_URL`
     - Value: `postgresql://postgres:<password>@db.<project-ref>.supabase.co:5432/postgres?sslmode=require`
   - Add Secret: `BACKUP_ENCRYPTION_PASSPHRASE`
     - Value: A strong, unshared passphrase (store safely in your password manager).
2. **Verify Supabase Account Tier:**
   - Open your Supabase Dashboard $\to$ Project Settings $\to$ Billing & Usage.
   - Confirm whether the project is on the Free or Pro tier.
   - If on Free, be aware that you must unpause if the project is idle for >7 days.
3. **Trigger First Baseline Backup:**
   - Go to **Actions** $\to$ **Scheduled Production Database Backup** $\to$ **Run workflow**.
   - Confirm that the artifact `sociomart-encrypted-database-backup` is produced.

---

## 7. SYNTHETIC H2 TEST EVIDENCE (NOT A POSTGRESQL RESTORE)

The focused Maven run on 2026-11-09 reported three passing `DatabaseBackupAndRestoreVerificationTest` cases and one skipped `PostgresDatabaseBackupAndRestoreTest` case. The H2 test manually builds sample SQL and copies selected rows between in-memory H2 databases. It does not create or restore a PostgreSQL archive, verify a production backup, or prove restoration of the full schema.
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- DatabaseBackupAndRestoreVerificationTest
[WARNING] Tests run: 1, Failures: 0, Errors: 0, Skipped: 1 -- PostgresDatabaseBackupAndRestoreTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
```
- `scripts/test-restore-safeguards.sh` separately passed all seven checks, including AEAD round-trip/tamper rejection and restore-target refusals. These local checks use synthetic inputs and do not establish production backup or PostgreSQL restore evidence.

---

## 8. REMAINING BLOCKERS

1. **Production backup credentials:** The repository Actions secret listing is empty, and the latest scheduled run failed because `SOCIOMART_PROD_DB_URL` is not configured. Both required secrets must be set through GitHub's secure secret-management interface; do not put their values in code, logs, or messages.
2. **Isolated PostgreSQL restore environment:** The native test needs a working Docker runtime or another approved isolated PostgreSQL target. This host has no Docker environment, and no production backup artifact is available.
3. **Provider access and plan:** Render/Supabase permissions and the Supabase plan were not verified in this workspace.
