package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.LicenseRequest;
import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Product;
import com.mbuyedzedzo.licensing.repo.LicenseRequestRepo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Public marketing + checkout pages. Everything here is reachable without logging in. */
@Controller
public class StorefrontController {

    private final CheckoutService checkout;
    private final PricingService pricing;
    private final AccountService accountService;
    private final LicenseRequestRepo licenseRequests;

    public StorefrontController(CheckoutService checkout, PricingService pricing, AccountService accountService,
                                 LicenseRequestRepo licenseRequests) {
        this.checkout = checkout;
        this.pricing = pricing;
        this.accountService = accountService;
        this.licenseRequests = licenseRequests;
    }

    @GetMapping("/")
    public String home() {
        return "shop/home";
    }

    @GetMapping("/download")
    public String download() {
        return "shop/download";
    }

    @GetMapping("/testers")
    public String testers() {
        return "shop/testers";
    }

    @GetMapping("/pricing")
    public String pricing(Model model) {
        model.addAttribute("standardPerpetual", pricing.priceFor(Product.POS_STANDARD, LicenseType.PERPETUAL));
        model.addAttribute("standardSubscription", pricing.priceFor(Product.POS_STANDARD, LicenseType.SUBSCRIPTION));
        model.addAttribute("retailPerpetual", pricing.priceFor(Product.POS_RETAIL, LicenseType.PERPETUAL));
        model.addAttribute("retailSubscription", pricing.priceFor(Product.POS_RETAIL, LicenseType.SUBSCRIPTION));
        return "shop/pricing";
    }

    @GetMapping("/buy")
    public String checkoutForm(@RequestParam Product product, @RequestParam LicenseType type, Model model) {
        model.addAttribute("product", product);
        model.addAttribute("type", type);
        model.addAttribute("productName", pricing.displayName(product));
        model.addAttribute("price", pricing.priceFor(product, type));
        return "shop/checkout";
    }

    @PostMapping("/buy")
    public String startCheckout(@RequestParam Product product, @RequestParam LicenseType type,
                                 @RequestParam String name, @RequestParam String email, Model model) {
        try {
            CheckoutService.CheckoutResult result = checkout.start(product, type, name, email);
            model.addAttribute("processUrl", result.processUrl());
            model.addAttribute("fields", result.payFastFields());
            return "shop/redirecting";
        } catch (CheckoutService.InvalidCheckoutException e) {
            model.addAttribute("product", product);
            model.addAttribute("type", type);
            model.addAttribute("productName", pricing.displayName(product));
            model.addAttribute("price", pricing.priceFor(product, type));
            model.addAttribute("error", e.getMessage());
            return "shop/checkout";
        }
    }

    // ── License request — stands in for the PayFast checkout while that's ──
    // ── still in testing: a visitor requests a license, an admin reviews   ──
    // ── it in the portal and approves it (issuing a real key + login) or  ──
    // ── rejects it. See LicenseRequestAdminService for the approval side. ──

    @GetMapping("/request-license")
    public String requestLicenseForm(@RequestParam Product product, @RequestParam LicenseType type, Model model) {
        model.addAttribute("product", product);
        model.addAttribute("type", type);
        model.addAttribute("productName", pricing.displayName(product));
        return "shop/request-license";
    }

    @PostMapping("/request-license")
    public String submitLicenseRequest(@RequestParam Product product, @RequestParam LicenseType type,
                                        @RequestParam String businessName, @RequestParam String contactName,
                                        @RequestParam String email, @RequestParam(required = false) String phone,
                                        @RequestParam(required = false) String notes, Model model) {
        String error = validateRequest(businessName, contactName, email);
        if (error != null) {
            model.addAttribute("product", product);
            model.addAttribute("type", type);
            model.addAttribute("productName", pricing.displayName(product));
            model.addAttribute("error", error);
            model.addAttribute("businessName", businessName);
            model.addAttribute("contactName", contactName);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            model.addAttribute("notes", notes);
            return "shop/request-license";
        }

        LicenseRequest req = new LicenseRequest();
        req.setBusinessName(businessName.trim());
        req.setContactName(contactName.trim());
        req.setEmail(email.trim());
        req.setPhone(phone == null ? null : phone.trim());
        req.setNotes(notes == null ? null : notes.trim());
        req.setProduct(product);
        req.setLicenseType(type);
        licenseRequests.save(req);

        return "shop/request-received";
    }

    private String validateRequest(String businessName, String contactName, String email) {
        if (businessName == null || businessName.isBlank() || businessName.length() > 150) {
            return "Please enter your business name (max 150 characters).";
        }
        if (contactName == null || contactName.isBlank() || contactName.length() > 150) {
            return "Please enter your name (max 150 characters).";
        }
        if (email == null || email.isBlank() || email.length() > 190
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "Please enter a valid email address.";
        }
        return null;
    }

    @GetMapping("/buy/success")
    public String success() {
        return "shop/success";
    }

    @GetMapping("/buy/cancel")
    public String cancel() {
        return "shop/cancel";
    }

    // ── "Set up your account" — the one-time link emailed after purchase ──

    @GetMapping("/account/set-password")
    public String setPasswordForm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "shop/set-password";
    }

    @PostMapping("/account/set-password")
    public String setPassword(@RequestParam String token, @RequestParam String password,
                               @RequestParam String confirm, Model model) {
        if (!password.equals(confirm)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Those passwords don't match.");
            return "shop/set-password";
        }
        if (password.length() < 8) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Password must be at least 8 characters.");
            return "shop/set-password";
        }
        boolean ok = accountService.setPasswordFromToken(token, password);
        if (!ok) {
            model.addAttribute("token", token);
            model.addAttribute("error", "This link has expired or was already used. Request a new one by contacting support.");
            return "shop/set-password";
        }
        return "redirect:/account/login?setupComplete";
    }
}
