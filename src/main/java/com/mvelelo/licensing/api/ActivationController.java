package com.mvelelo.licensing.api;

import com.mvelelo.licensing.license.ActivationService;
import com.mvelelo.licensing.license.ActivationService.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class ActivationController {

    private final ActivationService service;

    ActivationController(ActivationService service) {
        this.service = service;
    }

    @PostMapping("/activate")
    TokenResponse activate(@Valid @RequestBody ActivateRequest r) {
        return toResponse(service.activate(
                r.key(), r.fingerprint(), r.machineLabel(), r.product(), r.appVersion()));
    }

    @PostMapping("/validate")
    TokenResponse validate(@Valid @RequestBody ValidateRequest r) {
        return toResponse(service.validate(r.key(), r.fingerprint()));
    }

    @PostMapping("/trial")
    TokenResponse trial(@Valid @RequestBody TrialRequest r) {
        return toResponse(service.startTrial(
                r.product(), r.fingerprint(), r.machineLabel(), r.appVersion()));
    }

    @PostMapping("/deactivate")
    void deactivate(@Valid @RequestBody DeactivateRequest r) {
        service.deactivate(r.key(), r.fingerprint());
    }

    private static TokenResponse toResponse(Result res) {
        var l = res.license();
        return new TokenResponse(
                res.token().token(),
                res.token().expiresAt().toString(),
                l.getStatus().name(),
                l.getType().name(),
                l.getExpiresAt() == null ? null : l.getExpiresAt().toString());
    }
}
