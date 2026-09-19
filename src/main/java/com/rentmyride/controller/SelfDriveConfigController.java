package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.SelfDriveConfigDTO;
import com.rentmyride.service.SelfDriveConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Everything a self-drive booking needs (deposit, free km/day, overage rate, late penalty,
// refund window) lives here, per car category, entirely admin-editable — nothing about
// self-drive pricing is hardcoded in the booking flow.
@RestController
@RequestMapping("/api/self-drive-config")
@RequiredArgsConstructor
public class SelfDriveConfigController {

    private final SelfDriveConfigService selfDriveConfigService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> create(@Valid @RequestBody SelfDriveConfigDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Self-drive config created.", selfDriveConfigService.create(dto)));
    }

    @PutMapping("/{configId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> update(@PathVariable Long configId, @Valid @RequestBody SelfDriveConfigDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Self-drive config updated.", selfDriveConfigService.update(configId, dto)));
    }

    @DeleteMapping("/{configId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> delete(@PathVariable Long configId) {
        selfDriveConfigService.delete(configId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Self-drive config deleted.", null));
    }

    // Admin panel listing — shows every category and its current config.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "All self-drive configs.", selfDriveConfigService.getAll()));
    }

    // Used by the booking flow to fetch the deposit/km-limit/etc. for a specific car's
    // category before showing the self-drive option to a customer — customer & admin can
    // both read it, only admin can change it.
    @GetMapping("/category/{carCategory}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByCategory(@PathVariable String carCategory) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Self-drive config for " + carCategory + ".", selfDriveConfigService.getByCategory(carCategory)));
    }
}
