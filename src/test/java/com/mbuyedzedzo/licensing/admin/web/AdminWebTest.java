package com.mbuyedzedzo.licensing.admin.web;

import com.mbuyedzedzo.licensing.admin.*;
import com.mbuyedzedzo.licensing.config.SecurityConfig;
import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.license.LicenseAdminService;
import com.mbuyedzedzo.licensing.repo.AdminUserRepo;
import com.mbuyedzedzo.licensing.repo.AuditLogRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Renders the admin templates (catches Thymeleaf errors) and checks role gating. */
@WebMvcTest(controllers = {AdminController.class, CustomerController.class, LicenseController.class, AgentController.class})
@Import({SecurityConfig.class, Fmt.class})
class AdminWebTest {

    @Autowired MockMvc mvc;

    @MockBean DashboardService dashboard;
    @MockBean AuditLogRepo auditLog;
    @MockBean CustomerAdminService customers;
    @MockBean LicenseRepo licenseRepo;
    @MockBean LicenseQueryService licenseQuery;
    @MockBean LicenseAdminService licenseAdmin;
    @MockBean AgentService agents;
    @MockBean AdminUserDetailsService userDetailsService;
    @MockBean AdminUserRepo adminUserRepo;

    private static AdminPrincipal principal(Role role) {
        AdminUser u = new AdminUser();
        u.setId(1L); u.setEmail("me@test"); u.setFullName("Tester"); u.setRole(role); u.setActive(true);
        u.setPasswordHash("x");
        return new AdminPrincipal(u);
    }

    private static Page<?> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in")));
    }

    @Test
    void dashboardRendersForAgent() throws Exception {
        when(dashboard.stats()).thenReturn(
                new DashboardService.Stats(2, 1, 0, 5, 3, List.of()));
        mvc.perform(get("/admin").with(user(principal(Role.SALES_AGENT))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void licenseListRenders() throws Exception {
        when(licenseQuery.list(any(), any())).thenReturn((Page<License>) emptyPage());
        when(licenseQuery.customerNames(any())).thenReturn(java.util.Map.of());
        mvc.perform(get("/admin/licenses").with(user(principal(Role.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Generate key")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void licenseFormRenders() throws Exception {
        when(customers.list(any(), any(), any())).thenReturn((Page<Customer>) emptyPage());
        mvc.perform(get("/admin/licenses/new").with(user(principal(Role.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Generate license key")));
    }

    @Test
    void customerFormRenders() throws Exception {
        mvc.perform(get("/admin/customers/new").with(user(principal(Role.SALES_AGENT))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New customer")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void customerListRenders() throws Exception {
        when(customers.list(any(), any(), any())).thenReturn((Page<Customer>) emptyPage());
        mvc.perform(get("/admin/customers").with(user(principal(Role.SALES_AGENT))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New customer")));
    }

    @Test
    void licenseDetailRenders() throws Exception {
        License l = new License();
        l.setId(7L); l.setLicenseKey("POS-2026-XK29-MNQT-7R4B");
        l.setProduct(Product.POS_STANDARD); l.setType(LicenseType.PERPETUAL);
        l.setStatus(LicenseStatus.ACTIVE); l.setMaxMachines(2); l.setIssuedAt(Instant.now());
        when(licenseQuery.detail(any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(new LicenseQueryService.Detail(l, null, List.of(), List.of(), 1));
        mvc.perform(get("/admin/licenses/7").with(user(principal(Role.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reset machine binding")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Revoke")));
    }

    @Test
    void auditPageRenders() throws Exception {
        when(auditLog.findAllByOrderByAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/admin/audit").with(user(principal(Role.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Audit log")));
    }

    @Test
    void agentsPageIsSuperAdminOnly() throws Exception {
        when(agents.all()).thenReturn(List.of());
        mvc.perform(get("/admin/agents").with(user(principal(Role.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sales agents")));
        mvc.perform(get("/admin/agents").with(user(principal(Role.SALES_AGENT))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void agentCannotRevoke() throws Exception {
        mvc.perform(post("/admin/licenses/1/revoke").param("reason", "x")
                        .with(user(principal(Role.SALES_AGENT)))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}
