package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_id")
    private Long otpId;

    // Email (or mobile) the OTP was sent to
    @Column(name = "identifier", nullable = false, length = 100)
    private String identifier;

    @Column(name = "otp_code", nullable = false, length = 10)
    private String otpCode;

    // Bug fix: with no explicit columnDefinition, Hibernate's MySQL dialect mapped this to a
    // NATIVE MySQL ENUM(...) column matching whatever OtpPurpose values existed when the table
    // was first created — just LOGIN and FORGOT_PASSWORD. ddl-auto=update only ADDS missing
    // columns/tables, it does NOT alter an existing enum column's value list, so adding
    // SELF_DRIVE_PICKUP/SELF_DRIVE_RETURN to the Java enum silently did nothing to the DB column.
    // Every insert with one of the new values then failed with "Data truncated for column
    // 'purpose'" (MySQL's actual error when a value outside the column's fixed enum set is
    // inserted). Explicit VARCHAR sidesteps this permanently — plain string storage has no fixed
    // value list to fall out of sync with the Java enum ever again.
    // NOTE: this does NOT retroactively fix an existing dev database, since the physical column
    // there is already a native ENUM — run this once against it:
    //   ALTER TABLE otp_verification MODIFY COLUMN purpose VARCHAR(30) NOT NULL;
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, columnDefinition = "VARCHAR(30)")
    private OtpPurpose purpose;

    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.verified = false;
        this.attempts = 0;
    }

    public enum OtpPurpose {
        LOGIN,
        FORGOT_PASSWORD,
        // Self-drive handover codes are NOT emailed — they're shown on the customer's own
        // screen and read out to the staff at the counter. Kept as separate purposes so a
        // handover code and a login code for the same identifier can never consume each other.
        SELF_DRIVE_PICKUP,
        SELF_DRIVE_RETURN
    }
}
