package com.example.my_first_spring_api;

import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.service.AdminService;
import com.example.my_first_spring_api.service.DemoAccountSeeder;
import com.example.my_first_spring_api.service.FeatureService;
import com.example.my_first_spring_api.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Idempotent start-up bootstrap that every profile runs. It only ever ADDS what
 * is missing and never updates or deletes an existing row:
 * <ol>
 *   <li>the Super Admin and Admin accounts (mobiles configurable, credentials
 *       are environment-only and never stored here);</li>
 *   <li>the {@code preorders} and {@code menu_advance_days} feature flags;</li>
 *   <li>the real pilot area/society, only when no area exists at all.</li>
 * </ol>
 * It creates no sellers, kitchens, products, buyers, orders or any other
 * sample data. Switch it off with {@code sociomart.bootstrap.enabled=false}
 * (the automated tests do, to start from an empty database).
 */
@Component
@ConditionalOnProperty(name = "sociomart.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class ProductionBootstrap implements ApplicationRunner {

    public static final String PILOT_AREA = "Charholi / Lohegaon";
    public static final String PILOT_SOCIETY = "Pride World City";

    private static final Logger log = LoggerFactory.getLogger(ProductionBootstrap.class);

    private final AdminService adminService;
    private final FeatureService featureService;
    private final LocationService locationService;
    private final AreaRepository areaRepository;
    private final String superAdminMobile;
    private final String adminMobile;

    private final DemoAccountSeeder demoAccountSeeder;

    public ProductionBootstrap(AdminService adminService, FeatureService featureService,
                               LocationService locationService, AreaRepository areaRepository,
                               DemoAccountSeeder demoAccountSeeder,
                               @Value("${sociomart.bootstrap.super-admin-mobile:" + AdminService.SUPER_ADMIN_MOBILE + "}")
                               String superAdminMobile,
                               @Value("${sociomart.bootstrap.admin-mobile:" + AdminService.ADMIN_MOBILE + "}")
                               String adminMobile) {
        this.adminService = adminService;
        this.featureService = featureService;
        this.locationService = locationService;
        this.areaRepository = areaRepository;
        this.demoAccountSeeder = demoAccountSeeder;
        this.superAdminMobile = superAdminMobile.trim();
        this.adminMobile = adminMobile.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    public void bootstrap() {
        adminService.ensureBootstrapAccounts(superAdminMobile, adminMobile);
        featureService.ensureDefaults();
        if (areaRepository.count() == 0) {
            var area = locationService.createArea(PILOT_AREA);
            locationService.createSociety(area.getId(), PILOT_SOCIETY);
            log.info("Created the pilot area and society (no areas existed)");
        }
        if (demoAccountSeeder != null) {
            demoAccountSeeder.seedDemoAccounts();
        }
    }
}
