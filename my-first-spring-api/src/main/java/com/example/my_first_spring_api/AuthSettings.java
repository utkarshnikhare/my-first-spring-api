package com.example.my_first_spring_api;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Single reading point for the authentication settings.
 *
 * <p>Admin and Super Admin passwords come only from the environment. The
 * property keys below are fed by {@code SOCIOMART_ADMIN_PASSWORD} /
 * {@code SOCIOMART_SUPER_ADMIN_PASSWORD}, falling back to the older
 * {@code SOCIOMART_DEMO_ADMIN_PASSWORD} / {@code SOCIOMART_DEMO_SUPER_ADMIN_PASSWORD}
 * names already configured on existing deployments (see application.properties).
 * Values are never logged.</p>
 */
public final class AuthSettings {

    public static final String PASSWORD_LOGIN_ENABLED = "sociomart.auth.password-login-enabled";
    public static final String ADMIN_PASSWORD = "sociomart.auth.admin-password";
    public static final String SUPER_ADMIN_PASSWORD = "sociomart.auth.super-admin-password";

    private AuthSettings() {
    }

    /** Registration and mobile+password login. On by default in every profile. */
    public static boolean passwordLoginEnabled(Environment environment) {
        return environment.getProperty(PASSWORD_LOGIN_ENABLED, Boolean.class, true);
    }

    public static boolean adminPasswordConfigured(Environment environment) {
        return StringUtils.hasText(environment.getProperty(ADMIN_PASSWORD));
    }

    public static boolean superAdminPasswordConfigured(Environment environment) {
        return StringUtils.hasText(environment.getProperty(SUPER_ADMIN_PASSWORD));
    }

    /**
     * Production must not start without both admin credentials. The message names
     * the environment variables only, never a value.
     */
    public static void requireAdminPasswords(Environment environment) {
        boolean admin = adminPasswordConfigured(environment);
        boolean superAdmin = superAdminPasswordConfigured(environment);
        if (admin && superAdmin) return;
        StringBuilder missing = new StringBuilder();
        if (!admin) missing.append("SOCIOMART_ADMIN_PASSWORD (or legacy SOCIOMART_DEMO_ADMIN_PASSWORD)");
        if (!superAdmin) {
            if (missing.length() > 0) missing.append(" and ");
            missing.append("SOCIOMART_SUPER_ADMIN_PASSWORD (or legacy SOCIOMART_DEMO_SUPER_ADMIN_PASSWORD)");
        }
        throw new IllegalStateException("Refusing to start in production: " + missing
                + " must be set to a non-blank value.");
    }
}
