# SocioMart Production Readiness Plan

**Status:** Proposal only. This plan does not change the running demo or provision infrastructure.

## Current baseline

- Render runs the `sociomart-demo` web service with `SPRING_PROFILES_ACTIVE=demo`.
- The demo uses in-memory H2 and reseeds on application startup. Any non-seed runtime data is ephemeral and cannot be assumed to survive a restart or redeploy.
- The production properties file also uses local H2 and `ddl-auto=validate`; there is no checked-in schema migration or PostgreSQL driver.
- Authentication is demo-only. The demo login uses a mobile number and is not an identity system for real customers.
- OpenAPI/Swagger routes are permitted by the current security configuration and must be gated or disabled before production.

## Recommended target

Use **managed PostgreSQL** for a persistent pilot. It fits the existing relational JPA model, foreign-key relationships, transactions, uniqueness constraints, and occurrence-level inventory locking. Introduce schema ownership through Flyway (or another explicitly selected migration tool) and set Hibernate to `validate` after migrations have created the schema.

This is a recommendation, not an approval to create a database or change the Render service.

## Migration sequence

1. **Decide what data must survive.** The live demo currently runs in-memory H2. Before any further restart/cutover, the service owner must decide whether the target should start from the documented demo seed or preserve the currently running instance's non-seed data. Do not assume that a redeploy or re-seed is acceptable.
2. **Model the schema.** Create an initial versioned migration for the current entities, indexes, unique constraints, and foreign keys. Review identity/sequence behavior, enum values, date/time storage, and lazy relationship constraints against PostgreSQL.
3. **Keep migration and application deployment separate.** Add the PostgreSQL JDBC driver and migration dependency in a feature PR. Apply additive migrations first; configure the application to validate the migrated schema rather than create/alter it automatically.
4. **Test on an isolated PostgreSQL instance.** Run a fresh-schema migration, upgrade-path tests, persistence/rollback checks, and recurring occurrence/order tests on PostgreSQL. Keep H2 tests for fast feedback, but do not treat H2 as proof of PostgreSQL compatibility.
5. **Provision only after approval.** Create a managed database in the same Render region as the web service. Configure the connection through Render environment variables or a service-linked environment group; never commit credentials or paste them into chat.
6. **Cut over with a verified dataset.** If seed-only is approved, load the controlled demo seed. If data must be preserved, export and validate it before cutover, compare entity/relationship counts, and run non-mutating smoke checks before switching traffic.
7. **Set safe production schema behavior.** Use migrations for schema changes and `spring.jpa.hibernate.ddl-auto=validate`. Keep demo seeding and demo login disabled for any future real-customer profile.

## Backup, recovery, and rollback

- Before a persistent cutover, take a database snapshot/export and test restoring it into a separate instance.
- Prefer a paid database plan with point-in-time recovery and logical backups; define retention and periodically test restoration.
- Use backward-compatible, additive migrations during rollout. Keep the prior application image available for rollback.
- If rollback follows writes on the new schema, restore to a separate database and validate it before switching the service. Do not drop the migrated database or overwrite it with an H2 seed.
- The current in-memory H2 configuration has no durable snapshot to restore after a process restart. If non-seed data is still present in a live process, it must be exported before that process is restarted; this plan does not authorize such an export or migration.

## Capacity and indicative Render cost

Official Render pricing and free-tier documentation were checked on **2026-10-09**:

- Free Postgres includes 1 GB but expires after 30 days, has a 14-day upgrade grace period, and does not provide backups; it is unsuitable for long-lived pilot data.
- The listed smallest paid Postgres option is **$6/month** (256 MB RAM, 100 connections, 1 GB included storage). The listed 1 GB RAM tier is **$19/month**. Additional Postgres storage is listed at **$0.30/GB/month**.
- The listed smallest paid web-service compute is **$7/month** (512 MB RAM). A minimal paid web service plus the smallest paid Postgres tier is therefore about **$13/month**, before additional storage, bandwidth, any workspace-plan fee, and taxes. A 1 GB RAM Postgres tier plus that web tier is about **$26/month** before those additions.
- For the stated pilot target (about 100 buyers, 50 sellers, 100 orders/day, and 100 active menu items), 1 GB is only a starting capacity, not a validated sizing recommendation. No load test, row-size measurement, image-storage measurement, or retention analysis has been performed. Measure order items, analytics/audit retention, indexes, and backups before choosing a paid tier.

Sources: [Render pricing](https://render.com/pricing.md), [Render free-instance limitations](https://render.com/docs/free), and [Render Postgres backups and recovery](https://render.com/docs/postgresql-backups). Pricing and plan limits may change; verify them in the account before provisioning.

## Approval required before implementation

1. Confirm whether non-seed data in the live demo must be retained or whether a fresh demo seed is acceptable.
2. Approve a database provider/plan and recurring budget; the indicative minimum above is not a capacity guarantee.
3. Authorize creation of the managed database and its Render service-linked secret configuration.
4. Approve a migration window and rollback owner.
5. Approve a production identity design separately; demo authentication must not be presented as production authentication.

Until those decisions and resources exist, keep the current service demo-only. Do not silently migrate, reset, or change its environment variables.
