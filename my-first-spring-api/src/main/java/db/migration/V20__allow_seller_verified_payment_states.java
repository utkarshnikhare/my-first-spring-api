package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class V20__allow_seller_verified_payment_states extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String schema = connection.getSchema();
        if (schema == null || schema.isBlank()) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT current_schema()")) {
                if (result.next()) schema = result.getString(1);
            }
        }

        String tableName = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE LOWER(table_name) = 'orders'
                  AND LOWER(table_schema) = LOWER(?)
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    schema = result.getString(1);
                    tableName = result.getString(2);
                }
            }
        }
        if (tableName == null) {
            throw new java.sql.SQLException("The orders table is required to update payment-status constraints.");
        }

        List<String> paymentStatusConstraints = new ArrayList<>();
        String databaseProduct = connection.getMetaData().getDatabaseProductName();
        String findConstraints = "PostgreSQL".equalsIgnoreCase(databaseProduct)
                ? """
                  SELECT con.conname
                  FROM pg_constraint con
                  JOIN pg_class tbl ON tbl.oid = con.conrelid
                  JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
                  WHERE LOWER(tbl.relname) = LOWER(?)
                    AND LOWER(ns.nspname) = LOWER(?)
                    AND con.contype = 'c'
                    AND LOWER(pg_get_constraintdef(con.oid)) LIKE '%payment_status%'
                    AND LOWER(pg_get_constraintdef(con.oid)) NOT LIKE '%is not null%'
                    AND (
                      (
                        LOWER(pg_get_constraintdef(con.oid)) LIKE '%pending%'
                        AND LOWER(pg_get_constraintdef(con.oid)) LIKE '%paid%'
                        AND LOWER(pg_get_constraintdef(con.oid)) LIKE '%will_pay_later%'
                        AND LOWER(pg_get_constraintdef(con.oid)) NOT LIKE '%unpaid%'
                        AND LOWER(pg_get_constraintdef(con.oid)) NOT LIKE '%awaiting_seller_verification%'
                      )
                      OR LOWER(con.conname) = 'orders_payment_status_check'
                    )
                  """
                : """
                  SELECT tc.constraint_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.check_constraints cc
                    ON tc.constraint_catalog = cc.constraint_catalog
                   AND tc.constraint_schema = cc.constraint_schema
                   AND tc.constraint_name = cc.constraint_name
                  WHERE LOWER(tc.table_name) = 'orders'
                    AND LOWER(tc.constraint_type) = 'check'
                    AND LOWER(cc.check_clause) LIKE '%payment_status%'
                    AND LOWER(cc.check_clause) NOT LIKE '%is not null%'
                    AND (
                      (
                        LOWER(cc.check_clause) LIKE '%pending%'
                        AND LOWER(cc.check_clause) LIKE '%paid%'
                        AND LOWER(cc.check_clause) LIKE '%will_pay_later%'
                        AND LOWER(cc.check_clause) NOT LIKE '%unpaid%'
                        AND LOWER(cc.check_clause) NOT LIKE '%awaiting_seller_verification%'
                      )
                      OR LOWER(tc.constraint_name) = 'orders_payment_status_check'
                    )
                    AND LOWER(tc.table_schema) = LOWER(?)
                  """;
        try (PreparedStatement statement = connection.prepareStatement(findConstraints)) {
            if ("PostgreSQL".equalsIgnoreCase(databaseProduct)) {
                statement.setString(1, tableName);
                statement.setString(2, schema);
            } else {
                statement.setString(1, schema);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) paymentStatusConstraints.add(result.getString(1));
            }
        }

        String quote = connection.getMetaData().getIdentifierQuoteString();
        String safeQuote = (quote == null || quote.isBlank()) ? "" : quote.trim();
        String table = (schema != null && !schema.isBlank())
                ? quoteIdentifier(safeQuote, schema) + "." + quoteIdentifier(safeQuote, tableName)
                : quoteIdentifier(safeQuote, tableName);
        try (Statement statement = connection.createStatement()) {
            for (String constraint : paymentStatusConstraints) {
                statement.execute("ALTER TABLE " + table + " DROP CONSTRAINT "
                        + quoteIdentifier(safeQuote, constraint));
            }
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT "
                    + quoteIdentifier(safeQuote, "orders_payment_status_check")
                    + " CHECK (payment_status IN ('PENDING', 'UNPAID', "
                    + "'AWAITING_SELLER_VERIFICATION', 'PAID', 'WILL_PAY_LATER'))");
        }
    }

    private static String quoteIdentifier(String quote, String identifier) {
        if (quote == null || quote.isBlank()) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
