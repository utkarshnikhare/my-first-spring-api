# SocioMart — Production Release Runbook (PostgreSQL, Render)

**Status: PRODUCTION RELEASE BLOCKED; BACKUP/RESTORE NOT TESTED.** The Render dashboard reports the
service's live commit as `76c11a4`, with auto-deploy Off. Its latest manual attempt, commit `dd0807e`,
failed Hibernate schema validation because `orders.payment_method` was missing. Read-only `/api/health`
and `/api/version` requests each timed out after 30 seconds. The audit branch adds a forward-only V17
repair migration; it has not been applied to production. Database identity, migration history, backup
policy and existing-record integrity remain unverified. No production migration or deployment was run
during this audit. Render configuration has `SPRING_FLYWAY_ENABLED=true` and
`SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`; inspect the actual Flyway history before any deployment and do
not change either setting as a shortcut. Do not assume the production database is empty or matches this
repository. No credential values belong in this file.

---

## 1. Exact connection contract (verified from `application-prod.properties`)

The `prod` profile reads exactly these environment variables (no alternatives, no defaults):

| Environment variable | Maps to property | Purpose |
|---|---|---|
| `SOCIOMART_DB_JDBC_URL` | `spring.datasource.url` | PostgreSQL JDBC URL (e.g. `jdbc:postgresql://host:5432/sociomart`) |
| `SOCIOMART_DB_USERNAME` | `spring.datasource.username` | Database role |
| `SOCIOMART_DB_PASSWORD` | `spring.datasource.password` | Database password (rendered at boot from the secret store) |

Also required at boot: `SPRING_PROFILES_ACTIVE=prod`, `SOCIOMART_ADMIN_PASSWORD`,
`SOCIOMART_SUPER_ADMIN_PASSWORD` (wired to `sociomart.auth.admin-password` /
`sociomart.auth.super-admin-password`; `ProductionStartupValidator` aborts startup with
`Refusing to start in production: ...` if either resolves blank — names only, values are never echoed),
plus `PUBLIC_BASE_URL` and the Render-injected `PORT`.

Pooler note from the profile: the session-mode pooler (5432) works as-is; if a transaction pooler (6543)
is used, append `prepareThreshold=0` to the JDBC URL. Current repository Hikari settings are a maximum
pool of 4, minimum idle of 2, 10-second connection timeout and 3-second validation timeout. These are
repository settings, not proof of the running Render configuration.

## 2. Provider-neutral provisioning checklist

1. Choose a provider (section 3) and create the project/database named `sociomart` in the provider's
   region closest to the Render service region.
2. Create a dedicated database role and password; store them in Render's secret-file/env store — never in
   chat, tickets, screenshots or this repository.
3. Confirm connectivity from an approved machine: `psql`/GUI connect using TLS, note host/port/db name.
4. Build the JDBC URL in the provider's documented format; use the provider's supported TLS connection
   method (Supabase documents direct/pooler TLS modes).
5. In Render, set (values never printed elsewhere): `SPRING_PROFILES_ACTIVE`, `SOCIOMART_DB_JDBC_URL`,
   `SOCIOMART_DB_USERNAME`, `SOCIOMART_DB_PASSWORD`, `SOCIOMART_ADMIN_PASSWORD`,
   `SOCIOMART_SUPER_ADMIN_PASSWORD`, `PUBLIC_BASE_URL`.
6. Leave `sociomart.bootstrap.enabled` unset (defaults to `true`) so `ProductionBootstrap` creates the
   two admin accounts, the feature flags and — only when the database is completely empty — the pilot
   area/society. It never creates sellers, kitchens, products, buyers or orders.
7. Treat the live database as existing and unknown until verified. Identify its provider, database,
   schema, Flyway history and record counts using approved read-only access. Take and restore-test a
   backup before any deployment that may run migrations. Flyway owns the schema; this branch includes
   migrations through V17, including an additive repair for a missing `orders.payment_method` column.
   Never baseline, reset, clean or drop an existing database to force startup.
   Do not enable automatic demo seeding or copy demo data.
8. Only after the release gate is satisfied, deploy the exact approved commit and verify: boot log shows
   profile `prod`, Flyway validation/application outcome is recorded, `/api/health` reports both
   `status=UP` and `database=UP`, `/h2-console` is denied, and `/api/auth/config` reflects production
   authentication settings.
9. Exercise the backup/restore procedure (section 5) on an **isolated** copy before declaring the
   release viable.
10. Record evidence (log excerpts, screenshots, Flyway version row) in the release PR/issue.

## 3. Supported PostgreSQL options (verified 2026-10-12)

| Criterion | Supabase | Render Postgres | Other managed PG (e.g. Neon, Railway, Aiven) |
|---|---|---|---|
| Free tier | **$0/mo**: 500 MB DB, max 2 active projects, **paused after 1 week of inactivity**, community support, **no backups** | Free compute plans exist for **Render Postgres** with usage limits (verify current limits at provisioning; the web service also has a free plan that sleeps after ~15 min idle) | Varies; typically a limited free DB with no SLA |
| Paid tier | **Pro from $25/mo**: 8 GB/project, **daily backups, 7-day retention**, email support | Workspace **Pro $25/mo** + paid compute (from $7/mo 512 MB, $25/mo 2 GB); **PITR listed on paid plans** | Paid plans from ~$0–20+/mo |
| Fits app config? | Yes — `jdbc:postgresql://` + TLS; session-mode pooler documented | Yes — same region as the web service, lowest latency | Yes if standard PostgreSQL + JDBC URL |
| Backups on free | **No** | Not guaranteed on free — verify per plan | Verify per plan |
| Availability on free | **Pauses after 1 week idle** — unacceptable for a live service | Free usage limits; app free plan sleeps (cold starts) | Varies |

