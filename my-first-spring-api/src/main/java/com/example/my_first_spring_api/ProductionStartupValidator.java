package com.example.my_first_spring_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Stops the application from booting in production without required credentials or database settings. */
@Component
@Profile("prod")
public class ProductionStartupValidator {

    @Autowired
    public ProductionStartupValidator(Environment environment) {
        // 1. Validate Administrative Passwords
        AuthSettings.requireAdminPasswords(environment);

        // 2. Validate Database Configuration
        validateDatabaseConfiguration(environment);
    }

    private void validateDatabaseConfiguration(Environment environment) {
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        StringBuilder errors = new StringBuilder();

        // Validate JDBC URL
        if (!StringUtils.hasText(jdbcUrl)) {
            errors.append("SOCIOMART_DB_JDBC_URL is missing or blank. ");
        } else if (jdbcUrl.contains("${SOCIOMART_DB_JDBC_URL}")) {
            errors.append("SOCIOMART_DB_JDBC_URL is unresolved (literal placeholder found). ");
        } else if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            errors.append("SOCIOMART_DB_JDBC_URL is not a valid PostgreSQL JDBC URL. ");
        }

        // Validate Username
        if (!StringUtils.hasText(username)) {
            errors.append("SOCIOMART_DB_USERNAME is missing or blank. ");
        } else if (username.contains("${SOCIOMART_DB_USERNAME}")) {
            errors.append("SOCIOMART_DB_USERNAME is unresolved. ");
        }

        // Validate Password
        if (!StringUtils.hasText(password)) {
            errors.append("SOCIOMART_DB_PASSWORD is missing or blank. ");
        } else if (password.contains("${SOCIOMART_DB_PASSWORD}")) {
            errors.append("SOCIOMART_DB_PASSWORD is unresolved. ");
        }

        if (errors.length() > 0) {
            throw new IllegalStateException("Production database configuration is invalid: " + errors.toString().trim()
                    + "\nConfigure the required PostgreSQL environment variables in the Render service environment.");
        }
    }
}
