package com.rentmyride.dtos;

import com.rentmyride.entities.Reservation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDTO {

    private Long reservationId;
    private Long customerId;
    private String customerName;
    private String customerMobile;
    private Long carId;
    private String carBrand;
    private String carModel;
    private String carRegistrationNumber;
    private Long assignedDriverId;
    private String assignedDriverName;
    // New feature: Driver Trip Accept/Reject
    private Reservation.DriverAssignmentStatus driverAssignmentStatus;
    private String driverRejectionReason;
    private String assignedDriverMobile;
    private LocalDate pickupDate;
    private LocalTime pickupTime;
    private LocalDate returnDate;
    private Integer totalDays;
    private Reservation.TripType tripType;
    private String pickupLocation;
    private String dropLocation;
    private String viaLocations;
    private Double distanceKm;
    private Integer nights;
    private Double baseFare;
    private Double nightCharges;
    private String promoCode;
    private Double discountAmount;
    private Double walletCreditsUsed;
    private Double estimatedAmount;
    private Reservation.ReservationStatus reservationStatus;
    private Reservation.BookingPaymentStatus paymentStatus;
    private Reservation.PaymentType paymentType;
    private Double amountPaid;
    private Double balanceDue;
    private Double cancellationFee;
    private Double refundAmount;
    private String specialRequests;
    private LocalDateTime createdAt;
    private Reservation.TripMode tripMode;
    private Double selfDriveDeposit;

    // Create Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotNull(message = "Car id is required.")
        private Long carId;

        @NotNull(message = "Pickup date is required.")
        private LocalDate pickupDate;

        @NotNull(message = "Pickup time is required.")
        private LocalTime pickupTime;

        @NotNull(message = "Return date is required.")
        private LocalDate returnDate;

        @NotNull(message = "Trip type is required.")
        private Reservation.TripType tripType;

        @NotBlank(message = "Pickup location is required.")
        private String pickupLocation;

        @NotBlank(message = "Drop location is required.")
        private String dropLocation;

        private List<String> viaLocations; // optional extra stops, in order
        private String promoCode; // optional
        private boolean useWalletCredits; // apply referral wallet balance toward this booking
        private String specialRequests;
        // Self-drive feature: defaults to WITH_DRIVER when omitted, so existing clients that
        // don't send this field keep working exactly as before.
        private Reservation.TripMode tripMode;
    }

    // Live price preview before actually creating the reservation
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateRequest {
        private Long carId;
        private LocalDate pickupDate;
        private LocalDate returnDate;
        private Reservation.TripType tripType;
        private String pickupLocation;
        private String dropLocation;
        private List<String> viaLocations;
        private String promoCode;
        private Reservation.TripMode tripMode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EstimateResponse {
        private Reservation.TripType tripType;
        private Double distanceKm;     // round-trip distance, OUTSTATION only
        private Integer totalDays;
        private Integer nights;
        private Double ratePerKm;
        private Double baseFare;
        private Double nightCharges;
        private Double estimatedAmount;
        private String promoCode;
        private Double discountAmount;
        private Double finalAmount;
        private String pricingMethod; // "OUTSTATION_DISTANCE", "LOCAL_PACKAGE", or "PER_DAY" (fallback)
        // Self-drive feature — populated only when tripMode=SELF_DRIVE was requested and a
        // config exists for this car's category.
        private Reservation.TripMode tripMode;
        private Double selfDriveDeposit;
        private Double selfDriveFreeKmPerDay;
        // Bug fix (Razorpay amount mismatch): the loyalty-tier discount was applied server-side
        // when creating the order but never surfaced here, so the booking page showed one total
        // and Razorpay charged another. payableAmount is now the single number both use.
        private Double loyaltyDiscountPct;
        private Double loyaltyDiscountAmount;
        private Double payableAmount;   // fare − promo − loyalty + selfDriveDeposit
    }

    // Status Update Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateRequest {
        private Reservation.ReservationStatus reservationStatus;
        private String remarks;
    }

    // ── Booking payment (pay in full, or a minimum ₹1000 deposit to confirm) ──
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayRequest {
        @NotNull(message = "Payment type is required.")
        private Reservation.PaymentType paymentType; // FULL or DEPOSIT

        @Positive(message = "Amount must be a positive value.")
        private Double amount;                       // required when paymentType = DEPOSIT
        private String paymentMethod;                // UPI / CARD / NET_BANKING / etc (display only)
        // Set only when this payment was a verified Razorpay payment (see PaymentServiceImpl)
        // so a later cancellation can actually refund it via the Razorpay API (issue #16).
        private String razorpayPaymentId;

        public PayRequest(Reservation.PaymentType paymentType, Double amount, String paymentMethod) {
            this.paymentType = paymentType;
            this.amount = amount;
            this.paymentMethod = paymentMethod;
        }
    }

    // ── Reschedule an existing booking to new dates ──
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RescheduleRequest {
        @NotNull(message = "New pickup date is required.")
        private LocalDate newPickupDate;

        @NotNull(message = "New pickup time is required.")
        private LocalTime newPickupTime;

        @NotNull(message = "New return date is required.")
        private LocalDate newReturnDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RescheduleResponse {
        private ReservationDTO reservation;
        private boolean freeReschedule; // true if 12+ hours before the ORIGINAL pickup
        private Double rescheduleFee;
        private String message;
        // Bug fix: the new fare (based on THIS car's current rate for the new dates) used to be
        // silently saved onto the reservation with no way for the customer to see whether the
        // new dates cost more, less, or the same — the popup only ever showed the flat fee note.
        private Double oldAmount;
        private Double newAmount;
        private Double balanceDue;        // > 0 if the new dates cost more and payment is still owed
        private Double refundedToWallet;  // > 0 if the new dates cost less and the difference was refunded
    }

    // ── Cancellation result (shows the fee/refund applied) ──
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelResponse {
        private Long reservationId;
        private boolean freeCancellation; // true if cancelled 12+ hours before pickup
        private Double cancellationFee;
        private Double refundAmount;
        private String message;
    }

    // Shown on the car detail page so customers can check availability before booking
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BookedRange {
        private LocalDate pickupDate;
        private LocalDate returnDate;
        private Reservation.ReservationStatus status;
    }

    // Admin assigns a driver to a booking — triggers customer notification + email
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignDriverRequest {
        private Long driverId;
    }

    // New feature: Driver Trip Accept/Reject
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectTripRequest {
        private String reason; // optional — e.g. "vehicle unavailable", "personal emergency"
    }
}
