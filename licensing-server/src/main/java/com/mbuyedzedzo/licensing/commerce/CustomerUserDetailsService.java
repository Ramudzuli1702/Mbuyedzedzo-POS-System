package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.repo.CustomerRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepo repo;

    public CustomerUserDetailsService(CustomerRepo repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        // A customer with no password yet (never followed the "set up your account"
        // link) looks the same as "not found" here — same error either way at login,
        // so we don't reveal which emails have purchased something.
        // Emails are stored lowercased (CheckoutService normalizes at purchase time).
        return repo.findByEmail(email == null ? null : email.trim().toLowerCase())
                .filter(c -> c.getPasswordHash() != null)
                .map(CustomerPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }
}