**Free tier ≠ production-grade.** Free tiers generally provide: no backups, no PITR, no SLA,
pause/sleep behaviour, single region, shared resources, possible project eviction. Production-grade
guarantees (backups with retention, PITR, HA, SLA, restore support) require a **paid** tier on either
provider. **Do not activate billing or create a paid resource without explicit owner approval.**

## 4. Administrator bootstrap (secure, by design)

- Admin credentials exist **only** as environment variables (`SOCIOMART_ADMIN_PASSWORD`,
  `SOCIOMART_SUPER_ADMIN_PASSWORD`), resolved at boot into `sociomart.auth.*-password`. They are never
  stored in the repository, never logged, and error messages name the variables — never their values
  (covered by `ProductionStartupValidatorTest`).
- `ProductionBootstrap` (idempotent, `ApplicationRunner`) ensures the Super Admin and Admin accounts,
  feature flags and the pilot area/society on first boot of an empty database. Mobile numbers are
  configurable (`sociomart.bootstrap.super-admin-mobile` / `sociomart.bootstrap.admin-mobile`); passwords
  are env-only.
- Rotating a leaked credential means changing the environment variable and redeploying; the bootstrap
  does not overwrite an existing account, so rotation must use the admin password-change path.

## 5. Backup and isolated restore-validation procedure — **STATUS: NOT TESTED (release blocker)**

No backup or restore has ever been executed against a SocioMart database. The procedure below must be
performed and evidenced on an **isolated** copy before any production release; until an isolated restore
succeeds end-to-end, backup/restore must be described as *untested*.

**Backup (manual verification step):**
1. Trigger/confirm the provider backup (provider UI or CLI), or take a logical dump with PostgreSQL
   client tools. `pg_dump` requires a libpq connection URI, not the application's `jdbc:postgresql:` URL.
   In a secure operator shell, derive `SOCIOMART_LIBPQ_URI` from the private JDBC configuration (remove
   only the `jdbc:` prefix and verify PostgreSQL client-compatible options), and use a protected
   `PGPASSFILE` for credentials rather than embedding a password in the URI or command line:
   `pg_dump --no-owner --no-privileges --format=custom --dbname="$SOCIOMART_LIBPQ_URI" --file=sociomart-YYYYMMDD.dump`.
2. Store the artifact in owner-controlled storage; record its size, timestamp and checksum.
3. Confirm the dump contains the Flyway history rows (`flyway_schema_history`) and the `SPRING_SESSION*`
   tables.

**Isolated restore validation (must pass before release):**
1. Create a throwaway empty database (`sociomart-restore-test`) — separate from production.
2. Restore the custom-format dump into that isolated database with a separate non-production connection
   URI and protected credentials:
   `pg_restore --no-owner --no-privileges --dbname="$SOCIOMART_RESTORE_LIBPQ_URI" sociomart-YYYYMMDD.dump`.
3. Boot the app against it with `SPRING_PROFILES_ACTIVE=prod` and **test-only** admin env vars; verify
   Flyway reports the schema version as already applied (`validate` passes, no pending migrations),
   `/api/health` = `UP`, and one read query per core table succeeds.
4. Run one write + one read (e.g. create a draft order in a scratch session) and confirm persistence
   across an application restart.
5. Record evidence; drop the throwaway database.

If any step fails, the release stops (Phase 5 gate) — no production cutover.

## 6. Seed-data policy

Do **not** migrate demo/seed records automatically and do **not** claim existing production records have
been preserved without a before/after comparison. The production database identity and contents are not
verified in this audit. Preserve all existing data; any test data must be created only in an isolated
environment.

## 7. Release gate and recovery plan

**Gate (all must be true before merge + deploy):**
- [ ] Durable PostgreSQL exists, is reachable, and an isolated restore has succeeded (section 5).
- [ ] All datasource + admin env vars are configured in Render (names in section 1; values never printed).
- [ ] Migration plan (Flyway forward-only) and backup/restore plan approved by the owner.
- [ ] Exact commit to deploy known; CI green on that commit.
- [ ] Production configuration tested (isolated restore boot, section 5 step 3–4).
- [ ] Owner explicitly authorized merge **and** deploy.

If any box is unchecked: **stop before production mutation and report the blocker** — no merge, no
deploy, no migrations.

**Recovery plan (keep every production change reversible):**
1. Render `autoDeploy: false` keeps deploys explicit; roll back by re-deploying the previous known-good
   commit from the Render dashboard (Render keeps prior images/builds).
2. Environment variables are only added/changed with the change recorded beforehand; rollback = restore
   the recorded prior values (Render env-var history) and redeploy.
3. Migrations are forward-only: never edit any applied migration (`V1`–`V17` in this branch). A bad migration is corrected with a new forward
   migration, or — before any data loss matters — the database is restored from the section-5 backup and
   the fixed commit re-deployed.
4. If a critical acceptance test fails after deploy (Phase 5 list): halt rollout, roll back the service
   to the previous commit, keep the database untouched, and report the failure with evidence before any
   further attempt.
