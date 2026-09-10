package com.mvelelo.licensing.admin.web;

import com.mvelelo.licensing.admin.AdminPrincipal;
import com.mvelelo.licensing.admin.DashboardService;
import com.mvelelo.licensing.repo.AuditLogRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private final DashboardService dashboard;
    private final AuditLogRepo audit;

    public AdminController(DashboardService dashboard, AuditLogRepo audit) {
        this.dashboard = dashboard;
        this.audit = audit;
    }

    @GetMapping("/")
    String root() {
        return "redirect:/admin";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/admin")
    String dashboard(Model model, @AuthenticationPrincipal AdminPrincipal me) {
        model.addAttribute("me", me);
        model.addAttribute("stats", dashboard.stats());
        return "dashboard";
    }

    @GetMapping("/admin/audit")
    String audit(Model model, @AuthenticationPrincipal AdminPrincipal me,
                 @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("me", me);
        model.addAttribute("entries", audit.findAllByOrderByAtDesc(PageRequest.of(page, 50)));
        return "audit/list";
    }
}
