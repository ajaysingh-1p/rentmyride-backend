package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.PromoCodeDTO;
import com.rentmyride.service.PromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promo-codes")
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    // Customer checks a code before booking (or the backend calls this implicitly on booking creation)
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> validate(@Valid @RequestBody PromoCodeDTO.ValidateRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Validated.",
                promoCodeService.validate(request.getCode(), request.getBookingAmount())));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> create(@Valid @RequestBody PromoCodeDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Promo code created.", promoCodeService.create(dto)));
    }

    @PutMapping("/{promoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> update(@PathVariable Long promoId, @Valid @RequestBody PromoCodeDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Promo code updated.", promoCodeService.update(promoId, dto)));
    }

    @DeleteMapping("/{promoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> delete(@PathVariable Long promoId) {
        promoCodeService.delete(promoId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Promo code deleted.", null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All promo codes.", promoCodeService.getAll()));
    }
}
