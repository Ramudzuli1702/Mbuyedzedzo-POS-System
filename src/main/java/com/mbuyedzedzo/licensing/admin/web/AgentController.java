package com.mbuyedzedzo.licensing.admin.web;

import com.mbuyedzedzo.licensing.admin.AdminPrincipal;
import com.mbuyedzedzo.licensing.admin.AgentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/agents")
public class AgentController {

    private final AgentService agents;

    public AgentController(AgentService agents) {
        this.agents = agents;
    }

    @GetMapping
    String list(Model model, @AuthenticationPrincipal AdminPrincipal me) {
        model.addAttribute("me", me);
        model.addAttribute("agents", agents.all());
        return "agents/list";
    }

    @PostMapping
    String create(@RequestParam String fullName, @RequestParam String email,
                  @RequestParam String password, RedirectAttributes ra) {
        try {
            agents.createAgent(fullName, email, password);
            ra.addFlashAttribute("flash", "Agent created.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/agents";
    }

    @PostMapping("/{id}/toggle")
    String toggle(@PathVariable long id, @RequestParam boolean active, RedirectAttributes ra) {
        try {
            agents.setActive(id, active);
            ra.addFlashAttribute("flash", active ? "Agent activated." : "Agent deactivated.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/agents";
    }

    @PostMapping("/{id}/reset-password")
    String resetPassword(@PathVariable long id, @RequestParam String password, RedirectAttributes ra) {
        agents.resetPassword(id, password);
        ra.addFlashAttribute("flash", "Password reset.");
        return "redirect:/admin/agents";
    }
}
