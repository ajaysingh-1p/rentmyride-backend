package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
// Performance fix: added indexes on the columns actually filtered/sorted by in the hot paths —
// the double-booking conflict check (car_id + date range), driver-assignment conflict check
// (driver_id + date range), and admin's status filter/sort — none of which had an index before
// beyond MySQL's automatic one on the raw FK columns themselves.
@Table(name = "reservation", indexes = {
        @Index(name = "idx_reservation_car_dates", columnList = "car_id, pickup_date, return_date"),
        @Index(name = "idx_reservation_driver_dates", columnList = "driver_id, pickup_date, return_date"),
        @Index(name = "idx_reservation_status", columnList = "reservation_status"),
        @Index(name = "idx_reservation_customer", columnList = "customer_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    // Set by admin — the driver responsible for this booking's pickup/drop
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver assignedDriver;

    // New feature: Driver Trip Accept/Reject. A driver assignment is no longer final the moment
    // an admin picks a driver — the driver themselves confirms they can actually take the trip
    // (they might be double-booked in ways the system can't see, sick, etc.) before the customer
    // is told a specific driver is confirmed.
    @Enumerated(EnumType.STRING)
    @Column(name = "driver_assignment_status")
    private DriverAssignmentStatus driverAssignmentStatus;

    @Column(name = "driver_rejection_reason", length = 255)
    private String driverRejectionReason;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    // Used (with pickupDate) to enforce the 12-hour free-cancellation window
    @Column(name = "pickup_time", nullable = false)
    private LocalTime pickupTime;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    // LOCAL = customer stays within their home city/district (flat package rate)
    // OUTSTATION = travelling to another district (round-trip distance + night charges)
    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false)
    private TripType tripType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_mode", nullable = false)
    @Builder.Default
    private TripMode tripMode = TripMode.WITH_DRIVER;

    // Snapshotted from SelfDriveConfig.securityDeposit at booking time — deliberately NOT a
    // live lookup, so an admin editing the deposit later doesn't retroactively change what an
    // already-confirmed customer owes/gets refunded.
    @Column(name = "self_drive_deposit")
    private Double selfDriveDeposit;

    @Column(name = "pickup_location", nullable = false, length = 200)
    private String pickupLocation;

    @Column(name = "drop_location", nullable = false, length = 200)
    private String dropLocation;

    // Optional extra stops between pickup and drop, stored as a comma-separated list of location names
    @Column(name = "via_locations", length = 500)
    private String viaLocations;

    // Round-trip road distance (pickup → via stops → drop → back to pickup) — OUTSTATION only
    @Column(name = "distance_km")
    private Double distanceKm;

    // Number of nights the car stays out — 0 if same-day return
    @Column(name = "nights")
    private Integer nights;

    @Column(name = "base_fare")
    private Double baseFare;

    @Column(name = "night_charges")
    private Double nightCharges;

    @Column(name = "estimated_amount", nullable = false)
    private Double estimatedAmount;

    @Column(name = "promo_code", length = 30)
    private String promoCode;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "wallet_credits_used")
    private Double walletCreditsUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false)
    private ReservationStatus reservationStatus;

    // ── Booking-time payment (full payment OR minimum ₹1000 deposit) ──
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private BookingPaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType; // FULL or DEPOSIT — set once the customer pays

    @Column(name = "amount_paid", nullable = false)
    private Double amountPaid;

    // The most recent successful Razorpay payment id for this reservation (set in
    // payForReservation() when a real Razorpay payment is verified) — needed so cancelReservation()
    // can call Razorpay's actual refund API instead of only flipping a DB status (issue #16).
    private String razorpayPaymentId;

    // ── Cancellation ──
    @Column(name = "cancellation_fee")
    private Double cancellationFee;

    @Column(name = "refund_amount")
    private Double refundAmount;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "special_requests", length = 500)
    private String specialRequests;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.reservationStatus = ReservationStatus.PENDING;
        this.paymentStatus = BookingPaymentStatus.UNPAID;
        this.amountPaid = 0.0;
        // Bug fix: this used to be a raw calendar-day DIFFERENCE (pickup=today, return=tomorrow
        // → 1), which undercounts how many days the car is actually held for. A pickup today and
        // a return tomorrow spans TWO calendar days, so the customer is billed for and shown "2
        // days", not "1" — hence the +1 (inclusive day count). Same-day pickup/return still
        // correctly comes out to 1 day (0 + 1).
        this.totalDays = (int) (returnDate.toEpochDay() - pickupDate.toEpochDay()) + 1;
        // estimatedAmount/baseFare/nightCharges/distanceKm/nights are calculated by the
        // service layer and set on the builder before save, so they are NOT overwritten here.
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Bug fix: this reverse-mappedBy OneToOne field was NEVER actually read anywhere in the
    // codebase (verified — zero calls to reservation.getRental()) — its mere presence, though,
    // means Lombok's @Data-generated equals()/hashCode()/toString() on Reservation touch it,
    // and Hibernate CANNOT resolve a mappedBy OneToOne from its identity-map cache the way it
    // can for a owning-side reference — it must issue a fresh "SELECT ... FROM rental WHERE
    // reservation_id = ?" query every single time, even for a Rental object already sitting in
    // the same persistence context. That's exactly the "same thing queried 2-3 times for no
    // reason" pattern — removing the unused field removes the entire risk of it firing at all.
    // Enums
    public enum ReservationStatus {
        PENDING, CONFIRMED, CANCELLED, COMPLETED, REJECTED
    }

    public enum TripType {
        LOCAL, OUTSTATION
    }

    // Self-drive feature: WITH_DRIVER (default, existing behaviour — a driver is assigned by
    // admin after booking) vs SELF_DRIVE (customer drives themselves; no driver assignment
    // happens for this reservation, and a security deposit — snapshotted from SelfDriveConfig
    // at booking time — applies instead).
    public enum TripMode {
        WITH_DRIVER, SELF_DRIVE
    }

    // Driver Trip Accept/Reject feature
    public enum DriverAssignmentStatus {
        NONE,      // no driver assigned yet
        PENDING,   // assigned by admin, awaiting the driver's response
        ACCEPTED,  // driver confirmed they'll take the trip
        REJECTED   // driver declined — admin needs to assign someone else
    }

    public enum BookingPaymentStatus {
        UNPAID, DEPOSIT_PAID, FULLY_PAID, REFUNDED, PARTIALLY_REFUNDED
    }

    public enum PaymentType {
        FULL, DEPOSIT
    }
}
