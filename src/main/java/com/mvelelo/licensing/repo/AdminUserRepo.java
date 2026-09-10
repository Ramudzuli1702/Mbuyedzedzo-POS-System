package com.mvelelo.licensing.repo;

import com.mvelelo.licensing.domain.AdminUser;
import com.mvelelo.licensing.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepo extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByEmailIgnoreCase(String email);
    long countByRole(Role role);
}
