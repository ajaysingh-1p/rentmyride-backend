package com.rentmyride.security;

import com.rentmyride.custom_exceptions.UnauthorizedAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Central place for "who is making this request, and are they allowed to touch this record?"
 * checks (fixes the IDOR issues on Reservation / Invoice endpoints — see issues #1, #2, #12).
 *
 * ADMIN can always proceed. CUSTOMER/DRIVER must own the resource they're asking for.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static CustomUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserPrincipal)) {
            throw new UnauthorizedAccessException("Authentication required.");
        }
        return (CustomUserPrincipal) auth.getPrincipal();
    }

    /** ADMIN always passes. Otherwise the caller must be a CUSTOMER whose id matches ownerCustomerId. */
    public static void assertOwnsAsCustomer(Long ownerCustomerId) {
        CustomUserPrincipal user = currentUser();
        if (user.isAdmin()) return;
        if (user.isCustomer() && ownerCustomerId != null && ownerCustomerId.equals(user.getUserId())) return;
        throw new UnauthorizedAccessException("You are not authorized to access this resource.");
    }

    /**
     * ADMIN always passes. A CUSTOMER must match ownerCustomerId, OR a DRIVER must match
     * ownerDriverId (either may be null when not applicable to the resource / caller's role).
     */
    public static void assertOwnsAsCustomerOrDriver(Long ownerCustomerId, Long ownerDriverId) {
        CustomUserPrincipal user = currentUser();
        if (user.isAdmin()) return;
        if (user.isCustomer() && ownerCustomerId != null && ownerCustomerId.equals(user.getUserId())) return;
        if (user.isDriver() && ownerDriverId != null && ownerDriverId.equals(user.getUserId())) return;
        throw new UnauthorizedAccessException("You are not authorized to access this resource.");
    }

    /** ADMIN always passes. Otherwise the caller must be a DRIVER whose id matches ownerDriverId. */
    public static void assertOwnsAsDriver(Long ownerDriverId) {
        CustomUserPrincipal user = currentUser();
        if (user.isAdmin()) return;
        if (user.isDriver() && ownerDriverId != null && ownerDriverId.equals(user.getUserId())) return;
        throw new UnauthorizedAccessException("You are not authorized to access this resource.");
    }
}
