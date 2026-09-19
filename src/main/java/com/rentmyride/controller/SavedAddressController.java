package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.SavedAddressDTO;
import com.rentmyride.service.SavedAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Customers can save frequently-used pickup addresses ("Home", "Office", etc) so they
// don't have to retype them on every booking.
@RestController
@RequestMapping("/api/saved-addresses")
@RequiredArgsConstructor
public class SavedAddressController {

    private final SavedAddressService savedAddressService;

    @PostMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> add(
            @PathVariable Long customerId, @Valid @RequestBody SavedAddressDTO.SaveRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Address saved.", savedAddressService.addAddress(customerId, request)));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Saved addresses.", savedAddressService.getAddressesForCustomer(customerId)));
    }

    @DeleteMapping("/{savedAddressId}/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> delete(
            @PathVariable Long savedAddressId, @PathVariable Long customerId) {
        savedAddressService.deleteAddress(customerId, savedAddressId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Address deleted.", null));
    }
}
