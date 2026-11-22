package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=false",
        "spring.datasource.url=jdbc:h2:mem:adminexporttest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
public class AdminExportTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticatedExportRequestIsDenied() throws Exception {
        mvc.perform(get("/api/admin/exports/orders.csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void buyerRoleExportRequestIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/exports/orders.csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void sellerRoleExportRequestIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/exports/orders.csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportOrdersCsvHasCorrectHeadersAndStructure() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/exports/orders.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sociomart-admin-orders.csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("# SocioMart Admin V1 - Orders export");
        assertThat(body).contains("# Generated at ");
        assertThat(body).contains("Order ID,Order Number,Placed At,Buyer,Buyer Mobile,Seller,Storefront,Category,Area,Society,Building,Flat,Items,Quantity,Recorded Order Value,Payment,Delivery,Delivered At,Order Status");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportSellersCsvHasCorrectHeadersAndStructure() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/exports/sellers.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sociomart-admin-sellers.csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("# SocioMart Admin V1 - Sellers export");
        assertThat(body).contains("# Generated at ");
        assertThat(body).contains("Seller ID,Name,Mobile,Approval Status,Status Reason,Approved At,Registered At,Storefronts,Orders,Recorded Order Value");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportBuyersCsvHasCorrectHeadersAndStructure() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/exports/buyers.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sociomart-admin-buyers.csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("# SocioMart Admin V1 - Buyers export");
        assertThat(body).contains("# Generated at ");
        assertThat(body).contains("Buyer ID,Name,Mobile,Area,Society,Building,Flat,Orders,Recorded Order Value");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportAnalyticsCsvHasCorrectHeadersAndStructure() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/exports/analytics.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sociomart-admin-analytics.csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("# SocioMart Admin V1 - Analytics export");
        assertThat(body).contains("# Generated at ");
        assertThat(body).contains("Metric,Value");
        assertThat(body).contains("totalBuyers");
        assertThat(body).contains("totalSellers");
        assertThat(body).contains("totalOrders");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownExportDomainReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/exports/unsupported_domain.csv"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyDatasetRendersValidCsvWithoutErrors() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/exports/orders.csv")
                        .param("status", "CANCELLED")
                        .param("date", "1999-01-01"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sociomart-admin-orders.csv"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("# Filters applied:");
        assertThat(body).contains("date=1999-01-01");
        assertThat(body).contains("status=CANCELLED");
        assertThat(body).contains("Order ID,Order Number,Placed At");
    }

    @Test
    void formulaInjectionIsDetectedAndNeutralized() {
        assertThat(AdminService.isFormulaLike("=SUM(A1:A10)")).isTrue();
        assertThat(AdminService.isFormulaLike("+cmd|' /C calc'!A0")).isTrue();
        assertThat(AdminService.isFormulaLike("-12345")).isTrue();
        assertThat(AdminService.isFormulaLike("@echo")).isTrue();
        assertThat(AdminService.isFormulaLike("Normal Text")).isFalse();
    }
}
