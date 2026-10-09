package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.admin.AdminUserDetailsService;
import com.mbuyedzedzo.licensing.admin.LicenseRequestAdminService;
import com.mbuyedzedzo.licensing.config.SecurityConfig;
import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Product;
import com.mbuyedzedzo.licensing.repo.LicenseRequestRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the public storefront templates, catching Thymeleaf fragment errors
 * that only surface at request time (a bad `th:replace` selector compiles
 * fine but 500s on render) — same pattern as AdminWebTest.
 */
@WebMvcTest(controllers = StorefrontController.class)
@Import(SecurityConfig.class)
class StorefrontWebTest {

    @Autowired MockMvc mvc;

    @MockBean CheckoutService checkout;
    @MockBean PricingService pricing;
    @MockBean AccountService accountService;
    @MockBean LicenseRequestRepo licenseRequests;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean CustomerUserDetailsService customerUserDetailsService;
    @MockBean LicenseRequestAdminService licenseRequestAdminService;

    @Test
    void homeRendersPricingAndDownloadSections() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"pricing\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"download\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"story\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("selected testing team")));
    }

    @Test
    void pricingPageRendersStandalone() throws Exception {
        when(pricing.priceFor(any(Product.class), any(LicenseType.class))).thenReturn(BigDecimal.TEN);
        mvc.perform(get("/pricing"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("One licence")));
    }

    @Test
    void downloadPageRendersStandalone() throws Exception {
        mvc.perform(get("/download"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Standard POS")));
    }

    @Test
    void testersPageRenders() throws Exception {
        mvc.perform(get("/testers"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("selected testing team")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Document what you see")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download as PDF")));
    }
}
