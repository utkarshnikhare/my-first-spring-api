-- Phase E: persistent HTTP sessions (Spring Session JDBC).
-- Sessions move out of the servlet container's memory into the database, so an
-- app restart/redeploy no longer logs every user out. Tables and indexes are
-- copied VERBATIM from the schema published by spring-session-jdbc (the exact
-- dependency version is managed by the Spring Boot parent in the pom):
--   org/springframework/session/jdbc/schema-postgresql.sql
-- Spring Session only auto-creates these tables on EMBEDDED databases
-- (spring.session.jdbc.initialize-schema defaults to "embedded"); on
-- PostgreSQL Flyway owns them, exactly like the business tables above, and
-- Hibernate stays on ddl-auto=validate (these are not JPA entities).
-- The FK and indexes below are also how JdbcIndexedSessionRepository finds a
-- session, expires it and resolves sessions by principal name.
-- NOTE: BYTEA is valid PostgreSQL and is accepted by H2 as well (verified
-- against the project's H2 version, plain and MODE=PostgreSQL), so this one
-- migration runs unchanged on production PostgreSQL and the flyway-enabled H2
-- tests.

CREATE TABLE IF NOT EXISTS SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
