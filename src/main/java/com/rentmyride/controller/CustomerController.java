package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.CustomerDTO;
import com.rentmyride.dtos.OtpDTO;
import com.rentmyride.entities.Customer;
import com.rentmyride.security.IpRateLimiter;
import com.rentmyride.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    private final IpRateLimiter ipRateLimiter;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO.ApiResponse> register(@Valid @RequestBody CustomerDTO.RegisterRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Registration successful.",
                customerService.registerCustomer(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody CustomerDTO.LoginRequest request) {
        return ResponseEntity.ok(customerService.loginCustomer(request));
    }

    // ── OTP Login ──────────────────────────────────────────
    // Step 1: send an OTP to the account's registered email
    @PostMapping("/login/otp/send")
    public ResponseEntity<AuthResponseDTO.ApiResponse> sendLoginOtp(@Valid @RequestBody OtpDTO.SendRequest request,
            HttpServletRequest httpRequest) {
        // Issue #9: per-IP throttle, independent of the existing per-identifier attempt counter.
        ipRateLimiter.checkAllowed(httpRequest, "otp-send");
        customerService.sendLoginOtp(request.getIdentifier());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("OTP sent to your registered email.", null));
    }

    // Step 2: verify the OTP and receive a JWT, same shape as the normal login response
    @PostMapping("/login/otp/verify")
    public ResponseEntity<AuthResponseDTO> verifyLoginOtp(@Valid @RequestBody OtpDTO.VerifyRequest request) {
        return ResponseEntity.ok(customerService.verifyLoginOtp(request.getIdentifier(), request.getOtp()));
    }

    // ── Forgot Password (OTP based) ───────────────────────
    // Step 1: send an OTP to the account's registered email
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<AuthResponseDTO.ApiResponse> sendForgotPasswordOtp(@Valid @RequestBody OtpDTO.SendRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed(httpRequest, "otp-send");
        customerService.sendForgotPasswordOtp(request.getIdentifier());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("OTP sent to your registered email.", null));
    }

    // Step 2: verify the OTP before allowing a password reset
    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<AuthResponseDTO.ApiResponse> verifyForgotPasswordOtp(@Valid @RequestBody OtpDTO.VerifyRequest request) {
        customerService.verifyForgotPasswordOtp(request.getIdentifier(), request.getOtp());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("OTP verified.", null));
    }

    // Step 3: set the new password (requires the OTP to have been verified in step 2)
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<AuthResponseDTO.ApiResponse> resetPassword(@Valid @RequestBody OtpDTO.ResetPasswordRequest request) {
        customerService.resetPassword(request.getIdentifier(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Password reset successfully.", null));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getById(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customer fetched.",
                customerService.getCustomerById(customerId)));
    }

    @GetMapping("/{customerId}/referral")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getReferralInfo(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Referral info.",
                customerService.getReferralInfo(customerId)));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> update(@PathVariable Long customerId,
            @RequestBody CustomerDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Profile updated.",
                customerService.updateCustomer(customerId, dto)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All customers.",
                customerService.getAllCustomers()));
    }

    // Issue #25 — paginated version.
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customers page.",
                customerService.getAllCustomersPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> search(@RequestParam String keyword) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Search results.",
                customerService.searchCustomers(keyword)));
    }

    @PatchMapping("/{customerId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateStatus(@PathVariable Long customerId,
            @RequestParam Customer.AccountStatus status) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Status updated.",
                customerService.updateAccountStatus(customerId, status)));
    }

    @PatchMapping("/{customerId}/change-password")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> changePassword(
            @PathVariable Long customerId, @Valid @RequestBody CustomerDTO.ChangePasswordRequest request) {
        customerService.changePassword(customerId, request);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Password changed successfully.", null));
    }

    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> delete(@PathVariable Long customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customer deactivated.", null));
    }
}
