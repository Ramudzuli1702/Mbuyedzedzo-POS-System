package com.mvelelo.licensing.admin.web;

import com.mvelelo.licensing.admin.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Attributes every admin view needs: the current user and request path. */
@ControllerAdvice(basePackages = "com.mvelelo.licensing.admin.web")
class GlobalModel {

    @ModelAttribute("me")
    AdminPrincipal me(@AuthenticationPrincipal AdminPrincipal principal) {
        return principal;
    }

    @ModelAttribute("uri")
    String uri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
