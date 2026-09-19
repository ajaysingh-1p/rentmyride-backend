package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.RentalDTO;
import com.rentmyride.service.RentalService;
import com.rentmyride.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/rentals")
@RequiredArgsConstructor
public class RentalController {
    private final RentalService rentalService;
    private final InvoiceService invoiceService;

    // Pickup — same form as before; the reservation dropdown is restricted (on the frontend,
    // backed by GET /api/reservations/driver/{driverId}/pending-pickups) to the driver's own jobs.
    @PostMapping("/pickup")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> initiateRental(@RequestBody RentalDTO.PickupRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Rental started.", rentalService.initiateRental(request)));
    }

    // Return/Drop-off — same "last km" form as before
    //
    // Bug fix (the drop-off request that hangs ~50s then fails with a generic timeout on the
    // frontend): completeRental() used to call invoiceService.generateInvoice() — a
    // REQUIRES_NEW (separate-connection) transaction — from INSIDE its own still-open
    // @Transactional block, which itself was holding a pessimistic write lock on this exact
    // Rental row (findByIdForUpdate). Inserting the new Invoice row requires MySQL to take a
    // lock on the parent Rental row too (to check the foreign key) — a lock the outer
    // transaction is still holding and can't release until generateInvoice() RETURNS. Same
    // thread, waiting on itself through two different DB connections: a classic self-deadlock,
    // resolved only when MySQL's own innodb_lock_wait_timeout (default 50s) finally kills the
    // inner transaction — by which point the frontend's 15s axios timeout has long since fired
    // and shown "Could not close the trip.", even though the rental usually finishes completing
    // right after (just invoice-less, since that attempt errored out).
    //
    // Fix: call generateInvoice() here, AFTER rentalService.completeRental() has already
    // returned. The Controller itself isn't @Transactional, so Spring has already committed
    // (and released the lock on) the outer transaction by the time this line runs — the FK
    // check on the Invoice insert then has nothing left to wait for.
    @PatchMapping("/{rentalId}/return")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> completeRental(@PathVariable Long rentalId,
            @RequestBody RentalDTO.ReturnRequest request) {
        var completed = rentalService.completeRental(rentalId, request);
        try {
            invoiceService.generateInvoice(rentalId);
        } catch (Exception e) {
            log.error("[DDT-ERROR] Auto invoice generation failed for rental {}: {}", rentalId, e.getMessage());
        }
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Rental completed.", completed));
    }

    // Issue #21 — Admin review queue for damage charges a Driver reported above the auto-approval
    // threshold, plus the approve/reject actions.
    @GetMapping("/damage-approvals/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getPendingDamageApprovals() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Pending damage approvals.", rentalService.getPendingDamageApprovals()));
    }

    @PatchMapping("/{rentalId}/damage-approvals/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> approveDamageCharge(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Damage charge approved and added to the bill.", rentalService.approveDamageCharge(rentalId)));
    }

    @PatchMapping("/{rentalId}/damage-approvals/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> rejectDamageCharge(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Damage charge rejected — will not be billed.", rentalService.rejectDamageCharge(rentalId)));
    }

    // Customer-initiated: extend an active rental to a later return date
    @PatchMapping("/{rentalId}/extend")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> extendRental(@PathVariable Long rentalId,
            @RequestBody RentalDTO.ExtendRequest request) {
        RentalDTO.ExtendResponse result = rentalService.extendRental(rentalId, request);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(result.getMessage(), result));
    }

    // Driver's phone posts their current GPS position every ~20s while the trip is ACTIVE
    @PatchMapping("/{rentalId}/location")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateLocation(@PathVariable Long rentalId,
            @RequestParam Long driverId, @RequestBody RentalDTO.LocationUpdateRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Location updated.", rentalService.updateLocation(rentalId, driverId, request)));
    }

    @PatchMapping("/{rentalId}/rebook-poll")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> submitRebookPoll(@PathVariable Long rentalId,
            @RequestParam Long customerId, @RequestBody RentalDTO.RebookPollRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Thanks for the feedback!", rentalService.submitRebookPoll(rentalId, customerId, request)));
    }

    @GetMapping("/{rentalId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getById(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Rental fetched.", rentalService.getRentalById(rentalId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All rentals.", rentalService.getAllRentals()));
    }

    // Issue #25 — paginated version.
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Rentals page.",
                rentalService.getAllRentalsPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getActive() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Active rentals.", rentalService.getActiveRentals()));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customer rentals.", rentalService.getRentalsByCustomer(customerId)));
    }

    @GetMapping("/customer/{customerId}/export-pdf")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public org.springframework.http.ResponseEntity<byte[]> exportPdf(@PathVariable Long customerId,
            @RequestParam(required = false) String customerName) throws Exception {
        java.util.List<com.rentmyride.dtos.RentalDTO> rentals = rentalService.getRentalsByCustomer(customerId);
        byte[] pdf = com.rentmyride.util.TripHistoryPdfGenerator.generateRentals(rentals,
                customerName != null ? customerName : "Customer #" + customerId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rental-history.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver rentals.", rentalService.getRentalsByDriver(driverId)));
    }

    @GetMapping("/revenue/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> totalRevenue() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Total revenue.", rentalService.getTotalRevenue()));
    }

    @GetMapping("/revenue/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> monthlyRevenue(@RequestParam int month, @RequestParam int year) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Monthly revenue.", rentalService.getMonthlyRevenue(month, year)));
    }
}
