package com.rentmyride.controller;

import com.rentmyride.dtos.AdminDTO;
import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.BusinessSettingsDTO;
import com.rentmyride.security.JwtUtil;
import com.rentmyride.service.AdminService;
import com.rentmyride.service.BusinessSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final BusinessSettingsService businessSettingsService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AdminDTO.LoginRequest request) {
        return ResponseEntity.ok(adminService.loginAdmin(request));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getProfile(HttpServletRequest httpRequest) {
        Long adminId = extractAdminId(httpRequest);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Profile fetched.", adminService.getProfile(adminId)));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateProfile(
            HttpServletRequest httpRequest, @RequestBody AdminDTO.UpdateProfileRequest request) {
        Long adminId = extractAdminId(httpRequest);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Profile updated.", adminService.updateProfile(adminId, request)));
    }

    @PatchMapping("/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> changePassword(
            HttpServletRequest httpRequest, @RequestBody AdminDTO.ChangePasswordRequest request) {
        Long adminId = extractAdminId(httpRequest);
        adminService.changePassword(adminId, request);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Password changed successfully.", null));
    }

    @GetMapping("/business-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getBusinessSettings() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Business settings fetched.", businessSettingsService.getSettings()));
    }

    @PutMapping("/business-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateBusinessSettings(
            @RequestBody BusinessSettingsDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Business settings updated.", businessSettingsService.updateSettings(dto)));
    }

    // Pulls the authenticated admin's ID straight out of their own JWT —
    // avoids trusting a client-supplied ID for "my profile" operations.
    private Long extractAdminId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        return jwtUtil.extractUserId(token);
    }
}
