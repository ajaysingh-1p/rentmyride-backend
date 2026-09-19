package com.rentmyride.service.impl;

import com.rentmyride.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Fix for: "booking jab tak payment na kare tab tak create nahi hona chahiye."
 *
 * A reservation is still created in the DB the moment the customer submits the booking form
 * (status PENDING) — that's needed so the car/date is held for them while they're on the payment
 * page (otherwise two customers could both "win" the same car mid-checkout). But if they never
 * actually pay, that PENDING reservation used to sit there FOREVER, permanently blocking the car
 * for those dates for everyone else, and cluttering admin's view with bookings that were never
 * really made.
 *
 * This job sweeps up PENDING reservations that are older than the grace window below with zero
 * payment on them, and cancels them automatically — freeing the car for real bookings, exactly as
 * if the abandoned booking had never happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryService {

    private static final int GRACE_MINUTES = 20;

    private final ReservationRepository reservationRepository;

    @Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
    @Transactional
    public void expireAbandonedReservations() {
        // Race-condition fix: previously read the abandoned list into memory, then looped and
        // called save() on each — by the time save() ran, several seconds (or longer, under load)
        // could have passed since the read. If a customer's payment landed in that window, this
        // loop had no way to know and would silently cancel their now-paid booking. A single
        // atomic UPDATE ... WHERE (see repository) re-checks the condition at write time, so a
        // reservation that just got paid is automatically excluded instead of being raced.
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(GRACE_MINUTES);
        int cancelledCount = reservationRepository.cancelAbandonedUnpaidReservations(cutoff);
        if (cancelledCount > 0) {
            log.info("[DDT] Auto-cancelled {} abandoned unpaid reservation(s) older than {} minutes.",
                    cancelledCount, GRACE_MINUTES);
        }
    }
}
