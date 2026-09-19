package com.rentmyride.service.impl;

import com.rentmyride.dtos.BusinessSettingsDTO;
import com.rentmyride.entities.BusinessSettings;
import com.rentmyride.repository.BusinessSettingsRepository;
import com.rentmyride.service.BusinessSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessSettingsServiceImpl implements BusinessSettingsService {

    private static final Long SINGLETON_ID = 1L;

    private final BusinessSettingsRepository businessSettingsRepository;

    @Value("${gst.cgst}")
    private Double defaultCgst;

    @Value("${gst.sgst}")
    private Double defaultSgst;

    // Perf fix: getSettings() is called from 5+ different services, sometimes 2-3 times within a
    // SINGLE request (e.g. completeRental() → checkAndAwardStreakReward() → generateInvoice() each
    // fetch it independently) — for one row that an admin updates maybe once a month. @Cacheable
    // means only the FIRST call per cache-lifetime actually hits the database; every call after
    // that (across ALL requests, not just one) returns the cached copy instantly. @CacheEvict on
    // updateSettings() below clears it the moment an admin actually changes something, so the
    // cache can never serve stale settings.
    @Override
    @Transactional
    @Cacheable("businessSettings")
    public BusinessSettingsDTO getSettings() {
        return mapToDTO(getOrCreate());
    }

    @Override
    @Transactional
    @CacheEvict(value = "businessSettings", allEntries = true)
    public BusinessSettingsDTO updateSettings(BusinessSettingsDTO dto) {
        BusinessSettings settings = getOrCreate();
        settings.setCompanyName(dto.getCompanyName());
        settings.setGstin(dto.getGstin());
        settings.setCgstPercentage(dto.getCgstPercentage());
        settings.setSgstPercentage(dto.getSgstPercentage());
        settings.setSupportEmail(dto.getSupportEmail());
        settings.setSupportPhone(dto.getSupportPhone());
        settings.setAddress(dto.getAddress());
        settings.setSupportHours(dto.getSupportHours());
        settings.setHomeBaseLocation(dto.getHomeBaseLocation());

        settings.setCancellationFee(dto.getCancellationFee());
        settings.setRescheduleFee(dto.getRescheduleFee());
        settings.setFreeCancellationWindowHours(dto.getFreeCancellationWindowHours());
        settings.setMinDepositAmount(dto.getMinDepositAmount());

        settings.setReferralBonus(dto.getReferralBonus());
        settings.setStreakRewardAmount(dto.getStreakRewardAmount());
        settings.setStreakMilestoneMonths(dto.getStreakMilestoneMonths());
        settings.setSilverTierMinTrips(dto.getSilverTierMinTrips());
        settings.setGoldTierMinTrips(dto.getGoldTierMinTrips());
        settings.setSilverDiscountPct(dto.getSilverDiscountPct());
        settings.setGoldDiscountPct(dto.getGoldDiscountPct());

        settings.setOutstationMinKmPerDay(dto.getOutstationMinKmPerDay());
        settings.setLocalFreeKmLimit(dto.getLocalFreeKmLimit());
        settings.setExtraKmSlabSize(dto.getExtraKmSlabSize());
        settings.setExtraKmSlabCharge(dto.getExtraKmSlabCharge());
        settings.setLocalFallbackRatePerDay(dto.getLocalFallbackRatePerDay());
        settings.setDamageApprovalThreshold(dto.getDamageApprovalThreshold());
        settings.setMaxKmPerDaySanityLimit(dto.getMaxKmPerDaySanityLimit());

        return mapToDTO(businessSettingsRepository.save(settings));
    }

    // Defaults here match what used to be hardcoded Java constants across the codebase —
    // so behavior is identical on first deploy, until an admin actually changes them.
    private BusinessSettings getOrCreate() {
        return businessSettingsRepository.findById(SINGLETON_ID)
                .orElseGet(() -> businessSettingsRepository.save(
                        BusinessSettings.builder()
                                .settingsId(SINGLETON_ID)
                                .companyName("RentMyRide")
                                .gstin("")
                                .cgstPercentage(defaultCgst)
                                .sgstPercentage(defaultSgst)
                                .supportEmail("support@rentmyride.com")
                                .supportPhone("")
                                .address("")
                                .supportHours("9:00 AM - 9:00 PM, All Days")
                                .homeBaseLocation("Patna")
                                .cancellationFee(500.0)
                                .rescheduleFee(300.0)
                                .freeCancellationWindowHours(12)
                                .minDepositAmount(1000.0)
                                .referralBonus(200.0)
                                .streakRewardAmount(250.0)
                                .streakMilestoneMonths(3)
                                .silverTierMinTrips(5)
                                .goldTierMinTrips(15)
                                .silverDiscountPct(5.0)
                                .goldDiscountPct(10.0)
                                .outstationMinKmPerDay(200.0)
                                .localFreeKmLimit(200.0)
                                .extraKmSlabSize(100.0)
                                .extraKmSlabCharge(1500.0)
                                .localFallbackRatePerDay(2600.0)
                                .damageApprovalThreshold(5000.0)
                                .maxKmPerDaySanityLimit(600.0)
                                .build()));
    }

    // Null-safe fallbacks cover rows saved before these columns existed (ddl-auto adds the
    // column but leaves existing rows null) — same defaults as getOrCreate() above.
    private BusinessSettingsDTO mapToDTO(BusinessSettings s) {
        return BusinessSettingsDTO.builder()
                .companyName(s.getCompanyName())
                .gstin(s.getGstin())
                .cgstPercentage(s.getCgstPercentage())
                .sgstPercentage(s.getSgstPercentage())
                .supportEmail(s.getSupportEmail())
                .supportPhone(s.getSupportPhone())
                .address(s.getAddress())
                .supportHours(s.getSupportHours())
                .homeBaseLocation(orDefault(s.getHomeBaseLocation(), "Patna"))
                .cancellationFee(orDefault(s.getCancellationFee(), 500.0))
                .rescheduleFee(orDefault(s.getRescheduleFee(), 300.0))
                .freeCancellationWindowHours(orDefault(s.getFreeCancellationWindowHours(), 12))
                .minDepositAmount(orDefault(s.getMinDepositAmount(), 1000.0))
                .referralBonus(orDefault(s.getReferralBonus(), 200.0))
                .streakRewardAmount(orDefault(s.getStreakRewardAmount(), 250.0))
                .streakMilestoneMonths(orDefault(s.getStreakMilestoneMonths(), 3))
                .silverTierMinTrips(orDefault(s.getSilverTierMinTrips(), 5))
                .goldTierMinTrips(orDefault(s.getGoldTierMinTrips(), 15))
                .silverDiscountPct(orDefault(s.getSilverDiscountPct(), 5.0))
                .goldDiscountPct(orDefault(s.getGoldDiscountPct(), 10.0))
                .outstationMinKmPerDay(orDefault(s.getOutstationMinKmPerDay(), 200.0))
                .localFreeKmLimit(orDefault(s.getLocalFreeKmLimit(), 200.0))
                .extraKmSlabSize(orDefault(s.getExtraKmSlabSize(), 100.0))
                .extraKmSlabCharge(orDefault(s.getExtraKmSlabCharge(), 1500.0))
                .localFallbackRatePerDay(orDefault(s.getLocalFallbackRatePerDay(), 2600.0))
                .damageApprovalThreshold(orDefault(s.getDamageApprovalThreshold(), 5000.0))
                .maxKmPerDaySanityLimit(orDefault(s.getMaxKmPerDaySanityLimit(), 600.0))
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private <T> T orDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
