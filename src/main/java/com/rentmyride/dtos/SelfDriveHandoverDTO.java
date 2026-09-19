package com.rentmyride.dtos;

import com.rentmyride.entities.Reservation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Self-drive handover (Option A — OTP based).
 *
 * A self-drive booking has NO driver, so the physical car handover happens between the
 * ADMIN/staff at the hub and the CUSTOMER. Neither side can move the booking forward alone:
 *
 *   PICKUP : customer taps "Start Trip" in their app -> gets a 6-digit OTP on screen ->
 *            reads it out to the admin, who enters it on the handover form along with the
 *            odometer/fuel reading. The OTP proves the right customer is physically present.
 *   RETURN : customer taps "Return Car" -> gets a fresh OTP -> admin enters it on the return
 *            form with the closing odometer/fuel/damage. Then the deposit is settled.
 *
 * Nothing here is hardcoded — free km, overage rate and late penalty all come from the
 * admin-configured SelfDriveConfig for that car's category (see SelfDriveHandoverServiceImpl).
 */
public class SelfDriveHandoverDTO {

    // ── Customer side ────────────────────────────────────────────────────────

    /** What the customer sees after tapping "Start Trip" / "Return Car". */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpResponse {
        private String otp;                 // shown on the customer's screen — NOT emailed
        private int expiresInMinutes;
        private String stage;               // "PICKUP" or "RETURN"
        private String instruction;         // human-readable line for the UI
    }

    /** The customer's live self-drive trip card (allowance, deposit held, etc). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveTripResponse {
        private Long rentalId;
        private Long reservationId;
        private String carBrand;
        private String carModel;
        private String carRegistrationNumber;
        private LocalDateTime actualPickupDatetime;
        private LocalDateTime scheduledReturnDatetime;
        private Double odometerAtPickup;
        private Integer totalDays;
        private Double freeKmPerDay;
        private Double freeKmAllowance;     // freeKmPerDay * totalDays
        private Double overageRatePerKm;
        private Double lateReturnPenaltyPerHour;
        private Double depositHeld;
    }

    // ── Admin side ───────────────────────────────────────────────────────────

    /** A self-drive booking waiting to be handed over today. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingHandover {
        private Long reservationId;
        private Long customerId;
        private String customerName;
        private String customerMobile;
        private String carBrand;
        private String carModel;
        private String carRegistrationNumber;
        private LocalDate pickupDate;
        private LocalTime pickupTime;
        private LocalDate returnDate;
        private String pickupLocation;
        private Double estimatedAmount;
        private Double amountPaid;
        private Double depositHeld;
        private boolean fullyPaid;          // handover is blocked unless this is true
    }

    /** A self-drive car that's out with a customer right now. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveHandover {
        private Long rentalId;
        private Long reservationId;
        private String customerName;
        private String customerMobile;
        private String carBrand;
        private String carModel;
        private String carRegistrationNumber;
        private LocalDateTime actualPickupDatetime;
        private LocalDateTime scheduledReturnDatetime;
        private Double odometerAtPickup;
        private Double freeKmAllowance;
        private Double overageRatePerKm;
        private Double depositHeld;
        private boolean overdue;
        private java.util.List<String> pickupPhotoUrls;
    }

    /** Admin submits this to start the trip (after reading the customer's OTP). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickupRequest {
        private Long reservationId;
        private String otp;
        private Double odometerAtPickup;
        private Integer fuelLevelAtPickup;   // 0-100 %
        private String remarks;
        // URLs from POST /api/files/upload (type=handover) — 4-6 exterior/interior shots taken
        // at the counter, so a later damage dispute has more than one admin's word to go on.
        private java.util.List<String> photoUrls;
    }

    /** Admin submits this to close the trip (after reading the customer's return OTP). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnRequest {
        private Long rentalId;
        private String otp;
        private Double odometerAtReturn;
        private Integer fuelLevelAtReturn;   // 0-100 %
        private Double damageCharges;        // 0 / null if the car came back clean
        private LocalDateTime actualReturnDatetime; // null -> now
        private String remarks;
        // Same idea as PickupRequest.photoUrls — critical here specifically, since this is
        // where damageCharges gets entered and needs something to back it up.
        private java.util.List<String> photoUrls;
    }

    /** Full deposit settlement breakdown, shown to admin and customer after return. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementResponse {
        private Long rentalId;
        private Reservation.TripMode tripMode;
        private Double kmDriven;
        private Double freeKmAllowance;
        private Double extraKm;
        private Double extraKmCharges;
        private Long lateHours;
        private Double lateReturnCharges;
        private Double damageCharges;
        private Double fareAmount;           // the trip fare, deposit excluded
        private Double totalDeductions;      // extraKm + late + damage
        private Double depositHeld;
        private Double depositRefunded;      // credited to the customer's wallet
        private Double extraAmountDue;       // if deductions exceeded the deposit
        private Double finalBillAmount;      // fare + deductions
        private String summary;
        private java.util.List<String> pickupPhotoUrls;
        private java.util.List<String> returnPhotoUrls;
        private String depositRefundMethod;  // "WALLET" or "RAZORPAY" — null if nothing was refunded
    }
}
