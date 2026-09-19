package com.rentmyride.dtos;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessSettingsDTO {

    private String companyName;
    private String gstin;
    private Double cgstPercentage;
    private Double sgstPercentage;
    private String supportEmail;
    private String supportPhone;
    private String address;
    private String supportHours;
    private String homeBaseLocation;

    private Double cancellationFee;
    private Double rescheduleFee;
    private Integer freeCancellationWindowHours;
    private Double minDepositAmount;

    private Double referralBonus;
    private Double streakRewardAmount;
    private Integer streakMilestoneMonths;
    private Integer silverTierMinTrips;
    private Integer goldTierMinTrips;
    private Double silverDiscountPct;
    private Double goldDiscountPct;

    private Double outstationMinKmPerDay;

    private Double localFreeKmLimit;
    private Double extraKmSlabSize;
    private Double extraKmSlabCharge;
    private Double localFallbackRatePerDay;

    // Issues #20/#21
    private Double damageApprovalThreshold;
    private Double maxKmPerDaySanityLimit;

    private LocalDateTime updatedAt;
}
