package com.mvelelo.licensing.admin;

import com.mvelelo.licensing.domain.AdminUser;
import com.mvelelo.licensing.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The logged-in admin — reachable in controllers via @AuthenticationPrincipal. */
public class AdminPrincipal implements UserDetails {

    private final AdminUser user;

    public AdminPrincipal(AdminUser user) {
        this.user = user;
    }

    public Long id()            { return user.getId(); }
    public String email()       { return user.getEmail(); }
    public String displayName() { return user.getFullName(); }
    public Role role()          { return user.getRole(); }
    public boolean isSuperAdmin() { return user.getRole() == Role.SUPER_ADMIN; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getEmail(); }
    @Override public boolean isEnabled()  { return user.isActive(); }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
