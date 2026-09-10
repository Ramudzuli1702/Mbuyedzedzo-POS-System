package com.mvelelo.licensing.admin.web;

import com.mvelelo.licensing.admin.AdminPrincipal;
import com.mvelelo.licensing.admin.CustomerAdminService;
import com.mvelelo.licensing.admin.LicenseQueryService;
import com.mvelelo.licensing.domain.LicenseType;
import com.mvelelo.licensing.domain.Product;
import com.mvelelo.licensing.license.LicenseAdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Controller
@RequestMapping("/admin/licenses")
public class LicenseController {

    private final LicenseQueryService query;
    private final LicenseAdminService admin;
    private final CustomerAdminService customers;

    public LicenseController(LicenseQueryService query, LicenseAdminService admin, CustomerAdminService customers) {
        this.query = query;
        this.admin = admin;
        this.customers = customers;
    }

    @GetMapping
    String list(Model model, @AuthenticationPrincipal AdminPrincipal me,
                @RequestParam(defaultValue = "0") int page) {
        var licenses = query.list(me, PageRequest.of(page, 25, Sort.by(Sort.Direction.DESC, "issuedAt")));
        model.addAttribute("me", me);
        model.addAttribute("licenses", licenses);
        model.addAttribute("customerNames", query.customerNames(licenses.getContent()));
        return "licenses/list";
    }

    @GetMapping("/new")
    String newForm(Model model, @AuthenticationPrincipal AdminPrincipal me,
                   @RequestParam(required = false) Long customerId) {
        model.addAttribute("me", me);
        model.addAttribute("products", Product.values());
        model.addAttribute("types", LicenseType.values());
        model.addAttribute("customers", customers.list(me, null, PageRequest.of(0, 200, Sort.by("orgName"))));
        model.addAttribute("form", new Form(customerId, Product.POS_STANDARD, LicenseType.PERPETUAL, 1, null, null));
        return "licenses/form";
    }

    @PostMapping
    String create(@ModelAttribute("form") Form form, @AuthenticationPrincipal AdminPrincipal me,
                  RedirectAttributes ra) {
        Instant expires = form.type() == LicenseType.PERPETUAL || form.expiresOn() == null
                ? null
                : form.expiresOn().atStartOfDay().toInstant(ZoneOffset.UTC);
        var lic = admin.issue(form.product(), form.type(), form.maxMachines(),
                expires, form.customerId(), me.id(), form.notes(), me.email());
        ra.addFlashAttribute("flash", "License " + lic.getLicenseKey() + " issued.");
        return "redirect:/admin/licenses/" + lic.getId();
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model, @AuthenticationPrincipal AdminPrincipal me) {
        model.addAttribute("me", me);
        model.addAttribute("d", query.detail(me, id));
        return "licenses/detail";
    }

    @PostMapping("/{id}/revoke")
    String revoke(@PathVariable long id, @RequestParam String reason,
                  @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        requireSuperAdmin(me);
        admin.revoke(id, reason, me.email());
        ra.addFlashAttribute("flash", "License revoked.");
        return "redirect:/admin/licenses/" + id;
    }

    @PostMapping("/{id}/suspend")
    String suspend(@PathVariable long id, @RequestParam boolean suspended,
                   @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        requireSuperAdmin(me);
        admin.setSuspended(id, suspended, me.email());
        ra.addFlashAttribute("flash", suspended ? "License suspended." : "License un-suspended.");
        return "redirect:/admin/licenses/" + id;
    }

    @PostMapping("/{id}/renew")
    String renew(@PathVariable long id, @RequestParam LocalDate until,
                 @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        admin.renew(id, until.atStartOfDay().toInstant(ZoneOffset.UTC), me.email());
        ra.addFlashAttribute("flash", "License renewed to " + until + ".");
        return "redirect:/admin/licenses/" + id;
    }

    @PostMapping("/{id}/transfer")
    String transfer(@PathVariable long id, @RequestParam String reason,
                    @AuthenticationPrincipal AdminPrincipal me, RedirectAttributes ra) {
        // agents may transfer their own; query.detail() already enforces ownership
        query.detail(me, id);
        admin.resetMachineBinding(id, reason, me.email());
        ra.addFlashAttribute("flash", "Machine binding reset — the key can be activated on a new machine.");
        return "redirect:/admin/licenses/" + id;
    }

    private static void requireSuperAdmin(AdminPrincipal me) {
        if (!me.isSuperAdmin()) throw new org.springframework.security.access.AccessDeniedException("super admin only");
    }

    public record Form(Long customerId, Product product, LicenseType type,
                       int maxMachines, LocalDate expiresOn, String notes) {}
}
