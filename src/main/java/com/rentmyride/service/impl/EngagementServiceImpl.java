package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.dtos.BusinessSettingsDTO;
import com.rentmyride.dtos.EngagementDTO;
import com.rentmyride.entities.Car;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Rental;
import com.rentmyride.entities.Reservation;
import com.rentmyride.repository.BookingIntentRepository;
import com.rentmyride.repository.CarRepository;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.RentalRepository;
import com.rentmyride.repository.ReservationRepository;
import com.rentmyride.service.BusinessSettingsService;
import com.rentmyride.service.EngagementService;
import com.rentmyride.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EngagementServiceImpl implements EngagementService {

    private final CustomerRepository customerRepository;
    private final RentalRepository rentalRepository;
    private final ReservationRepository reservationRepository;
    private final CarRepository carRepository;
    private final BookingIntentRepository bookingIntentRepository;
    private final NotificationService notificationService;
    private final BusinessSettingsService businessSettingsService;

    // NOTE: tier thresholds, tier discounts, streak milestone length, and streak reward
    // amount are all admin-configurable via Business Settings — fetched fresh per call
    // below instead of being fixed Java constants.

    @Override
    @Transactional(readOnly = true)
    public EngagementDTO.Summary getSummary(Long customerId) {
        // IDOR fix: loyalty tier/badges/streak are personal — lock this to the owner or admin.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        BusinessSettingsDTO settings = businessSettingsService.getSettings();

        List<Rental> completed = rentalRepository.findByCustomer_CustomerIdAndRentalStatus(
                customerId, Rental.RentalStatus.COMPLETED);
        int completedTrips = completed.size();

        String tier;
        double discount;
        int tripsToNext;
        if (completedTrips >= settings.getGoldTierMinTrips()) {
            tier = "GOLD"; discount = settings.getGoldDiscountPct(); tripsToNext = 0;
        } else if (completedTrips >= settings.getSilverTierMinTrips()) {
            tier = "SILVER"; discount = settings.getSilverDiscountPct();
            tripsToNext = settings.getGoldTierMinTrips() - completedTrips;
        } else {
            tier = "BRONZE"; discount = 0.0;
            tripsToNext = settings.getSilverTierMinTrips() - completedTrips;
        }

        int streakMonths = computeStreakMonths(completed);
        String currentMonthKey = YearMonth.now().toString();
        boolean streakEligible = streakMonths > 0 && streakMonths % settings.getStreakMilestoneMonths() == 0
                && !currentMonthKey.equals(customer.getLastStreakRewardMonth());

        return EngagementDTO.Summary.builder()
                .loyaltyTier(tier)
                .loyaltyDiscountPct(discount)
                .completedTrips(completedTrips)
                .tripsToNextTier(tripsToNext)
                .badges(computeBadges(customer, completed))
                .currentStreakMonths(streakMonths)
                .streakRewardEligible(streakEligible)
                .build();
    }

    private List<EngagementDTO.Badge> computeBadges(Customer customer, List<Rental> completed) {
        List<EngagementDTO.Badge> badges = new ArrayList<>();
        int count = completed.size();

        if (count >= 1) badges.add(badge("FIRST_TRIP", "First Trip", "🎉", "Completed your first rental"));
        if (count >= 10) badges.add(badge("FREQUENT_RIDER", "Frequent Rider", "🚗", "10+ completed trips"));
        if (count >= 25) badges.add(badge("ROAD_WARRIOR", "Road Warrior", "🏆", "25+ completed trips"));

        if (count >= 5) {
            List<Rental> lastFive = completed.stream()
                    .sorted(Comparator.comparing(Rental::getActualReturnDatetime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .collect(Collectors.toList());
            boolean allClean = lastFive.stream().allMatch(r -> r.getDamageCharges() == null || r.getDamageCharges() == 0);
            if (allClean) badges.add(badge("ZERO_DAMAGE_STREAK", "Careful Driver", "🛡️", "Last 5 trips with zero damage charges"));
        }

        if (customer.getTrustScore() != null && customer.getTrustScore() >= 95) {
            badges.add(badge("TRUST_CHAMPION", "Trust Champion", "⭐", "Trust score of 95+"));
        }

        List<Reservation> reservations = reservationRepository.findByCustomer_CustomerId(customer.getCustomerId());
        boolean earlyBird = reservations.stream().anyMatch(r ->
                r.getCreatedAt() != null && r.getPickupDate() != null &&
                        ChronoUnit.DAYS.between(r.getCreatedAt().toLocalDate(), r.getPickupDate()) >= 7);
        if (earlyBird) badges.add(badge("EARLY_BIRD", "Early Bird", "🐦", "Booked a trip 7+ days in advance"));

        return badges;
    }

    private EngagementDTO.Badge badge(String code, String label, String emoji, String desc) {
        return EngagementDTO.Badge.builder().code(code).label(label).emoji(emoji).description(desc).build();
    }

    // Counts how many consecutive months (ending this month or last month) have >=1 completed rental
    private int computeStreakMonths(List<Rental> completed) {
        Set<YearMonth> monthsWithTrip = new HashSet<>();
        for (Rental r : completed) {
            if (r.getActualReturnDatetime() != null) {
                monthsWithTrip.add(YearMonth.from(r.getActualReturnDatetime()));
            }
        }
        if (monthsWithTrip.isEmpty()) return 0;

        YearMonth cursor = YearMonth.now();
        // Allow the streak to still count if this month has no trip YET but last month did
        if (!monthsWithTrip.contains(cursor)) cursor = cursor.minusMonths(1);

        int streak = 0;
        while (monthsWithTrip.contains(cursor)) {
            streak++;
            cursor = cursor.minusMonths(1);
        }
        return streak;
    }

    @Override
    @Transactional
    public void checkAndAwardStreakReward(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) return;
        BusinessSettingsDTO settings = businessSettingsService.getSettings();

        List<Rental> completed = rentalRepository.findByCustomer_CustomerIdAndRentalStatus(
                customerId, Rental.RentalStatus.COMPLETED);
        int streak = computeStreakMonths(completed);
        String currentMonthKey = YearMonth.now().toString();

        boolean newMilestone = streak > 0 && streak % settings.getStreakMilestoneMonths() == 0
                && !currentMonthKey.equals(customer.getLastStreakRewardMonth());

        if (newMilestone) {
            double reward = settings.getStreakRewardAmount();
            customer.setWalletBalance((customer.getWalletBalance() == null ? 0.0 : customer.getWalletBalance()) + reward);
            customer.setLastStreakRewardMonth(currentMonthKey);
            customerRepository.save(customer);

            notificationService.notifyCustomer(
                    customerId,
                    "🔥 " + streak + "-Month Streak Bonus!",
                    "You've booked with us " + streak + " months in a row! We've added ₹" +
                            (int) reward + " to your wallet as a thank-you. Keep it going!",
                    com.rentmyride.entities.Notification.Type.GENERAL, null
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EngagementDTO.LeaderboardEntry> getReferralLeaderboard() {
        return customerRepository.findAll().stream()
                .filter(c -> c.getReferralCode() != null)
                .map(c -> EngagementDTO.LeaderboardEntry.builder()
                        .customerId(c.getCustomerId())
                        .name(c.getFirstName() + " " + c.getLastName())
                        .referredCount(customerRepository.countByReferredByCode(c.getReferralCode()))
                        .build())
                .filter(e -> e.getReferredCount() > 0)
                .sorted(Comparator.comparingLong(EngagementDTO.LeaderboardEntry::getReferredCount).reversed())
                .limit(3)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordBookingIntent(Long customerId, Long carId) {
        // IDOR fix: without this, any logged-in customer could spam booking-intent records
        // against another customer's account.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer customer = customerRepository.findById(customerId).orElse(null);
        Car car = carRepository.findById(carId).orElse(null);
        if (customer == null || car == null) return;
        // One live intent per customer+car — clear any previous unreminded one first
        bookingIntentRepository.deleteByCustomer_CustomerIdAndCar_CarId(customerId, carId);
        bookingIntentRepository.save(com.rentmyride.entities.BookingIntent.builder()
                .customer(customer).car(car).build());
    }

    @Override
    @Transactional
    public void clearBookingIntent(Long customerId, Long carId) {
        bookingIntentRepository.deleteByCustomer_CustomerIdAndCar_CarId(customerId, carId);
    }

    @Override
    @Transactional(readOnly = true)
    public double getLoyaltyDiscountPct(Long customerId) {
        BusinessSettingsDTO settings = businessSettingsService.getSettings();
        List<Rental> completed = rentalRepository.findByCustomer_CustomerIdAndRentalStatus(
                customerId, Rental.RentalStatus.COMPLETED);
        int n = completed.size();
        if (n >= settings.getGoldTierMinTrips()) return settings.getGoldDiscountPct();
        if (n >= settings.getSilverTierMinTrips()) return settings.getSilverDiscountPct();
        return 0.0;
    }
}
