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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public-health-endpoint-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class PublicHealthEndpointSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void healthEndpointIsPublicAndReportsApplicationUp() throws Exception {
        mockMvc().perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void administrativeEndpointsRemainProtected() throws Exception {
        mockMvc().perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    private MockMvc mockMvc() {
        return webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }
}
