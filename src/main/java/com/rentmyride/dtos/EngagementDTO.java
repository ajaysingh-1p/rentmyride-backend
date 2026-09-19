package com.rentmyride.dtos;

import lombok.*;

import java.util.List;

public class EngagementDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private String loyaltyTier;         // BRONZE, SILVER, GOLD
        private double loyaltyDiscountPct;   // applied automatically at booking
        private int completedTrips;
        private int tripsToNextTier;         // 0 if already at the top tier
        private List<Badge> badges;
        private int currentStreakMonths;     // consecutive months with a completed trip
        private boolean streakRewardEligible; // true right after a new 3-month milestone is hit
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Badge {
        private String code;
        private String label;
        private String emoji;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaderboardEntry {
        private Long customerId;
        private String name;
        private long referredCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingIntentRequest {
        private Long customerId;
        private Long carId;
    }
}
