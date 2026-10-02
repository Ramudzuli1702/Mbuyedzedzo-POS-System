package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Product;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Public marketing + checkout pages. Everything here is reachable without logging in. */
@Controller
public class StorefrontController {

    private final CheckoutService checkout;
    private final PricingService pricing;
    private final AccountService accountService;

    public StorefrontController(CheckoutService checkout, PricingService pricing, AccountService accountService) {
        this.checkout = checkout;
        this.pricing = pricing;
        this.accountService = accountService;
    }

    @GetMapping("/")
    public String home() {
        return "shop/home";
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
        return "shop/password-set";
    }
}
