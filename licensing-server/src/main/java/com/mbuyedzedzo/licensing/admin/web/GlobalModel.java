package com.mbuyedzedzo.licensing.admin.web;

import com.mbuyedzedzo.licensing.admin.AdminPrincipal;
import com.mbuyedzedzo.licensing.admin.LicenseRequestAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Attributes every admin view needs: the current user and request path. */
@ControllerAdvice(basePackages = "com.mbuyedzedzo.licensing.admin.web")
class GlobalModel {

    private final LicenseRequestAdminService licenseRequests;

    GlobalModel(LicenseRequestAdminService licenseRequests) {
        this.licenseRequests = licenseRequests;
    }

    @ModelAttribute("me")
    AdminPrincipal me(@AuthenticationPrincipal AdminPrincipal principal) {
        return principal;
    }

    @ModelAttribute("uri")
    String uri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("pendingLicenseRequests")
    long pendingLicenseRequests() {
        return licenseRequests.countPending();
    }
}
