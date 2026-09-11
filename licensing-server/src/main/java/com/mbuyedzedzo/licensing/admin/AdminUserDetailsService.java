package com.mbuyedzedzo.licensing.admin;

import com.mbuyedzedzo.licensing.repo.AdminUserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepo repo;

    public AdminUserDetailsService(AdminUserRepo repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return repo.findByEmailIgnoreCase(email)
                .map(AdminPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }
}
