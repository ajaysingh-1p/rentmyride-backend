package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.*;
import com.rentmyride.dtos.RentalDTO;
import com.rentmyride.entities.*;
import com.rentmyride.repository.*;
import com.rentmyride.security.SecurityUtils;
import com.rentmyride.service.CustomerService;
import com.rentmyride.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RentalServiceImpl implements RentalService {

    // Single-day LOCAL bookings include this many hours of usage from actual pickup; each hour
    // beyond it is billed at EXTRA_HOUR_RATE.
    private static final int LOCAL_INCLUDED_HOURS = 10;
    private static final double EXTRA_HOUR_RATE = 200.0;

    private final RentalRepository rentalRepository;
    private final ReservationRepository reservationRepository;
    private final DriverRepository driverRepository;
    private final CarRepository carRepository;
    private final CustomerService customerService;
    private final com.rentmyride.service.EngagementService engagementService;
    private final com.rentmyride.service.BusinessSettingsService businessSettingsService;
    private final com.rentmyride.service.NotificationService notificationService;
    // Bug fix: invoice generation moved to RentalController (see completeRental() there) to
    // avoid a self-deadlock — this field is no longer used from here.

    // Pickup — the SAME form as before, except the reservation dropdown (built on the frontend)
    // is restricted to only the logged-in driver's own assigned reservations. We also validate
    // here that the reservation really was assigned to this driver, as a backend safety net.
    @Override
    @Transactional
    public RentalDTO initiateRental(RentalDTO.PickupRequest req) {
        Reservation reservation = reservationRepository.findById(req.getReservationId())
                .orElseThrow(() -> new ReservationNotFoundException(req.getReservationId()));

        Driver driver = req.getDriverId() != null
                ? driverRepository.findById(req.getDriverId()).orElseThrow(() -> new DriverNotFoundException(req.getDriverId()))
                : null;

        // IDOR fix: the assigned-driver check below only confirms req.getDriverId() matches the
        // reservation — it never confirms the CALLER actually is that driver. Without this, one
        // driver could start another driver's pickup just by supplying their driverId in the body.
        if (driver != null) {
            SecurityUtils.assertOwnsAsDriver(driver.getDriverId());
        }

        if (driver != null && (reservation.getAssignedDriver() == null
                || !reservation.getAssignedDriver().getDriverId().equals(driver.getDriverId()))) {
            throw new UnauthorizedAccessException("This booking is not assigned to you.");
        }

        // A driver can only start a pickup on the actual scheduled pickup date — not early.
        java.time.LocalDate today = java.time.LocalDate.now();
        if (reservation.getPickupDate().isAfter(today)) {
            throw new InvalidDateRangeException("This booking's pickup date is " + reservation.getPickupDate() +
                    ". You can only start the pickup on that date.");
        }

        // Start from the reservation's already-final price (local/outstation/promo/wallet all
        // accounted for at booking time) rather than recomputing a separate estimate here.
        double baseAmount = reservation.getEstimatedAmount();

        Rental rental = Rental.builder()
                .reservation(reservation).customer(reservation.getCustomer())
                .car(reservation.getCar()).driver(driver)
                .actualPickupDatetime(req.getActualPickupDatetime())
                .odometerAtPickup(req.getOdometerAtPickup())
                .baseAmount(baseAmount).totalAmount(baseAmount)
                .remarks(req.getRemarks())
                .build();
        reservation.setReservationStatus(Reservation.ReservationStatus.CONFIRMED);
        reservation.getCar().setAvailabilityStatus(Car.AvailabilityStatus.BOOKED);
        reservationRepository.save(reservation);
        carRepository.save(reservation.getCar());
        return mapToDTO(rentalRepository.save(rental));
    }

    // Return/Drop-off — the SAME "last km" form as before. Price is now recalculated here based
    // on the ACTUAL distance driven (not the pre-trip estimate):
    //   OUTSTATION: base fare = actual km × car's per-km rate (was previously frozen at the
    //               pre-trip estimated distance, which ignored real detours/extra driving).
    //   LOCAL:      flat per-day package covers up to 200 km total. Anything beyond that is
    //               charged in ₹1500 slabs per extra 100 km (or part thereof) — same for every car.
    // Any damage charge or discount entered here is added on top. The final total is synced back
    // onto the Reservation so the customer's "My Bookings" balance due reflects it immediately.
    // NOTE: the LOCAL free-km limit and extra-km slab charge are now admin-configurable via
    // Business Settings — fetched fresh below instead of being fixed Java constants.

    @Override
    @Transactional
    public RentalDTO completeRental(Long rentalId, RentalDTO.ReturnRequest req) {
        // Race-condition fix: lock this rental row BEFORE reading its status. Without the lock,
        // two near-simultaneous drop-off submissions for the same rental (double-tap, a retried
        // request, or two devices) could both run findById(), both see ACTIVE (because neither
        // has saved yet), and both fall through the "already completed" guard below — applying
        // trust-score changes twice, recomputing/overwriting the bill twice, and racing to create
        // two invoices (only saved from a hard DB-constraint error by Invoice.rental_id being
        // unique — which would surface as an ugly 500 to the second caller instead of the clean
        // "already completed" message they should get). The lock makes the second request wait
        // for the first to commit, then correctly see COMPLETED and get rejected below.
        Rental rental = rentalRepository.findByIdForUpdate(rentalId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));

        // Bug fix: nothing previously stopped this from running twice on the same rental — a
        // driver double-tapping "Complete Trip" (easy to do while waiting on a slow response) or
        // a retried network request would silently re-process an already-completed rental: apply
        // trust-score changes AGAIN, and (absent the unique constraint fix on Invoice.rental_id)
        // create a SECOND duplicate invoice for the same trip.
        if (rental.getRentalStatus() != Rental.RentalStatus.ACTIVE) {
            throw new InvalidDateRangeException("This rental has already been completed.");
        }

        // IDOR fix: nothing previously confirmed the calling driver is the one actually assigned
        // to this rental — any driver could close out (and set the final bill/damage charge for)
        // any other driver's trip just by knowing the rentalId.
        Long assignedDriverId = rental.getDriver() != null ? rental.getDriver().getDriverId() : null;
        SecurityUtils.assertOwnsAsDriver(assignedDriverId);

        double kmDriven = req.getOdometerAtReturn() - rental.getOdometerAtPickup();
        if (kmDriven < 0) {
            throw new InvalidDateRangeException("Return odometer reading can't be less than the pickup reading.");
        }

        Reservation reservation = rental.getReservation();
        Car car = rental.getCar();
        double damageReported = req.getDamageCharges() != null ? req.getDamageCharges() : 0.0;
        double discount = req.getDiscountAmount() != null ? req.getDiscountAmount() : 0.0;
        var settings = businessSettingsService.getSettings();

        // Issue #20 fix: sanity-check the odometer reading against how many days the rental
        // actually ran, instead of trusting whatever number is typed in. A reading with an extra
        // stray digit (e.g. 45000 instead of 4500 km) previously sailed straight through and
        // would have billed the customer thousands of rupees in "extra km" charges.
        long rentalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(
                rental.getActualPickupDatetime() != null ? rental.getActualPickupDatetime() : LocalDateTime.now().minusDays(1),
                req.getActualReturnDatetime() != null ? req.getActualReturnDatetime() : LocalDateTime.now()) + 1);
        double maxPlausibleKm = rentalDays * settings.getMaxKmPerDaySanityLimit();
        if (kmDriven > maxPlausibleKm) {
            throw new InvalidDateRangeException(
                    "Odometer reading implies " + (int) kmDriven + " km driven over " + rentalDays +
                    " day(s), which exceeds the plausible limit of " + (int) maxPlausibleKm +
                    " km. Please double-check the odometer reading.");
        }

        // Issue #20 fix (damage cap): a driver typo like an extra zero shouldn't be able to bill
        // a customer more than the car itself is worth. Anything beyond that is rejected outright
        // rather than silently accepted — a genuinely large claim should be re-entered correctly,
        // not waved through.
        double damageSanityCap = car.getRentPerDay() != null ? car.getRentPerDay() * 200 : 500000.0; // generous ceiling
        if (damageReported > damageSanityCap) {
            throw new InvalidPricingException(
                    "Reported damage charge of ₹" + damageReported + " looks implausible — please verify and re-enter.");
        }

        double baseAmount;
        double extraKmCharges = 0.0;

        if (reservation.getTripType() == Reservation.TripType.OUTSTATION) {
            // Re-price using the ACTUAL distance driven instead of the pre-trip estimate.
            double ratePerKm = (car.getRatePerKm() != null && car.getRatePerKm() > 0) ? car.getRatePerKm() : 0.0;
            if (ratePerKm > 0) {
                double billableKm = Math.max(kmDriven, 1.0);
                double actualBaseFare = Math.round(billableKm * ratePerKm * 100) / 100.0;
                double nightCharges = reservation.getNightCharges() != null ? reservation.getNightCharges() : 0.0;
                baseAmount = actualBaseFare + nightCharges;
            } else {
                baseAmount = rental.getBaseAmount(); // fallback if car has no per-km rate on file
            }
        } else {
            // LOCAL: keep the flat package amount already set at pickup, but add extra-km
            // charges if the customer drove more than the free-km limit included in the package.
            baseAmount = rental.getBaseAmount();
            // Bug fix: freeKmLimit was a flat 200 km regardless of trip length — a 3-day LOCAL
            // rental (including one extended via extendRental()) got the SAME 200 km allowance
            // as a 1-day rental, so extending the trip didn't extend the free-km limit with it.
            // It should scale with however many days are on the reservation right now — which
            // extendRental() already keeps up to date via reservation.setTotalDays(...).
            int totalDays = reservation.getTotalDays() != null && reservation.getTotalDays() > 0
                    ? reservation.getTotalDays() : 1;
            double freeKmLimit = settings.getLocalFreeKmLimit() * totalDays;
            if (kmDriven > freeKmLimit) {
                double extraKm = kmDriven - freeKmLimit;
                int slabs = (int) Math.ceil(extraKm / settings.getExtraKmSlabSize());
                extraKmCharges = slabs * settings.getExtraKmSlabCharge();
            }
        }

        // New: 10-hour usage window + ₹200/hour extra-hour charge — ONLY for a single-day LOCAL
        // booking (per requirement). The package covers 10 hours starting from when the driver
        // ACTUALLY picked up the car (not the scheduled pickup time), running to the actual
        // return time. Anything beyond 10 hours is billed extra, in whole hours (rounded up).
        double extraHourCharges = 0.0;
        boolean isSingleDayLocal = reservation.getTripType() == Reservation.TripType.LOCAL
                && reservation.getTotalDays() != null && reservation.getTotalDays() == 1;
        if (isSingleDayLocal && rental.getActualPickupDatetime() != null && req.getActualReturnDatetime() != null) {
            long minutesUsed = java.time.Duration.between(rental.getActualPickupDatetime(), req.getActualReturnDatetime()).toMinutes();
            long hoursUsed = (minutesUsed + 59) / 60; // round up any partial hour
            long extraHours = Math.max(0, hoursUsed - LOCAL_INCLUDED_HOURS);
            extraHourCharges = extraHours * EXTRA_HOUR_RATE;
            if (extraHourCharges > 0) {
                log.info("[DDT] Single-day LOCAL rental {} used {}h (10h included) — extra hour charge: ₹{}",
                        rentalId, hoursUsed, extraHourCharges);
            }
        }

        // Issue #21 fix: damage above the configured threshold isn't billed immediately just
        // because a Driver entered it — it's held pending an Admin's approve/reject decision
        // (see approveDamageCharge()/rejectDamageCharge() below). An Admin completing the rental
        // directly (or a Driver reporting a small, below-threshold amount) still applies
        // immediately, same as before.
        boolean callerIsAdmin;
        try {
            callerIsAdmin = SecurityUtils.currentUser().isAdmin();
        } catch (Exception e) {
            callerIsAdmin = false; // no authenticated context (e.g. internal/test call) — treat conservatively as non-admin
        }

        double damageApplied;
        Rental.DamageApprovalStatus damageStatus;
        Double pendingDamage;
        if (damageReported <= 0) {
            damageApplied = 0.0;
            damageStatus = Rental.DamageApprovalStatus.NONE;
            pendingDamage = null;
        } else if (callerIsAdmin || damageReported <= settings.getDamageApprovalThreshold()) {
            damageApplied = damageReported;
            damageStatus = callerIsAdmin ? Rental.DamageApprovalStatus.APPROVED : Rental.DamageApprovalStatus.AUTO_APPROVED;
            pendingDamage = null;
        } else {
            damageApplied = 0.0; // not billed yet
            damageStatus = Rental.DamageApprovalStatus.PENDING_APPROVAL;
            pendingDamage = damageReported;
            log.warn("[DDT-WARN] Rental {} completed with ₹{} damage pending Admin approval (threshold ₹{}).",
                    rentalId, damageReported, settings.getDamageApprovalThreshold());
        }

        // Issue #20 fix (discount floor): a discount larger than everything else being charged
        // used to be able to push the total negative. Clamp it so the customer is never billed
        // less than ₹0, instead of the business owing the customer money by accident.
        double total = Math.max(0.0, Math.round((baseAmount + extraKmCharges + extraHourCharges + damageApplied - discount) * 100) / 100.0);

        rental.setActualReturnDatetime(req.getActualReturnDatetime());
        rental.setOdometerAtReturn(req.getOdometerAtReturn());
        rental.setTotalKmDriven(kmDriven);
        rental.setBaseAmount(baseAmount);
        rental.setExtraKmCharges(extraKmCharges);
        rental.setLateReturnCharges(extraHourCharges);
        rental.setDamageCharges(damageApplied);
        rental.setDamageApprovalStatus(damageStatus);
        rental.setPendingDamageCharges(pendingDamage);
        rental.setDiscountAmount(discount);
        rental.setTotalAmount(total);
        rental.setRentalStatus(Rental.RentalStatus.COMPLETED);
        rental.setRemarks(req.getRemarks());
        rental.getCar().setAvailabilityStatus(Car.AvailabilityStatus.AVAILABLE);
        rental.getReservation().setReservationStatus(Reservation.ReservationStatus.COMPLETED);
        // Sync the final total back onto the reservation so "My Bookings" balance due updates
        rental.getReservation().setEstimatedAmount(total);
        carRepository.save(rental.getCar());
        reservationRepository.save(rental.getReservation());

        // ── Trust score: reward a clean trip, penalize real problems ──
        // Perf fix: this used to call adjustTrustScore() up to 3 separate times in a row (each
        // one its own fetch-customer + save-customer round trip) — once for the completion
        // bonus, once for a damage penalty, once for a late-return penalty. Combining them into
        // a single delta means ONE fetch and ONE save instead of up to three of each.
        Long customerId = rental.getCustomer().getCustomerId();
        int trustDelta = 2; // completed rental boost
        if (damageReported > 0) {
            trustDelta -= 8; // car came back damaged
        }
        java.time.LocalDate expectedReturn = reservation.getReturnDate();
        if (expectedReturn != null && req.getActualReturnDatetime() != null
                && req.getActualReturnDatetime().toLocalDate().isAfter(expectedReturn)) {
            long daysLate = java.time.temporal.ChronoUnit.DAYS.between(expectedReturn, req.getActualReturnDatetime().toLocalDate());
            trustDelta += (int) Math.max(-15, -3 * daysLate); // late return, capped
        }
        customerService.adjustTrustScore(customerId, trustDelta);
        engagementService.checkAndAwardStreakReward(customerId);

        Rental savedRental = rentalRepository.save(rental);

        // Bug fix: invoice generation used to happen right here, still inside this method's own
        // @Transactional block (which is holding a pessimistic lock on this very Rental row).
        // That caused a self-deadlock against generateInvoice()'s own REQUIRES_NEW transaction —
        // see RentalController.completeRental() for the full explanation and the fix (the call
        // now happens there, AFTER this transaction has committed and released the lock).
        return mapToDTO(savedRental);
    }

    // Extend an ACTIVE rental to a later return date — the customer is still using the car.
    // Extending doesn't change the route/distance, so extra cost is just additional day(s)/night(s):
    // LOCAL trips: extra days × ₹2600/day. OUTSTATION trips: extra nights × the car's night rate.
    @Override
    @Transactional
    public RentalDTO.ExtendResponse extendRental(Long rentalId, RentalDTO.ExtendRequest req) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));

        // IDOR fix: without this, any logged-in customer could extend (and get billed extra
        // charges against) someone else's rental just by guessing the rentalId.
        SecurityUtils.assertOwnsAsCustomer(rental.getCustomer().getCustomerId());

        if (rental.getRentalStatus() != Rental.RentalStatus.ACTIVE)
            throw new UnauthorizedAccessException("Only an active rental can be extended.");

        Reservation reservation = rental.getReservation();
        if (!req.getNewReturnDate().isAfter(reservation.getReturnDate()))
            throw new InvalidDateRangeException("New return date must be after the current return date.");

        // Car must be free for the extra days (no other reservation starting before the new return date)
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                rental.getCar().getCarId(), reservation.getReturnDate().plusDays(1), req.getNewReturnDate());
        conflicts.removeIf(c -> c.getReservationId().equals(reservation.getReservationId()));
        if (!conflicts.isEmpty())
            throw new CarNotAvailableException("Car is already booked starting " + conflicts.get(0).getPickupDate() + " — can't extend that far.");

        long extraDays = req.getNewReturnDate().toEpochDay() - reservation.getReturnDate().toEpochDay();
        double extraCharge;
        if (reservation.getTripType() == Reservation.TripType.LOCAL) {
            // Bug fix: this was hardcoded to 2600.0 for every car — that's actually the
            // *fallback* rate ReservationServiceImpl.calculatePrice() only uses when a car has
            // no rentPerDay set at all (see LOCAL_PACKAGE_RATE_PER_DAY there). The comment here
            // claiming it was "the same flat local package rate used at booking" was simply
            // wrong — the real per-day rate used at booking is the CAR's own rentPerDay. Every
            // extension was silently billed at ₹2600/day regardless of what the booked car
            // actually rents for.
            double ratePerDay = (rental.getCar().getRentPerDay() != null && rental.getCar().getRentPerDay() > 0)
                    ? rental.getCar().getRentPerDay() : 2600.0;
            extraCharge = extraDays * ratePerDay;
        } else {
            double nightRate = (rental.getCar().getNightChargePerNight() == null || rental.getCar().getNightChargePerNight() < 300)
                    ? 300.0 : rental.getCar().getNightChargePerNight();
            extraCharge = extraDays * nightRate;
        }
        extraCharge = Math.round(extraCharge * 100) / 100.0;

        reservation.setReturnDate(req.getNewReturnDate());
        reservation.setTotalDays(reservation.getTotalDays() + (int) extraDays);
        reservation.setNights((reservation.getNights() == null ? 0 : reservation.getNights()) + (int) extraDays);
        reservation.setNightCharges((reservation.getNightCharges() == null ? 0 : reservation.getNightCharges())
                + (reservation.getTripType() == Reservation.TripType.OUTSTATION ? extraCharge : 0));
        reservation.setEstimatedAmount(reservation.getEstimatedAmount() + extraCharge);
        reservationRepository.save(reservation);

        rental.setBaseAmount(rental.getBaseAmount() + extraCharge);
        rental.setTotalAmount(rental.getTotalAmount() + extraCharge);
        Rental saved = rentalRepository.save(rental);

        return RentalDTO.ExtendResponse.builder()
                .rental(mapToDTO(saved))
                .extraCharge(extraCharge)
                .newReturnDate(req.getNewReturnDate())
                .message("Rental extended to " + req.getNewReturnDate() + ". Extra charge: ₹" + extraCharge + ".")
                .build();
    }

    @Override @Transactional(readOnly = true)
    public RentalDTO getRentalById(Long id) {
        // IDOR fix: without this, any customer or driver could view any rental's full details
        // (billing, damage charges, odometer, remarks) just by guessing the rentalId.
        Rental rental = rentalRepository.findById(id).orElseThrow(() -> new RentalNotFoundException(id));
        Long ownerDriverId = rental.getDriver() != null ? rental.getDriver().getDriverId() : null;
        SecurityUtils.assertOwnsAsCustomerOrDriver(rental.getCustomer().getCustomerId(), ownerDriverId);
        return mapToDTO(rental);
    }

    // Issue #21 — Admin decision on a damage charge that exceeded the auto-approval threshold.
    @Override
    @Transactional
    public RentalDTO approveDamageCharge(Long rentalId) {
        Rental rental = rentalRepository.findById(rentalId).orElseThrow(() -> new RentalNotFoundException(rentalId));
        if (rental.getDamageApprovalStatus() != Rental.DamageApprovalStatus.PENDING_APPROVAL)
            throw new InvalidPricingException("This rental has no damage charge awaiting approval.");

        double approvedAmount = rental.getPendingDamageCharges();
        rental.setDamageCharges(approvedAmount);
        rental.setDamageApprovalStatus(Rental.DamageApprovalStatus.APPROVED);
        rental.setPendingDamageCharges(null);
        double newTotal = Math.max(0.0, rental.getTotalAmount() + approvedAmount);
        rental.setTotalAmount(newTotal);
        rental.getReservation().setEstimatedAmount(newTotal);
        reservationRepository.save(rental.getReservation());
        Rental saved = rentalRepository.save(rental);

        notificationService.notifyCustomer(rental.getCustomer().getCustomerId(), "Damage Charge Approved",
                "A damage charge of ₹" + approvedAmount + " for rental #" + rentalId + " has been approved and added to your bill.",
                Notification.Type.GENERAL, null);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public RentalDTO rejectDamageCharge(Long rentalId) {
        Rental rental = rentalRepository.findById(rentalId).orElseThrow(() -> new RentalNotFoundException(rentalId));
        if (rental.getDamageApprovalStatus() != Rental.DamageApprovalStatus.PENDING_APPROVAL)
            throw new InvalidPricingException("This rental has no damage charge awaiting approval.");

        rental.setDamageApprovalStatus(Rental.DamageApprovalStatus.REJECTED);
        rental.setPendingDamageCharges(null);
        // damageCharges stays 0 — it was never applied to the total in the first place.
        Rental saved = rentalRepository.save(rental);

        notificationService.notifyCustomer(rental.getCustomer().getCustomerId(), "Damage Charge Rejected",
                "The disputed damage charge for rental #" + rentalId + " was reviewed and rejected — it will not be billed.",
                Notification.Type.GENERAL, null);
        return mapToDTO(saved);
    }

    @Override @Transactional(readOnly = true)
    public List<RentalDTO> getPendingDamageApprovals() {
        return rentalRepository.findByDamageApprovalStatus(Rental.DamageApprovalStatus.PENDING_APPROVAL)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override @Transactional(readOnly = true)
    public List<RentalDTO> getAllRentals() {
        // Issue #24 fix — see RentalRepository.findAllWithDetails().
        return rentalRepository.findAllWithDetails().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated counterpart.
    @Override @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<RentalDTO> getAllRentalsPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        var result = rentalRepository.findAllWithDetails(pageable).map(this::mapToDTO);
        return com.rentmyride.dtos.PageResponse.from(result);
    }
    @Override @Transactional(readOnly = true)
    public List<RentalDTO> getRentalsByCustomer(Long id) {
        SecurityUtils.assertOwnsAsCustomer(id);
        return rentalRepository.findByCustomer_CustomerId(id).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public List<RentalDTO> getRentalsByDriver(Long id) {
        SecurityUtils.assertOwnsAsDriver(id);
        return rentalRepository.findByDriver_DriverId(id).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public List<RentalDTO> getRentalsByStatus(Rental.RentalStatus status) {
        return rentalRepository.findByRentalStatus(status).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public List<RentalDTO> getActiveRentals() {
        return rentalRepository.findAllActiveRentals().stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public Double getTotalRevenue() { return rentalRepository.getTotalRevenue(); }
    @Override @Transactional(readOnly = true)
    public Double getMonthlyRevenue(int month, int year) { return rentalRepository.getMonthlyRevenue(month, year); }

    // Driver's phone calls this every ~15-30s while the rental is ACTIVE, using the browser/device
    // Geolocation API. Silently ignored (not an error) once the trip is no longer ACTIVE, since the
    // driver's app may still have a stale watcher running for a moment after drop-off.
    @Override
    @Transactional
    public RentalDTO updateLocation(Long rentalId, Long driverId, RentalDTO.LocationUpdateRequest req) {
        // IDOR fix: confirm the CALLER is actually driverId, not just that driverId (whatever
        // value was sent) matches the rental — otherwise anyone could spoof anyone's GPS trail.
        SecurityUtils.assertOwnsAsDriver(driverId);
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));
        if (rental.getDriver() == null || !rental.getDriver().getDriverId().equals(driverId)) {
            throw new UnauthorizedAccessException("This rental is not assigned to you.");
        }
        if (rental.getRentalStatus() == Rental.RentalStatus.ACTIVE) {
            rental.setCurrentLat(req.getLat());
            rental.setCurrentLng(req.getLng());
            rental.setLocationUpdatedAt(LocalDateTime.now());
            rentalRepository.save(rental);
        }
        return mapToDTO(rental);
    }

    @Override
    @Transactional
    public RentalDTO submitRebookPoll(Long rentalId, Long customerId, RentalDTO.RebookPollRequest req) {
        // IDOR fix: confirm the CALLER is actually customerId, same reasoning as updateLocation above.
        SecurityUtils.assertOwnsAsCustomer(customerId);
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));
        if (!rental.getCustomer().getCustomerId().equals(customerId)) {
            throw new UnauthorizedAccessException("This rental doesn't belong to you.");
        }
        rental.setWouldRebook(req.getWouldRebook());
        return mapToDTO(rentalRepository.save(rental));
    }

    private RentalDTO mapToDTO(Rental r) {
        return RentalDTO.builder()
                .rentalId(r.getRentalId())
                .reservationId(r.getReservation().getReservationId())
                .customerId(r.getCustomer().getCustomerId())
                .customerName(r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName())
                .customerMobile(r.getCustomer().getMobileNumber())
                .carId(r.getCar().getCarId()).carBrand(r.getCar().getBrand())
                .carModel(r.getCar().getModel()).carRegistrationNumber(r.getCar().getRegistrationNumber())
                .driverId(r.getDriver() != null ? r.getDriver().getDriverId() : null)
                .driverName(r.getDriver() != null
                        ? r.getDriver().getFirstName() + " " + r.getDriver().getLastName() : null)
                .pickupLocation(r.getReservation().getPickupLocation())
                .dropLocation(r.getReservation().getDropLocation())
                .tripType(r.getReservation().getTripType())
                .actualPickupDatetime(r.getActualPickupDatetime())
                .actualReturnDatetime(r.getActualReturnDatetime())
                .odometerAtPickup(r.getOdometerAtPickup()).odometerAtReturn(r.getOdometerAtReturn())
                .totalKmDriven(r.getTotalKmDriven()).baseAmount(r.getBaseAmount())
                .extraKmCharges(r.getExtraKmCharges()).damageCharges(r.getDamageCharges())
                .damageApprovalStatus(r.getDamageApprovalStatus()).pendingDamageCharges(r.getPendingDamageCharges())
                .lateReturnCharges(r.getLateReturnCharges()).discountAmount(r.getDiscountAmount())
                .totalAmount(r.getTotalAmount()).rentalStatus(r.getRentalStatus())
                .remarks(r.getRemarks()).createdAt(r.getCreatedAt())
                .expectedReturnDate(r.getReservation().getReturnDate())
                .currentLat(r.getCurrentLat()).currentLng(r.getCurrentLng())
                .locationUpdatedAt(r.getLocationUpdatedAt())
                .wouldRebook(r.getWouldRebook())
                .build();
    }
}
