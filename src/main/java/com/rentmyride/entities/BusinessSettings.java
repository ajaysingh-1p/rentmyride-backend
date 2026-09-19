package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Single-row config table. Always use settingsId = 1L.
 * Seeded on startup by DataSeeder from application.properties defaults
 * (gst.percentage / gst.cgst / gst.sgst) if the row does not already exist.
 */
@Entity
@Table(name = "business_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessSettings {

    @Id
    @Column(name = "settings_id")
    private Long settingsId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "gstin", nullable = false, length = 15)
    private String gstin;

    @Column(name = "cgst_percentage", nullable = false)
    private Double cgstPercentage;

    @Column(name = "sgst_percentage", nullable = false)
    private Double sgstPercentage;

    @Column(name = "support_email", nullable = false, length = 100)
    private String supportEmail;

    @Column(name = "support_phone", length = 15)
    private String supportPhone;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "support_hours", length = 100)
    private String supportHours;

    // City all cars are based in — where every trip's pickup starts from. Configurable
    // instead of hardcoded so the business can operate from a different city later.
    @Column(name = "home_base_location", length = 100)
    private String homeBaseLocation;

    // ── Cancellation / booking policy ──
    @Column(name = "cancellation_fee")
    private Double cancellationFee;

    @Column(name = "reschedule_fee")
    private Double rescheduleFee;

    @Column(name = "free_cancellation_window_hours")
    private Integer freeCancellationWindowHours;

    @Column(name = "min_deposit_amount")
    private Double minDepositAmount;

    // ── Referral & loyalty ──
    @Column(name = "referral_bonus")
    private Double referralBonus;

    @Column(name = "streak_reward_amount")
    private Double streakRewardAmount;

    @Column(name = "streak_milestone_months")
    private Integer streakMilestoneMonths;

    @Column(name = "silver_tier_min_trips")
    private Integer silverTierMinTrips;

    @Column(name = "gold_tier_min_trips")
    private Integer goldTierMinTrips;

    @Column(name = "silver_discount_pct")
    private Double silverDiscountPct;

    @Column(name = "gold_discount_pct")
    private Double goldDiscountPct;

    // ── OUTSTATION trip pricing ──
    // A real cab operator's outstation package guarantees a minimum billable distance per day
    // (Ola/Uber-style "250km/day" packages) — a 3-day trip is billed for at least
    // 3 × outstationMinKmPerDay even if the actual round-trip route is shorter, and for the
    // ACTUAL km driven if that turns out to be more. Protects revenue on short-route multi-day
    // trips where the car (and driver) is committed for the whole period regardless of distance.
    @Column(name = "outstation_min_km_per_day")
    private Double outstationMinKmPerDay;

    // ── LOCAL trip pricing ──
    @Column(name = "local_free_km_limit")
    private Double localFreeKmLimit;

    @Column(name = "extra_km_slab_size")
    private Double extraKmSlabSize;

    @Column(name = "extra_km_slab_charge")
    private Double extraKmSlabCharge;

    @Column(name = "local_fallback_rate_per_day")
    private Double localFallbackRatePerDay;

    // Damage charges reported by a Driver above this amount require Admin approval before being
    // billed to the customer (issue #21) — a Driver alone can no longer levy an unlimited charge.
    @Column(name = "damage_approval_threshold")
    private Double damageApprovalThreshold;

    // Sanity cap (issue #20): flags/rejects an implausible odometer reading, e.g. a typo like an
    // extra digit, instead of silently billing the customer for thousands of "extra" km. Expressed
    // as max km per day of the rental.
    @Column(name = "max_km_per_day_sanity_limit")
    private Double maxKmPerDaySanityLimit;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
