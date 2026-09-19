package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/customer/{customerId}/car/{carId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> add(@PathVariable Long customerId, @PathVariable Long carId) {
        wishlistService.addToWishlist(customerId, carId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Added to wishlist.", null));
    }

    @DeleteMapping("/customer/{customerId}/car/{carId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> remove(@PathVariable Long customerId, @PathVariable Long carId) {
        wishlistService.removeFromWishlist(customerId, carId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Removed from wishlist.", null));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getWishlist(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Wishlist.", wishlistService.getWishlistedCars(customerId)));
    }

    @GetMapping("/customer/{customerId}/car/{carId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> status(@PathVariable Long customerId, @PathVariable Long carId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Status.", wishlistService.isWishlisted(customerId, carId)));
    }
}
