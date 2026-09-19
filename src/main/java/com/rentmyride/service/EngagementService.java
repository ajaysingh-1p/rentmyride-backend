package com.rentmyride.service;

import com.rentmyride.dtos.EngagementDTO;

import java.util.List;

public interface EngagementService {
    EngagementDTO.Summary getSummary(Long customerId);
    List<EngagementDTO.LeaderboardEntry> getReferralLeaderboard();
    void recordBookingIntent(Long customerId, Long carId);
    void clearBookingIntent(Long customerId, Long carId);
    double getLoyaltyDiscountPct(Long customerId);
    // Call this right after a rental completes — awards a wallet-credit bonus the first time
    // a customer hits a new 3/6/9... consecutive-month booking streak milestone.
    void checkAndAwardStreakReward(Long customerId);
}
