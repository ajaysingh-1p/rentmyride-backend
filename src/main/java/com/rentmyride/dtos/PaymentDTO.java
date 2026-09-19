package com.rentmyride.dtos;

import com.rentmyride.entities.Payment;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDTO {

    private Long paymentId;
    private Long rentalId;
    // New — populated for booking-time (deposit/full) payments made against a Reservation,
    // before any Rental exists yet.
    private Long reservationId;
    private Long customerId;
    private String customerName;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
    private Double baseAmount;
    private Double gstPercentage;
    private Double gstAmount;
    private Double totalAmount;
    private Payment.PaymentMethod paymentMethod;
    private Payment.PaymentStatus paymentStatus;
    private String transactionReference;
    private LocalDateTime paymentDatetime;
    private String failureReason;

    // Initiate Payment Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InitiateRequest {
        private Long rentalId;
        private Payment.PaymentMethod paymentMethod;
    }

    // Razorpay Verify Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyRequest {
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
    }

    // Razorpay Order Response
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RazorpayOrderResponse {
        private String orderId;
        private Double amount;
        private String currency;
        private String keyId;
        private String customerName;
        private String customerEmail;
        private String customerContact;
        // Bug fix: when the customer's wallet balance covers the entire trip cost, there is
        // nothing left for Razorpay to charge — walletOnly=true tells the frontend to skip
        // opening the Razorpay checkout entirely (an order for ₹0 gets rejected by Razorpay's
        // own minimum-amount rule anyway) and go straight to the already-confirmed booking
        // carried in `reservation`. orderId/keyId are null in that case.
        private boolean walletOnly;
        private ReservationDTO reservation;
    }

    // ── Reservation-based booking payment (full or deposit, paid via Razorpay) ──
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationOrderRequest {
        private Long reservationId;
        private com.rentmyride.entities.Reservation.PaymentType paymentType; // FULL or DEPOSIT
        private Double amount; // required when paymentType = DEPOSIT
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationVerifyRequest {
        private Long reservationId;
        private com.rentmyride.entities.Reservation.PaymentType paymentType;
        private Double amount;
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
    }

    // ── New booking, paid upfront ("create only after payment") ──
    // Carries the full booking form — nothing is saved to the database yet at this point, this
    // only creates a Razorpay order for the correct (server-computed) amount.
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewBookingOrderRequest {
        private com.rentmyride.dtos.ReservationDTO.CreateRequest booking;
        private com.rentmyride.entities.Reservation.PaymentType paymentType;
        private Double amount; // required when paymentType = DEPOSIT
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewBookingVerifyRequest {
        private com.rentmyride.dtos.ReservationDTO.CreateRequest booking;
        private com.rentmyride.entities.Reservation.PaymentType paymentType;
        private Double amount;
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
    }
}
