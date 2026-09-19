package com.rentmyride.scheduled;

import com.rentmyride.entities.BookingIntent;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Reservation;
import com.rentmyride.repository.BookingIntentRepository;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.ReservationRepository;
import com.rentmyride.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Two lightweight re-engagement jobs:
//  1. Abandoned booking reminder — customer picked dates on a car but never confirmed the
//     booking; nudge them ~2 hours later.
//  2. "We miss you" email — customer hasn't booked anything in 30+ days.
// Both are best-effort: failures are logged, not thrown, so one bad email never blocks the rest.
@Component
@RequiredArgsConstructor
@Slf4j
public class EngagementScheduledJobs {

    private static final int ABANDONED_CUTOFF_HOURS = 2;
    private static final int INACTIVITY_DAYS = 30;
    private static final int RE_ENGAGEMENT_COOLDOWN_DAYS = 30;

    private final BookingIntentRepository bookingIntentRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    // Runs every 30 minutes
    @Scheduled(fixedRate = 30 * 60 * 1000)
    @Transactional
    public void sendAbandonedBookingReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ABANDONED_CUTOFF_HOURS);
        List<BookingIntent> stale = bookingIntentRepository.findStaleUnreminded(cutoff);

        for (BookingIntent intent : stale) {
            try {
                Customer c = intent.getCustomer();
                String carLabel = intent.getCar().getBrand() + " " + intent.getCar().getModel();
                notificationService.sendEmail(
                        c.getEmail(),
                        "Still interested in the " + carLabel + "? 🚗",
                        "Hi " + c.getFirstName() + ",\n\n" +
                                "We noticed you were checking out the " + carLabel + " but didn't finish booking it. " +
                                "It's still available — come back and complete your booking whenever you're ready!\n\n" +
                                "— Team RentMyRide"
                );
                intent.setReminderSent(true);
                bookingIntentRepository.save(intent);
            } catch (Exception e) {
                log.warn("[RMR] Failed to send abandoned-booking reminder for intent {}: {}", intent.getIntentId(), e.getMessage());
            }
        }
    }

    // Runs once a day at 10 AM
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional
    public void sendReEngagementEmails() {
        LocalDateTime inactivityCutoff = LocalDateTime.now().minusDays(INACTIVITY_DAYS);
        LocalDateTime cooldownCutoff = LocalDateTime.now().minusDays(RE_ENGAGEMENT_COOLDOWN_DAYS);

        List<Customer> activeCustomers = customerRepository.findByAccountStatus(Customer.AccountStatus.ACTIVE);

        // Most recent reservation.createdAt per customer, computed once instead of N queries
        Map<Long, LocalDateTime> lastBookingByCustomer = reservationRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCustomer().getCustomerId(),
                        Collectors.mapping(Reservation::getCreatedAt,
                                Collectors.maxBy(LocalDateTime::compareTo))))
                .entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));

        for (Customer c : activeCustomers) {
            LocalDateTime lastBooking = lastBookingByCustomer.get(c.getCustomerId());
            boolean inactive = lastBooking == null || lastBooking.isBefore(inactivityCutoff);
            boolean neverEmailed = c.getLastReEngagementEmailAt() == null;
            boolean cooldownPassed = neverEmailed || c.getLastReEngagementEmailAt().isBefore(cooldownCutoff);

            if (inactive && cooldownPassed) {
                try {
                    notificationService.sendEmail(
                            c.getEmail(),
                            "We miss you at RentMyRide! 🚗💙",
                            "Hi " + c.getFirstName() + ",\n\n" +
                                    "It's been a while since your last trip with us. Come back and check out our latest cars — " +
                                    "your saved addresses and loyalty progress are still right where you left them!\n\n" +
                                    "— Team RentMyRide"
                    );
                    c.setLastReEngagementEmailAt(LocalDateTime.now());
                    customerRepository.save(c);
                } catch (Exception e) {
                    log.warn("[RMR] Failed to send re-engagement email to customer {}: {}", c.getCustomerId(), e.getMessage());
                }
            }
        }
    }
}
