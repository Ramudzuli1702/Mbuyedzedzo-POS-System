package com.mvelelo.licensing.admin;

import com.mvelelo.licensing.domain.AdminUser;
import com.mvelelo.licensing.domain.Role;
import com.mvelelo.licensing.repo.AdminUserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private final AdminUserRepo repo;
    private final PasswordEncoder encoder;

    public AgentService(AdminUserRepo repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public List<AdminUser> all() {
        return repo.findAll();
    }

    public AdminUser createAgent(String fullName, String email, String password) {
        repo.findByEmailIgnoreCase(email).ifPresent(u -> {
            throw new IllegalArgumentException("An account with that email already exists.");
        });
        AdminUser a = new AdminUser();
        a.setFullName(fullName.trim());
        a.setEmail(email.trim());
        a.setPasswordHash(encoder.encode(password));
        a.setRole(Role.SALES_AGENT);
        a.setActive(true);
        return repo.save(a);
    }

    public void setActive(long id, boolean active) {
        AdminUser a = repo.findById(id).orElseThrow();
        if (a.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot deactivate a super admin here.");
        }
        a.setActive(active);
        repo.save(a);
    }

    public void resetPassword(long id, String newPassword) {
        AdminUser a = repo.findById(id).orElseThrow();
        a.setPasswordHash(encoder.encode(newPassword));
        repo.save(a);
    }
}
