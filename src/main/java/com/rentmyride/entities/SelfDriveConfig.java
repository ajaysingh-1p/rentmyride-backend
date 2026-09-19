package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// One row per Car.CarCategory. Everything an admin needs to control about self-drive
// bookings for that category lives here so nothing is hardcoded in the service layer —
// security deposit, free km/day, overage rate, late-return penalty and refund window are
// all editable from the admin panel (see SelfDriveConfigController).
@Entity
@Table(name = "self_drive_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfDriveConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    // One config per car category — enforced unique so there's exactly one active row to look
    // up per category instead of ambiguous duplicates.
    @Enumerated(EnumType.STRING)
    @Column(name = "car_category", nullable = false, unique = true)
    private Car.CarCategory carCategory;

    // Refundable amount collected before a self-drive trip starts.
    @Column(name = "security_deposit", nullable = false)
    private Double securityDeposit;

    // Free kilometres included per day of the trip before overage charges apply.
    @Column(name = "free_km_per_day", nullable = false)
    private Double freeKmPerDay;

    // ₹ charged per km once the free limit is crossed. Falls back to the car's own
    // ratePerKm in the service layer if an admin leaves this null for a category.
    @Column(name = "overage_rate_per_km")
    private Double overageRatePerKm;

    // ₹ charged per hour the car is returned late, deducted from the deposit.
    @Column(name = "late_return_penalty_per_hour", nullable = false)
    private Double lateReturnPenaltyPerHour;

    // How many days after a clean return the deposit refund is processed.
    @Column(name = "refund_window_days", nullable = false)
    private Integer refundWindowDays;

    // Whether self-drive is even offered for this category right now — lets admin turn it
    // off for e.g. LUXURY without deleting the pricing config underneath it.
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
