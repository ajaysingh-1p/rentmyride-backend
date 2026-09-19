package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.ReservationDTO;
import com.rentmyride.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    // Public — shown on the car detail page so customers can see availability before booking (no login required)
    @GetMapping("/car/{carId}/availability")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAvailability(@PathVariable Long carId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Booked date ranges.",
                reservationService.getBookedDateRanges(carId)));
    }

    // Admin assigns a driver to a booking — sends the customer a notification + email
    @PatchMapping("/{reservationId}/assign-driver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> assignDriver(@PathVariable Long reservationId,
            @RequestBody ReservationDTO.AssignDriverRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Driver assigned.",
                reservationService.assignDriver(reservationId, request.getDriverId())));
    }

    // Driver's own pickup-form dropdown — only their assigned, not-yet-picked-up bookings
    @GetMapping("/driver/{driverId}/pending-pickups")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getPendingPickups(@PathVariable Long driverId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Pending pickups.",
                reservationService.getPendingPickupsForDriver(driverId)));
    }

    // "You have a due amount" login popup.
    @GetMapping("/customer/{customerId}/due-balance")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getDueBalance(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Due balance.",
                reservationService.getDueBalanceReservations(customerId)));
    }

    // ── Driver Trip Accept/Reject ──────────────────────────────
    // Trip requests still awaiting this driver's accept/reject response.
    @GetMapping("/driver/trip-requests/pending")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getPendingTripRequests() {
        Long driverId = com.rentmyride.security.SecurityUtils.currentUser().getUserId();
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Pending trip requests.",
                reservationService.getPendingTripRequestsForDriver(driverId)));
    }

    @PatchMapping("/{reservationId}/trip-requests/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> acceptTripAssignment(@PathVariable Long reservationId) {
        // driverId is taken from the caller's own JWT, never the request body/URL — a driver can
        // only ever accept/reject a trip that's actually assigned to THEM.
        Long driverId = com.rentmyride.security.SecurityUtils.currentUser().getUserId();
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Trip accepted.",
                reservationService.acceptTripAssignment(reservationId, driverId)));
    }

    @PatchMapping("/{reservationId}/trip-requests/reject")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> rejectTripAssignment(@PathVariable Long reservationId,
            @RequestBody(required = false) ReservationDTO.RejectTripRequest request) {
        Long driverId = com.rentmyride.security.SecurityUtils.currentUser().getUserId();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Trip declined.",
                reservationService.rejectTripAssignment(reservationId, driverId, reason)));
    }

    @PostMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> create(@PathVariable Long customerId,
            @Valid @RequestBody ReservationDTO.CreateRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Reservation created.",
                reservationService.createReservation(customerId, request)));
    }

    // Live price preview (distance × car's per-km rate) shown as the customer picks locations,
    // before they actually confirm the booking.
    @PostMapping("/estimate")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> estimate(@RequestBody ReservationDTO.EstimateRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Price estimated.",
                reservationService.estimatePrice(request)));
    }

    @GetMapping("/{reservationId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getById(@PathVariable Long reservationId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Reservation fetched.",
                reservationService.getReservationById(reservationId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All reservations.",
                reservationService.getAllReservations()));
    }

    // Issue #25 — paginated version for admin tables. GET /api/reservations/page?page=0&size=20
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Reservations page.",
                reservationService.getAllReservationsPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customer reservations.",
                reservationService.getReservationsByCustomer(customerId)));
    }

    @GetMapping("/customer/{customerId}/export-pdf")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public org.springframework.http.ResponseEntity<byte[]> exportPdf(@PathVariable Long customerId,
            @RequestParam(required = false) String customerName) throws Exception {
        java.util.List<ReservationDTO> reservations = reservationService.getReservationsByCustomer(customerId);
        byte[] pdf = com.rentmyride.util.TripHistoryPdfGenerator.generateReservations(reservations,
                customerName != null ? customerName : "Customer #" + customerId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=booking-history.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PatchMapping("/{reservationId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateStatus(@PathVariable Long reservationId,
            @RequestBody ReservationDTO.StatusUpdateRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Status updated.",
                reservationService.updateReservationStatus(reservationId, request)));
    }

    // Customer pays in full, or a minimum ₹1000 deposit, to confirm a PENDING booking
    @PostMapping("/{reservationId}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> pay(@PathVariable Long reservationId,
            @Valid @RequestBody ReservationDTO.PayRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payment recorded.",
                reservationService.payForReservation(reservationId, request)));
    }

    // Reschedule an existing booking to new dates — free 12+ hours before original pickup, ₹300 fee otherwise
    @PatchMapping("/{reservationId}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> reschedule(@PathVariable Long reservationId,
            @Valid @RequestBody ReservationDTO.RescheduleRequest request) {
        ReservationDTO.RescheduleResponse result = reservationService.rescheduleReservation(reservationId, request);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(result.getMessage(), result));
    }

    // Free cancellation up to 12 hours before pickup; after that a ₹500 fee is deducted from the refund
    @PatchMapping("/{reservationId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> cancel(@PathVariable Long reservationId) {
        ReservationDTO.CancelResponse result = reservationService.cancelReservation(reservationId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(result.getMessage(), result));
    }
}
