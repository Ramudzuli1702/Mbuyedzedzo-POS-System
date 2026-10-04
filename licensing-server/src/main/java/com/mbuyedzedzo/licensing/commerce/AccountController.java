package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.Activation;
import com.mbuyedzedzo.licensing.domain.License;
import com.mbuyedzedzo.licensing.domain.TransferRequest;
import com.mbuyedzedzo.licensing.domain.TransferRequestStatus;
import com.mbuyedzedzo.licensing.repo.ActivationRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import com.mbuyedzedzo.licensing.repo.TransferRequestRepo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The customer self-service portal — logged in via the /account/** security chain. */
@Controller
public class AccountController {

    private final LicenseRepo licenses;
    private final TransferRequestRepo transferRequests;
    private final ActivationRepo activations;

    public AccountController(LicenseRepo licenses, TransferRequestRepo transferRequests, ActivationRepo activations) {
        this.licenses = licenses;
        this.transferRequests = transferRequests;
        this.activations = activations;
    }

    @GetMapping("/account/login")
    public String login() {
        return "account/login";
    }

    @GetMapping("/account")
    public String dashboard(@AuthenticationPrincipal CustomerPrincipal me, Model model) {
        List<License> myLicenses = licenses.findByCustomerId(me.id());
        List<TransferRequest> myRequests = transferRequests.findByCustomerId(me.id());

        // licenseId -> most recent transfer request, so the view can show "pending" inline
        Map<Long, TransferRequest> latestRequestByLicense = new java.util.HashMap<>();
        for (TransferRequest tr : myRequests) {
            latestRequestByLicense.merge(tr.getLicenseId(), tr,
                    (a, b) -> a.getRequestedAt().isAfter(b.getRequestedAt()) ? a : b);
        }

        // licenseId -> the currently-active machine, if any
        Map<Long, Activation> activeActivationByLicense = new java.util.HashMap<>();
        for (License l : myLicenses) {
            activations.findByLicenseIdOrderByActivatedAtDesc(l.getId()).stream()
                    .filter(Activation::isActive)
                    .findFirst()
                    .ifPresent(a -> activeActivationByLicense.put(l.getId(), a));
        }

        model.addAttribute("me", me);
        model.addAttribute("licenses", myLicenses);
        model.addAttribute("requestsByLicense", latestRequestByLicense);
        model.addAttribute("activationByLicense", activeActivationByLicense);
        return "account/dashboard";
    }

    @PostMapping("/account/licenses/{id}/request-transfer")
    public String requestTransfer(@PathVariable long id, @RequestParam(required = false) String reason,
                                   @AuthenticationPrincipal CustomerPrincipal me) {
        License license = licenses.findById(id).orElse(null);
        // Belongs-to-me check: don't let a customer request a transfer on someone else's
        // license just by guessing an id — same "don't leak existence" pattern as the
        // admin side's CustomerAdminService.get().
        if (license == null || !me.id().equals(license.getCustomerId())) {
            return "redirect:/account?error=notfound";
        }

        boolean alreadyPending = !transferRequests.findByLicenseIdAndStatus(id, TransferRequestStatus.PENDING).isEmpty();
        if (!alreadyPending) {
            TransferRequest tr = new TransferRequest();
            tr.setLicenseId(id);
            tr.setCustomerId(me.id());
            tr.setReason(reason);
            tr.setStatus(TransferRequestStatus.PENDING);
            tr.setRequestedAt(Instant.now());
            transferRequests.save(tr);
        }
        return "redirect:/account?requested";
    }
}
