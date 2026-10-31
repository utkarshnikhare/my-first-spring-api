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
                SELECT table_name
                FROM information_schema.tables
                WHERE LOWER(table_name) = 'orders'
                  AND LOWER(table_schema) = LOWER(?)
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) tableName = result.getString(1);
            }
        }
        if (tableName == null) {
            throw new java.sql.SQLException("The orders table is required to update payment-status constraints.");
        }

        List<String> paymentStatusConstraints = new ArrayList<>();
        String findConstraints = """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON tc.constraint_catalog = cc.constraint_catalog
                 AND tc.constraint_schema = cc.constraint_schema
                 AND tc.constraint_name = cc.constraint_name
                WHERE LOWER(tc.table_name) = 'orders'
                  AND LOWER(tc.constraint_type) = 'check'
                  AND LOWER(cc.check_clause) LIKE '%payment_status%'
                  AND LOWER(tc.table_schema) = LOWER(?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(findConstraints)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) paymentStatusConstraints.add(result.getString(1));
            }
        }

        String quote = connection.getMetaData().getIdentifierQuoteString().trim();
        String table = quoteIdentifier(quote, tableName);
        try (Statement statement = connection.createStatement()) {
            for (String constraint : paymentStatusConstraints) {
                statement.execute("ALTER TABLE " + table + " DROP CONSTRAINT "
                        + quoteIdentifier(quote, constraint));
            }
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT "
                    + quoteIdentifier(quote, "orders_payment_status_check")
                    + " CHECK (payment_status IN ('PENDING', 'UNPAID', "
                    + "'AWAITING_SELLER_VERIFICATION', 'PAID', 'WILL_PAY_LATER'))");
        }
    }

    private static String quoteIdentifier(String quote, String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
