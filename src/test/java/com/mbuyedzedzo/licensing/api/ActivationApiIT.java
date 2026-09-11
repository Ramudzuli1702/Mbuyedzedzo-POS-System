package com.mbuyedzedzo.licensing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mbuyedzedzo.licensing.domain.License;
import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Product;
import com.mbuyedzedzo.licensing.license.LicenseAdminService;
import com.mbuyedzedzo.licensing.repo.ActivationRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end activation API test against a real MySQL. Supply a dedicated
 * schema via system properties, e.g.:
 *
 *   mvn -Dlicensing.it.jdbcUrl=jdbc:mysql://localhost:3306/mbuyedzedzo_licensing_test \
 *       -Dlicensing.it.user=root -Dlicensing.it.password=secret test
 *
 * Skipped entirely when licensing.it.jdbcUrl is not set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "licensing.it.jdbcUrl", matches = ".+")
class ActivationApiIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      () -> System.getProperty("licensing.it.jdbcUrl"));
        r.add("spring.datasource.username", () -> System.getProperty("licensing.it.user", "root"));
        r.add("spring.datasource.password", () -> System.getProperty("licensing.it.password", ""));
        r.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired LicenseAdminService admin;
    @Autowired LicenseRepo licenses;
    @Autowired ActivationRepo activations;
    @Autowired ObjectMapper json;

    private License lic;

    @BeforeEach
    void seed() {
        activations.deleteAll();
        licenses.deleteAll();
        lic = admin.issue(Product.POS_STANDARD, LicenseType.PERPETUAL, 1,
                null, null, null, "test", "test");
    }

    private String body(Object o) throws Exception { return json.writeValueAsString(o); }

    @Test
    void activateThenValidate_roundTrips() throws Exception {
        String activate = mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest(lic.getLicenseKey(), "fp-1", "Till 1", Product.POS_STANDARD, "1.0.0"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.licenseStatus").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode tok = json.readTree(activate);
        assertFalse(tok.get("token").asText().isBlank());

        mvc.perform(post("/api/v1/validate").contentType("application/json")
                .content(body(new ValidateRequest(lic.getLicenseKey(), "fp-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void reactivatingSameMachineIsIdempotent() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/activate").contentType("application/json")
                    .content(body(new ActivateRequest(lic.getLicenseKey(), "fp-1", "Till 1", Product.POS_STANDARD, "1.0.0"))))
                    .andExpect(status().isOk());
        }
        assertEquals(1, activations.countByLicenseIdAndActiveTrue(lic.getId()));
    }

    @Test
    void secondMachineHitsTheLimit() throws Exception {
        mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest(lic.getLicenseKey(), "fp-1", null, Product.POS_STANDARD, "1.0.0"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest(lic.getLicenseKey(), "fp-2", null, Product.POS_STANDARD, "1.0.0"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MACHINE_LIMIT"));
    }

    @Test
    void wrongProductIsRejected() throws Exception {
        mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest(lic.getLicenseKey(), "fp-1", null, Product.POS_RETAIL, "1.0.0"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_PRODUCT"));
    }

    @Test
    void badChecksumIsRejectedBeforeLookup() throws Exception {
        mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest("POS-2026-AAAA-BBBB-CCCC", "fp-1", null, Product.POS_STANDARD, "1.0.0"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BAD_CHECKSUM"));
    }

    @Test
    void revokedKeyIsBlocked() throws Exception {
        admin.revoke(lic.getId(), "test", "test");
        mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest(lic.getLicenseKey(), "fp-1", null, Product.POS_STANDARD, "1.0.0"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVOKED"));
    }

    @Test
    void expiredKeyIsBlockedAndMarkedExpired() throws Exception {
        License sub = admin.issue(Product.POS_STANDARD, LicenseType.SUBSCRIPTION, 1,
                Instant.now().minus(1, ChronoUnit.DAYS), null, null, "old", "test");
        mvc.perform(post("/api/v1/activate").contentType("application/json")
                .content(body(new ActivateRequest(sub.getLicenseKey(), "fp-1", null, Product.POS_STANDARD, "1.0.0"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXPIRED"));
        assertEquals("EXPIRED", licenses.findById(sub.getId()).orElseThrow().getStatus().name());
    }

    @Test
    void trialCanBeStartedOncePerMachine() throws Exception {
        mvc.perform(post("/api/v1/trial").contentType("application/json")
                .content(body(new TrialRequest(Product.POS_STANDARD, "fp-trial", "Laptop", "1.0.0"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseType").value("TRIAL"))
                .andExpect(jsonPath("$.licenseExpiresAt").isNotEmpty());

        mvc.perform(post("/api/v1/trial").contentType("application/json")
                .content(body(new TrialRequest(Product.POS_STANDARD, "fp-trial", "Laptop", "1.0.0"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIAL_ALREADY_USED"));
    }
}
