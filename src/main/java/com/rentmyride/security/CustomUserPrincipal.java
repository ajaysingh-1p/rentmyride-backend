package com.rentmyride.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated-principal type used across the app instead of Spring's plain User.
 *
 * The default UserDetails only carries a username (email), which is not enough to answer
 * "does this logged-in user own the resource they're asking for?" (see IDOR fixes on
 * Reservation / Invoice endpoints). This principal is built straight from the JWT's own
 * claims (email, role, userId) in JwtFilter, so it's always available on every authenticated
 * request without an extra DB round-trip.
 */
public class CustomUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String role; // "CUSTOMER" | "DRIVER" | "ADMIN"

    public CustomUserPrincipal(Long userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isCustomer() {
        return "CUSTOMER".equalsIgnoreCase(role);
    }

    public boolean isDriver() {
        return "DRIVER".equalsIgnoreCase(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // Password is never needed post-authentication (the JWT itself is the credential).
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
