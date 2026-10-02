package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Society;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin System Health screen.
 *
 * <p>The screen must report only what the running application can actually prove.
 * Two properties matter and are pinned here: the demo-login flag is read through the
 * very same helper Spring Security uses, so the screen can never disagree with the
 * real gate; and "database reachable" is the result of a real JPA round trip rather
 * than a hard-coded "OK".</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-health-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminSystemHealthTest {

    @Autowired private AdminService adminService;
    @Autowired private LocationService locationService;
    @Autowired private org.springframework.core.env.Environment environment;

    @SuppressWarnings("unchecked")
    @Test
    void healthReportsTheRealProfileAndDemoGate() {
        Map<String, Object> health = adminService.systemHealth();

        List<String> profiles = (List<String>) health.get("activeProfiles");
        assertThat(profiles).isNotEmpty();
        assertThat(profiles).contains("test");

        // The flag must be DERIVED from the very helper Spring Security uses, so the
        // screen can never disagree with the real gate. Asserting equality against that
        // helper is the real contract; the literal below just documents the outcome for
        // the "test" profile, which is not one of demo/dev/default and so is not a demo
        // environment. (It used to read as true, which would have been a wrong
        // expectation rather than a wrong implementation.)
        assertThat(health.get("demoLoginEnabled"))
                .isEqualTo(com.example.my_first_spring_api.SecurityConfig.isDemoEnvironment(environment));
        assertThat(health.get("demoLoginEnabled")).isEqualTo(Boolean.FALSE);
    }

    @SuppressWarnings("unchecked")
    @Test
    void databaseReachabilityComesFromARealQuery() {
        Map<String, Object> health = adminService.systemHealth();
        Map<String, Object> database = (Map<String, Object>) health.get("database");

        assertThat(database.get("reachable")).isEqualTo(Boolean.TRUE);
        assertThat(database).containsKeys("users", "kitchens", "orders");
        assertThat((Long) database.get("users")).isNotNegative();
    }

    @SuppressWarnings("unchecked")
    @Test
    void locationMasterCountsMatchTheActualMaster() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        Area area = locationService.createArea("Health Area " + sfx);
        locationService.createSociety(area.getId(), "Health Society " + sfx);
        locationService.createSociety(area.getId(), "Health Society 2 " + sfx);

        Map<String, Object> health = adminService.systemHealth();
        Map<String, Object> master = (Map<String, Object>) health.get("locationMaster");

        assertThat(master).containsKeys("areas", "societies");
        assertThat((Long) master.get("areas")).isGreaterThanOrEqualTo(1L);
        assertThat((Long) master.get("societies")).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void healthIsReadOnlyAndRepeatable() {
        Map<String, Object> first = adminService.systemHealth();
        Map<String, Object> second = adminService.systemHealth();

        assertThat(second).isEqualTo(first);
        assertThat(first).containsKeys("application", "activeProfiles", "demoLoginEnabled",
                "database", "locationMaster");
    }
}
