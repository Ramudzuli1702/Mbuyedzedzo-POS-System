package com.mbuyedzedzo.licensing.admin.web;

import com.mbuyedzedzo.licensing.admin.AdminPrincipal;
import com.mbuyedzedzo.licensing.admin.MailSettingsService;
import com.mbuyedzedzo.licensing.commerce.EmailService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Settings → Email — SMTP configuration, entered here instead of as Azure App Service env vars. */
@Controller
@RequestMapping("/admin/settings/email")
public class EmailSettingsController {

    private final MailSettingsService mailSettings;
    private final EmailService emailService;

    public EmailSettingsController(MailSettingsService mailSettings, EmailService emailService) {
        this.mailSettings = mailSettings;
        this.emailService = emailService;
    }

    @GetMapping
    String form(Model model, @AuthenticationPrincipal AdminPrincipal me) {
        requireSuperAdmin(me);
        model.addAttribute("settings", mailSettings.current());
        model.addAttribute("hasPasswordSet", mailSettings.hasPasswordSet());
        return "settings/email";
    }

    @PostMapping
    String save(@AuthenticationPrincipal AdminPrincipal me,
                @RequestParam String host, @RequestParam int port, @RequestParam String username,
                @RequestParam(required = false) String password, @RequestParam(required = false) String fromName,
                @RequestParam(defaultValue = "false") boolean useStartTls, RedirectAttributes ra) {
        requireSuperAdmin(me);
        mailSettings.save(host, port, username, password, fromName, useStartTls);
        ra.addFlashAttribute("flash", "Email settings saved.");
        return "redirect:/admin/settings/email";
    }

    @PostMapping("/test")
    String sendTest(@AuthenticationPrincipal AdminPrincipal me, @RequestParam String testEmail, RedirectAttributes ra) {
        requireSuperAdmin(me);
        try {
            emailService.sendTestEmail(testEmail);
            ra.addFlashAttribute("flash", "Test email sent to " + testEmail + " — check the inbox (and spam folder).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not send: " + e.getMessage());
        }
        return "redirect:/admin/settings/email";
    }

    private void requireSuperAdmin(AdminPrincipal me) {
        if (me == null || !me.isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Super admin only");
        }
    }
}
