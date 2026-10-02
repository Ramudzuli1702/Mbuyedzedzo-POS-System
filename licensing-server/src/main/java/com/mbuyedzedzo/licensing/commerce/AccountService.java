package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.Customer;
import com.mbuyedzedzo.licensing.repo.CustomerRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Customer self-service account setup (the "set your password" link from the purchase email). */
@Service
public class AccountService {

    private final CustomerRepo customers;
    private final PasswordEncoder passwordEncoder;

    public AccountService(CustomerRepo customers, PasswordEncoder passwordEncoder) {
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
    }

    /** @return false if the token doesn't exist or has expired. */
    @Transactional
    public boolean setPasswordFromToken(String token, String newPassword) {
        Optional<Customer> maybe = customers.findBySetPasswordToken(token);
        if (maybe.isEmpty()) return false;

        Customer c = maybe.get();
        if (c.getSetPasswordTokenExpiresAt() == null || c.getSetPasswordTokenExpiresAt().isBefore(Instant.now())) {
            return false;
        }

        c.setPasswordHash(passwordEncoder.encode(newPassword));
        c.setSetPasswordToken(null);
        c.setSetPasswordTokenExpiresAt(null);
        customers.save(c);
        return true;
    }
}
