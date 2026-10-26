package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public-homemade-routing-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class PublicHomemadeRoutingSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void homemadeHubIsPubliclyAccessibleWithoutAuth() throws Exception {
        mockMvc().perform(get("/homemade"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void homemadeStoreBySlugIsPubliclyAccessibleWithoutAuth() throws Exception {
        mockMvc().perform(get("/homemade-store/meena-cakes"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void productDtoCorrectlyIdentifiesHomemadeItems() {
        ProductDto p1 = new ProductDto();
        p1.setCategory("HOMEMADE,SNACKS");
        assertThat(p1.getIsHomemade()).isTrue();

        ProductDto p2 = new ProductDto();
        p2.setCategory("MEAL");
        assertThat(p2.getIsHomemade()).isFalse();

        ProductDto p3 = new ProductDto();
        p3.setIsHomemade(true);
        assertThat(p3.getIsHomemade()).isTrue();
    }
}
