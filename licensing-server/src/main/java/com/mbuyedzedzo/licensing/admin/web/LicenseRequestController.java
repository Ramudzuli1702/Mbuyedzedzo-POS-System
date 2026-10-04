package com.mbuyedzedzo.licensing.admin.web;

import com.mbuyedzedzo.licensing.admin.AdminPrincipal;
import com.mbuyedzedzo.licensing.admin.LicenseRequestAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/requests")
public class LicenseRequestController {

    private final LicenseRequestAdminService service;

    public LicenseRequestController(LicenseRequestAdminService service) {
        this.service = service;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("items", service.listPending());
        return "requests/list";
    }

    @PostMapping("/{id}/approve")
    String approve(@PathVariable long id, @RequestParam(defaultValue = "1") int maxMachines,
                    @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        try {
            service.approve(me, id, maxMachines);
            ra.addFlashAttribute("flash", "Licence issued and emailed to the requester.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/requests";
    }

    @PostMapping("/{id}/reject")
    String reject(@PathVariable long id, @RequestParam(required = false) String reason,
                  @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        try {
            service.reject(me, id, reason);
            ra.addFlashAttribute("flash", "Request rejected.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/requests";
    }
}
