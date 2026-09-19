package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.DriverDTO;
import com.rentmyride.dtos.OtpDTO;
import com.rentmyride.entities.Driver;
import com.rentmyride.security.IpRateLimiter;
import com.rentmyride.service.DriverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final IpRateLimiter ipRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody DriverDTO.LoginRequest request) {
        return ResponseEntity.ok(driverService.loginDriver(request));
    }

    // ── OTP Login (mirrors CustomerController) ──────────────
    @PostMapping("/login/otp/send")
    public ResponseEntity<AuthResponseDTO.ApiResponse> sendLoginOtp(@Valid @RequestBody OtpDTO.SendRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed(httpRequest, "otp-send");
        driverService.sendLoginOtp(request.getIdentifier());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("OTP sent to your registered email.", null));
    }

    @PostMapping("/login/otp/verify")
    public ResponseEntity<AuthResponseDTO> verifyLoginOtp(@Valid @RequestBody OtpDTO.VerifyRequest request) {
        return ResponseEntity.ok(driverService.verifyLoginOtp(request.getIdentifier(), request.getOtp()));
    }

    // ── Forgot Password (OTP based, mirrors CustomerController) ──
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<AuthResponseDTO.ApiResponse> sendForgotPasswordOtp(@Valid @RequestBody OtpDTO.SendRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed(httpRequest, "otp-send");
        driverService.sendForgotPasswordOtp(request.getIdentifier());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("OTP sent to your registered email.", null));
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<AuthResponseDTO.ApiResponse> verifyForgotPasswordOtp(@Valid @RequestBody OtpDTO.VerifyRequest request) {
        driverService.verifyForgotPasswordOtp(request.getIdentifier(), request.getOtp());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("OTP verified.", null));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<AuthResponseDTO.ApiResponse> resetPassword(@Valid @RequestBody OtpDTO.ResetPasswordRequest request) {
        driverService.resetPassword(request.getIdentifier(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Password reset successfully.", null));
    }

    // Admin creates driver accounts
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> addDriver(@Valid @RequestBody DriverDTO.RegisterRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver added.", driverService.addDriver(request)));
    }

    @GetMapping("/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getById(@PathVariable Long driverId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver fetched.", driverService.getDriverById(driverId)));
    }

    @PutMapping("/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> update(@PathVariable Long driverId, @RequestBody DriverDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver updated.", driverService.updateDriver(driverId, dto)));
    }

    @DeleteMapping("/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> delete(@PathVariable Long driverId) {
        driverService.deleteDriver(driverId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver deactivated.", null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All drivers.", driverService.getAllDrivers()));
    }

    // Issue #25 — paginated version.
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Drivers page.",
                driverService.getAllDriversPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByStatus(@PathVariable Driver.Status status) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Drivers by status.", driverService.getDriversByStatus(status)));
    }

    @PatchMapping("/{driverId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateStatus(@PathVariable Long driverId, @RequestParam Driver.Status status) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Status updated.", driverService.updateStatus(driverId, status)));
    }

    @PatchMapping("/{driverId}/change-password")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> changePassword(
            @PathVariable Long driverId, @Valid @RequestBody DriverDTO.ChangePasswordRequest request) {
        driverService.changePassword(driverId, request);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Password changed successfully.", null));
    }

    @GetMapping("/{driverId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getStats(@PathVariable Long driverId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver stats.", driverService.getStats(driverId)));
    }
}
