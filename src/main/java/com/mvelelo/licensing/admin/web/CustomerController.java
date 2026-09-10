package com.mvelelo.licensing.admin.web;

import com.mvelelo.licensing.admin.AdminPrincipal;
import com.mvelelo.licensing.admin.CustomerAdminService;
import com.mvelelo.licensing.repo.LicenseRepo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/customers")
public class CustomerController {

    private final CustomerAdminService customers;
    private final LicenseRepo licenses;

    public CustomerController(CustomerAdminService customers, LicenseRepo licenses) {
        this.customers = customers;
        this.licenses = licenses;
    }

    @GetMapping
    String list(Model model, @AuthenticationPrincipal AdminPrincipal me,
                @RequestParam(required = false) String q,
                @RequestParam(defaultValue = "0") int page) {
        var pageable = PageRequest.of(page, 20, Sort.by("orgName"));
        model.addAttribute("me", me);
        model.addAttribute("q", q);
        model.addAttribute("customers", customers.list(me, q, pageable));
        return "customers/list";
    }

    @GetMapping("/new")
    String newForm(Model model, @AuthenticationPrincipal AdminPrincipal me) {
        model.addAttribute("me", me);
        model.addAttribute("form", new Form("", "", "", "", ""));
        return "customers/form";
    }

    @PostMapping
    String create(@Validated @ModelAttribute("form") Form form, BindingResult errors,
                  @AuthenticationPrincipal AdminPrincipal me, Model model, RedirectAttributes ra) {
        if (errors.hasErrors()) {
            model.addAttribute("me", me);
            return "customers/form";
        }
        var c = customers.create(me, form.orgName(), form.contactName(), form.email(), form.phone(), form.notes());
        ra.addFlashAttribute("flash", "Customer created.");
        return "redirect:/admin/customers/" + c.getId();
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model, @AuthenticationPrincipal AdminPrincipal me) {
        var c = customers.get(me, id);
        model.addAttribute("me", me);
        model.addAttribute("customer", c);
        model.addAttribute("licenses", licenses.findByCustomerId(id));
        return "customers/detail";
    }

    public record Form(
            @NotBlank String orgName,
            @NotBlank String contactName,
            @NotBlank @Email String email,
            String phone,
            String notes) {}
}
