package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "mobile_number", nullable = false, unique = true, length = 15)
    private String mobileNumber;

    @Column(name = "alternate_mobile", length = 15)
    private String alternateMobile;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "driving_license_number", unique = true, length = 20)
    private String drivingLicenseNumber;

    @Column(name = "driving_license_expiry")
    private LocalDate drivingLicenseExpiry;

    @Column(name = "aadhar_number", unique = true, length = 12)
    private String aadharNumber;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "driving_license_image_url", length = 500)
    private String drivingLicenseImageUrl;

    @Column(name = "aadhar_image_url", length = 500)
    private String aadharImageUrl;

    // 0-100 — reflects booking reliability (completed rentals, on-time returns, feedback vs late cancellations)
    @Column(name = "trust_score", nullable = false)
    @Builder.Default
    private Integer trustScore = 0;

    // ── Referral Program ──
    @Column(name = "referral_code", unique = true, length = 20)
    private String referralCode;

    @Column(name = "referred_by_code", length = 20)
    private String referredByCode; // the code THIS customer used at signup, if any

    @Column(name = "wallet_balance", nullable = false)
    @Builder.Default
    private Double walletBalance = 0.0;

    // ── Engagement tracking ──
    // "yyyy-MM" of the month a booking-streak reward was last paid out, to avoid double-paying
    @Column(name = "last_streak_reward_month", length = 7)
    private String lastStreakRewardMonth;

    // When we last sent a "we miss you" re-engagement email, to avoid spamming
    @Column(name = "last_re_engagement_email_at")
    private LocalDateTime lastReEngagementEmailAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "role", nullable = false)
    private String role = "CUSTOMER";

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.accountStatus = AccountStatus.ACTIVE;
        this.role = "CUSTOMER";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Bug fix: reservations/rentals/payments were @OneToMany reverse-mappedBy fields never
    // actually read anywhere (Customer's own history is fetched via dedicated repository
    // queries like findByCustomer_CustomerId, not through these). Since Customer is the most
    // frequently loaded entity in the app (every authenticated request touches one), an
    // accidental equals()/hashCode()/toString() trigger here was the highest-risk site for
    // silent N+1 queries — removed.
    // Enums
    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum AccountStatus {
        ACTIVE, INACTIVE, BLOCKED, PENDING_VERIFICATION
    }
}
