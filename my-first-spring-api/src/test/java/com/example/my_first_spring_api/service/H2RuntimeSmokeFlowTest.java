package com.example.my_first_spring_api.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The same marketplace flow on in-memory H2, so it also runs where Docker is unavailable. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:runtime-smoke;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "sociomart.auth.password-login-enabled=true"
})
@ActiveProfiles("test")
class H2RuntimeSmokeFlowTest extends AbstractRuntimeSmokeFlow {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("sociomart.auth.admin-password", () -> ADMIN_PASSWORD);
        registry.add("sociomart.auth.super-admin-password", () -> SUPER_ADMIN_PASSWORD);
    }
}
