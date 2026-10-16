-- V9__add_email_to_users_if_missing.sql
-- Ensures the `email` column exists on users.
--
-- V7__add_email_to_users.sql added this column, but production was deployed
-- before V7 ran against its database, so the column is missing there.
-- This migration is idempotent (IF NOT EXISTS): if V7 already applied, it is
-- a no-op. Adding a nullable column does not affect existing rows.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR;