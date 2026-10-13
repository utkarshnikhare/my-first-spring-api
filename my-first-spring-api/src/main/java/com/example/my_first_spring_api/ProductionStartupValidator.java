package com.example.my_first_spring_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Stops the application from booting in production without admin credentials. */
@Component
@Profile("prod")
public class ProductionStartupValidator {

    @Autowired
    public ProductionStartupValidator(Environment environment) {
        AuthSettings.requireAdminPasswords(environment);
    }
}
