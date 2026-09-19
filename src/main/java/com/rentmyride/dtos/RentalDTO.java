package com.rentmyride.dtos;

import com.rentmyride.entities.Rental;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalDTO {

    private Long rentalId;
    private Long reservationId;
    private Long customerId;
    private String customerName;
    private String customerMobile;
    private Long carId;
    private String carBrand;
    private String carModel;
    private String carRegistrationNumber;
    private Long driverId;
    private String driverName;
    private String pickupLocation;
    private String dropLocation;
    private com.rentmyride.entities.Reservation.TripType tripType;
    private LocalDateTime actualPickupDatetime;
    private LocalDateTime actualReturnDatetime;
    private Double odometerAtPickup;
    private Double odometerAtReturn;
    private Double totalKmDriven;
    private Double baseAmount;
    private Double extraKmCharges;
    private Double damageCharges;
    // Issue #21 — surfaces the pending-approval workflow to the frontend (Admin dashboard shows
    // a review queue; customer sees "under review" instead of an unexplained charge).
    private Rental.DamageApprovalStatus damageApprovalStatus;
    private Double pendingDamageCharges;
    private Double lateReturnCharges;
    private Double discountAmount;
    private Double totalAmount;
    private Rental.RentalStatus rentalStatus;
    private String remarks;
    private LocalDateTime createdAt;
    private java.time.LocalDate expectedReturnDate; // from the reservation — for the "time left" countdown

    // Live tracking
    private Double currentLat;
    private Double currentLng;
    private LocalDateTime locationUpdatedAt;
    private Boolean wouldRebook;

    // Driver's phone posts this periodically while the rental is ACTIVE
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationUpdateRequest {
        private Double lat;
        private Double lng;
    }

    // Post-trip "would you book again?" quick poll
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RebookPollRequest {
        private Boolean wouldRebook;
    }

    // Pickup Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PickupRequest {
        private Long reservationId;
        private Long driverId;
        private Double odometerAtPickup;
        private LocalDateTime actualPickupDatetime;
        private String remarks;
    }

    // Return Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReturnRequest {
        private Double odometerAtReturn;
        private LocalDateTime actualReturnDatetime;
        private Double damageCharges;
        private Double discountAmount;
        private String remarks;
    }

    // Extend an ACTIVE rental to a later return date
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtendRequest {
        private java.time.LocalDate newReturnDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtendResponse {
        private RentalDTO rental;
        private Double extraCharge;
        private java.time.LocalDate newReturnDate;
        private String message;
    }
}
