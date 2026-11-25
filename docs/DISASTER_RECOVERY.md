# SocioMart Disaster Recovery, Backup & Business Continuity Runbook

## 1. Objectives & SLA Targets

- **Recovery Point Objective (RPO):** < 1 Hour (Maximum acceptable data loss window during catastrophic hardware or datacenter failure).
- **Recovery Time Objective (RTO):** < 15 Minutes (Maximum downtime to restore services from backup to an operational state).

---

## 2. Backup Strategy & Architecture

SocioMart implements a multi-tier backup and data protection architecture:

### A. Automated Daily & Shift Backups (GitHub Actions + Supabase)
- **Schedule:** Automated GitHub Actions workflow (`.github/workflows/database-backup.yml`) triggers every 12 hours.
- **Format:** Native PostgreSQL 16 dump (`pg_dump -Fc` custom binary compressed format).
- **Security:** Dumps are encrypted using AES-256 before transmission and stored in secure cloud object storage.
- **Retention:** 30 days rolling retention for daily snapshots; 12 months for month-end snapshots.

### B. Transactional Flyway Migrations
- All schema alterations are version-controlled under `src/main/resources/db/migration/` (currently up to `V28`).
- Hibernate DDL auto is strictly locked to `validate` in production (`spring.jpa.hibernate.ddl-auto=validate`), ensuring no unexpected schema drift.

---

## 3. Step-by-Step Restoration Procedure (Drill Runbook)

In the event of database corruption, accidental table modification, or catastrophic failure:

### Step 1: Isolate the Incident
1. Put the frontend into maintenance mode or pause traffic to avoid writes to a degraded database.
2. In the Render Dashboard, suspend the Web Service temporarily:
   `Dashboard -> sociomart-demo -> Settings -> Suspend Service`.

### Step 2: Retrieve the Latest Verified Backup
1. Access the secure backup artifact repository:
   ```bash
   # Download the latest backup timestamped archive
   aws s3 cp s3://sociomart-backups/latest-production-backup.dump ./backup.dump
   ```
2. Verify SHA-256 checksum against the backup manifest.

### Step 3: Execute Restoration to Clean Database
1. Provision or clean target database schema.
2. Restore using `pg_restore`:
   ```bash
   pg_restore --clean --if-exists --no-owner --no-privileges \
              -h <TARGET_DB_HOST> -p 5432 -U postgres -d postgres backup.dump
   ```

### Step 4: Validate Data Integrity
Run verification queries:
```sql
SELECT count(*) FROM users;
SELECT count(*) FROM kitchens;
SELECT count(*) FROM orders;
SELECT max(id) FROM orders;
```

### Step 5: Resume Application Services
1. Re-enable the Render Web Service.
2. Monitor `/api/health` and `/api/version` endpoints until HTTP 200 is confirmed.
3. Verify buyer and seller login flows.
