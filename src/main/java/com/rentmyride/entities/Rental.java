package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rental", indexes = {
        @Index(name = "idx_rental_status", columnList = "rental_status"),
        @Index(name = "idx_rental_driver", columnList = "driver_id"),
        @Index(name = "idx_rental_customer", columnList = "customer_id"),
        @Index(name = "idx_rental_damage_approval", columnList = "damage_approval_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_id")
    private Long rentalId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "actual_pickup_datetime")
    private LocalDateTime actualPickupDatetime;

    @Column(name = "actual_return_datetime")
    private LocalDateTime actualReturnDatetime;

    @Column(name = "odometer_at_pickup")
    private Double odometerAtPickup;

    @Column(name = "odometer_at_return")
    private Double odometerAtReturn;

    @Column(name = "total_km_driven")
    private Double totalKmDriven;

    @Column(name = "base_amount", nullable = false)
    private Double baseAmount;

    @Column(name = "extra_km_charges")
    private Double extraKmCharges;

    @Column(name = "damage_charges")
    private Double damageCharges;

    // Issue #21 fix: damage above BusinessSettings.damageApprovalThreshold is no longer applied
    // to the customer's bill purely on a Driver's say-so — it sits here pending an Admin's
    // approve/reject decision (see RentalServiceImpl.completeRental / approveDamageCharge).
    @Enumerated(EnumType.STRING)
    @Column(name = "damage_approval_status")
    private DamageApprovalStatus damageApprovalStatus;

    @Column(name = "pending_damage_charges")
    private Double pendingDamageCharges;

    @Column(name = "late_return_charges")
    private Double lateReturnCharges;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_status", nullable = false)
    private RentalStatus rentalStatus;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // ── Live tracking (updated by the driver's phone while the rental is ACTIVE) ──
    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;

    // Post-trip quick poll: "Would you book again?" — null until the customer answers
    @Column(name = "would_rebook")
    private Boolean wouldRebook;

    // ── Self-drive handover (driver is null in that flow) ──
    // Fuel noted at both ends so a shortfall can be justified against the deposit.
    @Column(name = "fuel_level_at_pickup")
    private Integer fuelLevelAtPickup;

    @Column(name = "fuel_level_at_return")
    private Integer fuelLevelAtReturn;

    // Deposit snapshotted here from the Reservation at handover time — if an admin later edits
    // the SelfDriveConfig, THIS trip's settlement must not change underneath the customer.
    @Column(name = "deposit_held")
    private Double depositHeld;

    @Column(name = "deposit_deductions")
    private Double depositDeductions;

    @Column(name = "deposit_refunded")
    private Double depositRefunded;

    // "WALLET" or "RAZORPAY" — which path SelfDriveHandoverServiceImpl actually used. Kept even
    // when self-drive.deposit-refund-method=RAZORPAY, because a failed Razorpay attempt falls
    // back to WALLET, and this column is how that fallback stays visible after the fact.
    @Column(name = "deposit_refund_method", length = 20)
    private String depositRefundMethod;

    // Comma-separated file URLs from /api/files/upload (type=handover) — a handful of exterior/
    // interior shots taken at the counter, so a damage dispute later has something to point to
    // beyond one admin's word against the customer's.
    @Column(name = "pickup_photos", length = 2000)
    private String pickupPhotos;

    @Column(name = "return_photos", length = 2000)
    private String returnPhotos;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.rentalStatus = RentalStatus.ACTIVE;
        this.extraKmCharges = 0.0;
        this.damageCharges = 0.0;
        this.lateReturnCharges = 0.0;
        this.discountAmount = 0.0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Bug fix: this reverse-mappedBy OneToOne field was never explicitly read anywhere in the
    // codebase (zero calls to rental.getInvoice()) — same issue as Reservation.rental above.
    // Its mere presence made Lombok's @Data equals()/hashCode()/toString() silently trigger a
    // fresh "SELECT ... FROM invoice WHERE rental_id = ?" query whenever a List<Rental> got
    // compared/logged/deduplicated anywhere (e.g. inside EngagementServiceImpl's streak-reward
    // check, which loads a customer's completed rentals) — one query PER rental in the list,
    // for a relationship nothing ever actually used. Removed entirely.
    // Enum
    public enum RentalStatus {
        ACTIVE, COMPLETED, CANCELLED, OVERDUE
    }

    public enum DamageApprovalStatus {
        NONE,             // no damage reported
        AUTO_APPROVED,    // damage was at/below the auto-approval threshold, applied immediately
        PENDING_APPROVAL, // above threshold — awaiting an Admin decision, not yet billed
        APPROVED,         // Admin approved — now applied to the bill
        REJECTED          // Admin rejected — never applied to the bill
    }
}
