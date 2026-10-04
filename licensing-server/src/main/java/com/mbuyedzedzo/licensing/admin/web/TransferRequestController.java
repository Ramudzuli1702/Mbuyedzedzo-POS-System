package com.mbuyedzedzo.licensing.admin.web;

import com.mbuyedzedzo.licensing.admin.AdminPrincipal;
import com.mbuyedzedzo.licensing.admin.TransferRequestAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/transfers")
public class TransferRequestController {

    private final TransferRequestAdminService service;

    public TransferRequestController(TransferRequestAdminService service) {
        this.service = service;
    }

    @GetMapping
    String list(Model model, @AuthenticationPrincipal AdminPrincipal me) {
        model.addAttribute("me", me);
        model.addAttribute("items", service.listPending(me));
        return "transfers/list";
    }

    @PostMapping("/{id}/approve")
    String approve(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        try {
            service.approve(me, id);
            ra.addFlashAttribute("flash", "Transfer approved — the licence can now be activated on a new machine.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/transfers";
    }

    @PostMapping("/{id}/deny")
    String deny(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        try {
            service.deny(me, id);
            ra.addFlashAttribute("flash", "Transfer request denied.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/transfers";
    }
}
