package com.example.my_first_spring_api;

import com.example.my_first_spring_api.service.AdminService;
import com.example.my_first_spring_api.service.FeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * One-time platform bootstrap:
 *  - creates the built-in Super Admin and Admin accounts (OTP login),
 *  - grandfathers sellers that existed before the approval workflow,
 *  - seeds the initial seller feature catalogue.
 * Every step is idempotent and safe to run on every startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminService adminService;
    private final FeatureService featureService;

    @Autowired
    public DataInitializer(AdminService adminService, FeatureService featureService) {
        this.adminService = adminService;
        this.featureService = featureService;
    }

    @Override
    public void run(String... args) {
        adminService.ensureBootstrapAccounts();
        adminService.approveLegacySellers();
        featureService.ensureDefaults();
    }
}
