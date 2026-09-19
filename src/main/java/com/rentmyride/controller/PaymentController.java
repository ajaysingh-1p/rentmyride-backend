package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.PaymentDTO;
import com.rentmyride.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    // LEGACY — the pre-reservation-flow rental-based payment path. Its baseAmount doesn't
    // include GST at the source (unlike reservation pricing, which is fully GST-inclusive), so
    // it separately adds 18% on top here — that's intentional FOR THIS FLOW, but mixing it with
    // the newer reservation-flow (which shows GST-inclusive totals with no separate GST line)
    // confused both customers ("I paid ₹3500 but it shows ₹3325 + GST") and admin ("why does
    // this payment still say PENDING"). Nothing in the current UI calls this anymore (see
    // MyBookings.jsx's "Pay Now" button, which now routes through the reservation flow instead)
    // — restricted to ADMIN only, kept only for potential manual/walk-in payment bookkeeping.
    @PostMapping("/create-order")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> createOrder(@RequestBody PaymentDTO.InitiateRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Order created.", paymentService.createRazorpayOrder(request)));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> verifyPayment(@RequestBody PaymentDTO.VerifyRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payment verified.", paymentService.verifyAndSavePayment(request)));
    }

    // ── Booking-confirmation payment (full or ₹1000+ deposit) via Razorpay ──
    @PostMapping("/reservation/create-order")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> createReservationOrder(@RequestBody PaymentDTO.ReservationOrderRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Order created.",
                paymentService.createReservationOrder(request)));
    }

    @PostMapping("/reservation/verify")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> verifyReservationPayment(@RequestBody PaymentDTO.ReservationVerifyRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payment verified. Booking confirmed.",
                paymentService.verifyReservationPayment(request)));
    }

    // ── New booking, paid upfront ("create only after payment") — nothing is saved to the
    // database until verify() succeeds. See PaymentServiceImpl for the full explanation. ──
    @PostMapping("/new-booking/create-order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> createNewBookingOrder(@RequestBody PaymentDTO.NewBookingOrderRequest request) {
        Long customerId = com.rentmyride.security.SecurityUtils.currentUser().getUserId();
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Order created.",
                paymentService.createNewBookingOrder(customerId, request)));
    }

    @PostMapping("/new-booking/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> verifyNewBookingPayment(@RequestBody PaymentDTO.NewBookingVerifyRequest request) {
        Long customerId = com.rentmyride.security.SecurityUtils.currentUser().getUserId();
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payment verified. Booking created.",
                paymentService.verifyNewBookingPayment(customerId, request)));
    }

    // Razorpay calls this directly (server-to-server) — not the customer's browser, so it must
    // stay outside JWT auth (permitAll in SecurityConfig) and instead trusts Razorpay's own HMAC
    // signature in the X-Razorpay-Signature header (issue #18).
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String rawPayload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        paymentService.handleWebhookEvent(rawPayload, signature);
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getById(@PathVariable Long paymentId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payment fetched.", paymentService.getPaymentById(paymentId)));
    }

    @GetMapping("/rental/{rentalId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByRental(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payment by rental.", paymentService.getPaymentByRentalId(rentalId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All payments.", paymentService.getAllPayments()));
    }

    // Issue #25 — paginated version.
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Payments page.",
                paymentService.getAllPaymentsPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customer payments.", paymentService.getPaymentsByCustomer(customerId)));
    }
}
