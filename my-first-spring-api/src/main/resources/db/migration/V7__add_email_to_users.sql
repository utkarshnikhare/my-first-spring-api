-- V7__add_email_to_users.sql
-- Adds the nullable `email` column required by the forgot-password flow
-- (User.email). Flyway applies this BEFORE Hibernate's ddl-auto=validate runs,
-- so the schema drift is caught by the migration, not by Hibernate.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR;