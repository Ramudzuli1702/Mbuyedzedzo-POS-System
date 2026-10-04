package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.Customer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The logged-in customer — reachable in controllers via @AuthenticationPrincipal. */
public class CustomerPrincipal implements UserDetails {

    private final Customer customer;

    public CustomerPrincipal(Customer customer) {
        this.customer = customer;
    }

    public Long id()            { return customer.getId(); }
    public String email()       { return customer.getEmail(); }
    public String displayName() { return customer.getContactName(); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
    @Override public String getPassword() { return customer.getPasswordHash(); }
    @Override public String getUsername() { return customer.getEmail(); }
    @Override public boolean isEnabled()  { return customer.getPasswordHash() != null; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
