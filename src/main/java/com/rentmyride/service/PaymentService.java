package com.rentmyride.service;

import com.rentmyride.dtos.PaymentDTO;
import com.rentmyride.entities.Payment;
import java.util.List;

public interface PaymentService {
    PaymentDTO.RazorpayOrderResponse createRazorpayOrder(PaymentDTO.InitiateRequest request);
    PaymentDTO verifyAndSavePayment(PaymentDTO.VerifyRequest request);

    // Booking-confirmation payment (full or ₹1000+ deposit) — scoped to a Reservation, not a Rental
    PaymentDTO.RazorpayOrderResponse createReservationOrder(PaymentDTO.ReservationOrderRequest request);
    com.rentmyride.dtos.ReservationDTO verifyReservationPayment(PaymentDTO.ReservationVerifyRequest request);

    // New feature: "create only after payment" — order/verify for a booking that doesn't exist
    // in the database yet (see ReservationServiceImpl.createReservationWithPayment).
    PaymentDTO.RazorpayOrderResponse createNewBookingOrder(Long customerId, PaymentDTO.NewBookingOrderRequest request);
    com.rentmyride.dtos.ReservationDTO verifyNewBookingPayment(Long customerId, PaymentDTO.NewBookingVerifyRequest request);

    // Server-to-server Razorpay webhook (issue #18) — the source of truth for payment status,
    // independent of whether the customer's browser stuck around to fire the checkout callback.
    void handleWebhookEvent(String rawPayload, String signatureHeader);

    PaymentDTO getPaymentById(Long paymentId);
    PaymentDTO getPaymentByRentalId(Long rentalId);
    List<PaymentDTO> getAllPayments();
    com.rentmyride.dtos.PageResponse<PaymentDTO> getAllPaymentsPaged(int page, int size);
    List<PaymentDTO> getPaymentsByCustomer(Long customerId);
    List<PaymentDTO> getPaymentsByStatus(Payment.PaymentStatus status);
    Double getTotalCollected();
    Double getMonthlyCollection(int month, int year);
}
