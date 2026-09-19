package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.SelfDriveHandoverDTO;
import com.rentmyride.service.InvoiceService;
import com.rentmyride.service.SelfDriveHandoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/self-drive")
@RequiredArgsConstructor
public class SelfDriveHandoverController {

    private final SelfDriveHandoverService handoverService;
    private final InvoiceService invoiceService;

    // ── Customer: get the code to show at the counter ──────────────────────────

    @PostMapping("/reservations/{reservationId}/pickup-otp")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> requestPickupOtp(@PathVariable Long reservationId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Pickup code generated.",
                handoverService.requestPickupOtp(reservationId)));
    }

    @PostMapping("/rentals/{rentalId}/return-otp")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> requestReturnOtp(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Return code generated.",
                handoverService.requestReturnOtp(rentalId)));
    }

    @GetMapping("/customer/{customerId}/active-trip")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getMyActiveTrip(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Active self-drive trip.",
                handoverService.getMyActiveTrip(customerId)));
    }

    // ── Admin/staff: the handover desk ────────────────────────────────────────

    @GetMapping("/handover/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getPendingHandovers() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Pending self-drive handovers.",
                handoverService.getPendingHandovers()));
    }

    @GetMapping("/handover/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getActiveHandovers() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Self-drive cars currently out.",
                handoverService.getActiveHandovers()));
    }

    @PostMapping("/handover/pickup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> confirmPickup(
            @RequestBody SelfDriveHandoverDTO.PickupRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Keys handed over — trip started.",
                handoverService.confirmPickup(request)));
    }

    /**
     * Invoice generation deliberately happens HERE, after confirmReturn() has returned and its
     * transaction has committed — exactly the same reason as RentalController.completeRental():
     * generateInvoice() runs in its own REQUIRES_NEW transaction, and calling it while the outer
     * transaction still holds a pessimistic lock on the Rental row self-deadlocks until MySQL's
     * lock-wait timeout fires.
     */
    @PostMapping("/handover/return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> confirmReturn(
            @RequestBody SelfDriveHandoverDTO.ReturnRequest request) {
        var settlement = handoverService.confirmReturn(request);
        try {
            invoiceService.generateInvoice(settlement.getRentalId());
        } catch (Exception e) {
            log.error("[DDT-ERROR] Auto invoice generation failed for self-drive rental {}: {}",
                    settlement.getRentalId(), e.getMessage());
        }
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(settlement.getSummary(), settlement));
    }
}
