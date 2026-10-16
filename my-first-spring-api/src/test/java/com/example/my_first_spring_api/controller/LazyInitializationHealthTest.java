package com.example.my_first_spring_api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the production startup strategy: {@code application-prod.properties} enables
 * {@code spring.main.lazy-initialization=true} so Render can reach {@code /api/health}
 * before every bean is created (the Render free tier kills instances that do not become
 * healthy fast enough, causing a restart loop).
 *
 * <p>Lazy initialization can defer a wiring error until the first request, so this boots a
 * full component-scanned context with lazy initialization ON and asserts that the very
 * first request - the health check Render uses to decide whether the instance is alive -
 * still resolves, returns HTTP 200, and stays public. If a future change makes the health
 * path depend on a heavyweight bean, this test is where it fails.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lazy-init-health-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.main.lazy-initialization=true"
})
@ActiveProfiles("test")
class LazyInitializationHealthTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void firstRequestUnderLazyInitializationServesTheRenderHealthCheck() throws Exception {
        mockMvc().perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private MockMvc mockMvc() {
        return webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }
}
