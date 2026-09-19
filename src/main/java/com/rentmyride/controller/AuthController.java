package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.OtpDTO;
import com.rentmyride.entities.Admin;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Driver;
import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.custom_exceptions.DriverNotFoundException;
import com.rentmyride.repository.AdminRepository;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.DriverRepository;
import com.rentmyride.security.IpRateLimiter;
import com.rentmyride.security.JwtUtil;
import com.rentmyride.security.TokenBlacklistService;
import com.rentmyride.service.CustomerService;
import com.rentmyride.service.DriverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

// Single, role-agnostic login endpoint. The person just enters their email/mobile + password —
// we work out whether they're a customer, driver, or admin by looking up the identifier across
// all three tables (customers first, since that's by far the most common login), and return the
// same AuthResponseDTO the old per-role endpoints used, with `role` telling the frontend where
// to route them. The old /api/customers/login, /api/drivers/login and /api/admin/login endpoints
// are left in place (unused by the new unified form) so nothing else breaks.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final CustomerService customerService;
    private final DriverService driverService;
    private final IpRateLimiter ipRateLimiter;

    // Same generic message whether the account doesn't exist OR the password is wrong.
    // Distinguishing the two (issue #4) lets an attacker enumerate which emails/mobiles are
    // registered, so both paths below return exactly this text.
    private static final String GENERIC_LOGIN_FAILURE = "Invalid email/mobile or password.";

    @PostMapping("/login")
    public AuthResponseDTO login(@RequestBody LoginRequest req) {
        String identifier = req.getUsername() == null ? "" : req.getUsername().trim();
        // Issue #33 fix — email is always stored lowercase (see registerCustomer/addDriver), so
        // lowercase it here too before every lookup below; mobile numbers are digits-only so
        // trimming alone is enough for that case.
        if (identifier.contains("@")) identifier = identifier.toLowerCase();
        String password = req.getPassword();

        if (identifier.isBlank() || password == null || password.isBlank()) {
            return AuthResponseDTO.failure("Email/mobile and password are required.");
        }

        // 1) Customer — matches by email OR mobile number
        Optional<Customer> customerOpt = customerRepository.findByEmailOrMobile(identifier);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            if (!passwordEncoder.matches(password, customer.getPassword())) {
                return AuthResponseDTO.failure(GENERIC_LOGIN_FAILURE);
            }
            if (customer.getAccountStatus() == Customer.AccountStatus.BLOCKED) {
                return AuthResponseDTO.failure("Your account has been blocked. Contact support.");
            }
            String token = jwtUtil.generateToken(customer.getEmail(), "CUSTOMER", customer.getCustomerId());
            return AuthResponseDTO.success(token, "CUSTOMER", customer.getCustomerId(),
                    customer.getFirstName() + " " + customer.getLastName(), customer.getEmail());
        }

        // 2) Driver — matches by email only
        Optional<Driver> driverOpt = driverRepository.findByEmail(identifier);
        if (driverOpt.isPresent()) {
            Driver driver = driverOpt.get();
            if (!passwordEncoder.matches(password, driver.getPassword())) {
                return AuthResponseDTO.failure(GENERIC_LOGIN_FAILURE);
            }
            if (driver.getStatus() != Driver.Status.ACTIVE) {
                return AuthResponseDTO.failure("Account inactive. Contact admin.");
            }
            String token = jwtUtil.generateToken(driver.getEmail(), "DRIVER", driver.getDriverId());
            return AuthResponseDTO.success(token, "DRIVER", driver.getDriverId(),
                    driver.getFirstName() + " " + driver.getLastName(), driver.getEmail());
        }

        // 3) Admin — matches by email only
        Optional<Admin> adminOpt = adminRepository.findByEmail(identifier);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (!passwordEncoder.matches(password, admin.getPassword())) {
                return AuthResponseDTO.failure(GENERIC_LOGIN_FAILURE);
            }
            String token = jwtUtil.generateToken(admin.getEmail(), "ADMIN", admin.getAdminId());
            return AuthResponseDTO.success(token, "ADMIN", admin.getAdminId(), admin.getName(), admin.getEmail());
        }

        // No account matched at all — same generic message as a wrong password, on purpose.
        return AuthResponseDTO.failure(GENERIC_LOGIN_FAILURE);
    }

    // ── Unified OTP Login (tries customer first, then driver — same lookup order as
    // password login above) ──────────────────────────────────
    @PostMapping("/login/otp/send")
    public AuthResponseDTO.ApiResponse sendLoginOtp(@Valid @RequestBody OtpDTO.SendRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed(httpRequest, "otp-send");
        try {
            customerService.sendLoginOtp(request.getIdentifier());
        } catch (CustomerNotFoundException notACustomer) {
            driverService.sendLoginOtp(request.getIdentifier()); // lets DriverNotFoundException propagate if this also fails
        }
        return AuthResponseDTO.ApiResponse.success("OTP sent to your registered email.", null);
    }

    @PostMapping("/login/otp/verify")
    public AuthResponseDTO verifyLoginOtp(@Valid @RequestBody OtpDTO.VerifyRequest request) {
        try {
            return customerService.verifyLoginOtp(request.getIdentifier(), request.getOtp());
        } catch (CustomerNotFoundException notACustomer) {
            return driverService.verifyLoginOtp(request.getIdentifier(), request.getOtp());
        }
    }

    // ── Unified Forgot Password (OTP based) ──────────────────
    @PostMapping("/forgot-password/send-otp")
    public AuthResponseDTO.ApiResponse sendForgotPasswordOtp(@Valid @RequestBody OtpDTO.SendRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed(httpRequest, "otp-send");
        try {
            customerService.sendForgotPasswordOtp(request.getIdentifier());
        } catch (CustomerNotFoundException notACustomer) {
            driverService.sendForgotPasswordOtp(request.getIdentifier());
        }
        return AuthResponseDTO.ApiResponse.success("OTP sent to your registered email.", null);
    }

    @PostMapping("/forgot-password/verify-otp")
    public AuthResponseDTO.ApiResponse verifyForgotPasswordOtp(@Valid @RequestBody OtpDTO.VerifyRequest request) {
        try {
            customerService.verifyForgotPasswordOtp(request.getIdentifier(), request.getOtp());
        } catch (CustomerNotFoundException notACustomer) {
            driverService.verifyForgotPasswordOtp(request.getIdentifier(), request.getOtp());
        }
        return AuthResponseDTO.ApiResponse.success("OTP verified.", null);
    }

    @PostMapping("/forgot-password/reset")
    public AuthResponseDTO.ApiResponse resetPassword(@Valid @RequestBody OtpDTO.ResetPasswordRequest request) {
        try {
            customerService.resetPassword(request.getIdentifier(), request.getOtp(), request.getNewPassword());
        } catch (CustomerNotFoundException notACustomer) {
            driverService.resetPassword(request.getIdentifier(), request.getOtp(), request.getNewPassword());
        }
        return AuthResponseDTO.ApiResponse.success("Password reset successfully.", null);
    }

    // Revokes the caller's own current token so it can't be reused even before its natural
    // expiry — closes issue #8 (no JWT revocation). Frontend should still discard its local
    // copy of the token; this covers the case where it (or a copy of it) leaks.
    @PostMapping("/logout")
    public com.rentmyride.dtos.AuthResponseDTO.ApiResponse logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklistService.revoke(authHeader.substring(7));
        }
        return com.rentmyride.dtos.AuthResponseDTO.ApiResponse.success("Logged out.", null);
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
