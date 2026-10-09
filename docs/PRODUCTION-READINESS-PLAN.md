# SocioMart Production Readiness Plan

**Status:** Persistence preparation is implemented on `copilot/persistent-database-migration`; it is not merged or deployed. No database was provisioned and no billable service was enabled.

## Current baseline

- Render runs the `sociomart-demo` web service with `SPRING_PROFILES_ACTIVE=demo`.
- The demo uses in-memory H2 and reseeds on application startup. Any non-seed runtime data is ephemeral and cannot be assumed to survive a restart or redeploy.
- The public H2 Console reports that remote connections are disabled. There is no complete read-only export endpoint, so the current live database has not been safely snapshotted.
- The branch now contains a separate `postgres-demo` profile, PostgreSQL/Flyway dependencies, and an initial schema migration. The profile is not selected by Render.
- Authentication is demo-only. The demo login uses a mobile number and is not an identity system for real customers.
- OpenAPI/Swagger routes are permitted by the current security configuration and must be gated or disabled before production.

## Persistence-preparation implementation

- `postgres-demo` reads `SOCIOMART_DB_JDBC_URL`, `SOCIOMART_DB_USERNAME`, and `SOCIOMART_DB_PASSWORD`; it runs Flyway from `classpath:db/migration`, sets Hibernate to `validate`, disables H2 Console, and keeps demo login enabled.
- Its Flyway V1 migration creates the 23 mapped tables/join table, current declared indexes, unique constraints, and entity relationship foreign keys.
- Demo data seeders are not enabled in `postgres-demo`, preventing startup seed rows from being mixed into a restored dataset.
- The H2 PostgreSQL-mode migration test passes and Hibernate validated the full JPA schema against it.
- A Testcontainers PostgreSQL test is included for GitHub Actions. It was skipped locally because Docker is unavailable; native PostgreSQL execution is not yet verified.

This preparation does not create, connect, or migrate any database. It does not change Render settings.

## Free-tier PostgreSQL alternatives (checked 2026-10-09)

| Option | Published free allowance / behavior | Risks for this demo |
|---|---|---|
| **Neon Free — first candidate to evaluate** | 1 GB storage per project, 100 CU-hours/project/month, 5 GB network transfer/project/month; compute scales to zero after 5 minutes; compute/network quotas suspend service until reset, while stored data is not deleted by quota exhaustion. | External database traffic from Render; cold start after scale-to-zero; monthly compute/egress ceilings; no paid-plan SLA or support. Render warns that unusually high service-initiated external traffic can suspend a free web service. Must monitor quotas and take independent exports. |
| **Supabase Free** | 500 MB database; free projects can be paused after 7 days of low activity; paused projects can be restored for up to one year. Free plan has no automatic daily backup/PITR. | More restrictive storage; manual resume can interrupt the demo; exports/backups must be operated by the project owner. Supabase APIs/Auth/Storage are not needed by the current app. |
| **Render Free Postgres** | 1 GB storage, but database expires after 30 days, followed by a 14-day upgrade grace period; no automated backups. | Unsuitable as a lasting zero-budget database for data that must be retained. |

### Recommendation within the ₹0/month limit

**Neon Free is the most plausible zero-cost evaluation candidate**, because its documented free database has no 30-day expiration and its monthly compute quota suspension does not delete stored data. This is not equivalent to reliable/production hosting: an exhausted quota, cold start, network cap, provider outage, or account-policy change can make the app unavailable. Supabase Free is a secondary option if its larger platform feature set is desired, but its 500 MB limit and inactivity pause are less suitable for an always-accessible demo. Render Free Postgres is not recommended for preserved data because it expires after 30 days.

No free provider has been provisioned or connected. The current service remains unchanged until its live H2 data is safely exported and the owner approves the selected external provider and public-network database connection. Before connecting an external provider, verify Render's account spend controls so database egress cannot cause charges under the ₹0/month ceiling.

## Migration sequence

1. **Preserve data first.** Do not restart or redeploy the current H2-backed service. Its Console refuses remote connections and no full read-only dump route was found. Obtain a safe export from an owner-controlled live-process mechanism or an owner-provided export before continuing.
2. **Review V1 migration.** Verify every current model, FK, index, enum, identity sequence and date/time type on a native PostgreSQL instance. H2 compatibility is only a fallback check.
3. **Run GitHub CI.** The added Testcontainers PostgreSQL test should run on a Docker-enabled GitHub Actions runner and must pass before any deployment.
4. **Select a $0 provider only after review.** Compare its data retention, SLA, connection/egress limits, region, and backup/export tools. Do not use Render’s expiring free Postgres for data the user asked to preserve.
5. **Make a manual export and validate counts.** Keep the export outside Git, protect its PII, and do not put database credentials or row contents in logs or chat.
6. **Create/import the external database only after explicit provider approval.** Configure service secrets through the dashboard; do not commit JDBC credentials.
7. **Cut over only after comparison.** Compare row counts and important relationships, verify sequence counters and login/order flows, and have a rollback target before changing the existing Render service.

## Backup, recovery, and rollback

- Before any persistent cutover, take a complete export of the live H2 state and test importing/restoring it into an isolated PostgreSQL instance. No export has been captured because the public H2 Console refuses remote connections.
- Free PostgreSQL tiers do not provide the paid backup/PITR guarantees. Define a manual `pg_dump` export cadence and an owner-controlled backup destination before cutover; do not commit dumps or credentials.
- Test a restore from that manual export. If the free provider's retention/snapshot features are insufficient, remain on the current H2 demo until the owner explicitly approves a paid plan.
- Use backward-compatible, additive migrations during rollout. Keep the prior application image available for rollback.
- If rollback follows writes on the new schema, restore to a separate database and validate it before switching the service. Do not drop the migrated database or overwrite it with an H2 seed.
- The current in-memory H2 configuration has no durable snapshot to restore after a process restart. If non-seed data is still present in a live process, it must be exported before that process is restarted; this plan does not authorize such an export or migration.

## Cost and capacity under the ₹0/month constraint

The approved operating ceiling is **₹0/month**. Paid database/web plans, storage, backups, and billable add-ons are out of scope unless separately approved. Free usage is quota-bound; an external free Postgres database may also add cold-start latency and internet-egress dependence to the Render free web service.

Sources checked on 2026-10-09: [Neon Free limits](https://neon.com/faqs/free-plan-limits-and-quotas), [Supabase billing quotas](https://supabase.com/docs/guides/platform/billing-on-supabase), [Supabase inactivity pause](https://supabase.com/docs/guides/platform/free-project-pausing), [Supabase backup policy](https://supabase.com/docs/guides/platform/backups), and [Render free limits](https://render.com/docs/free).

## Approval required before implementation

1. Provide or authorize an owner-controlled, complete H2 export path that does not restart the current service; preserve non-seed data as instructed.
2. Review the free-tier comparison and explicitly select a provider before any external database project is created or service environment is changed.
3. Approve how periodic exports and restoration will be handled on the selected free tier.
4. Approve a migration window and rollback owner.
5. Approve a production identity design separately; demo authentication must not be presented as production authentication.

Until these requirements are satisfied, keep the current service on its existing H2 demo configuration. Do not migrate, reset, restart, change environment variables, or activate an external database.
