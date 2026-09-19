package com.rentmyride.service.impl;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.rentmyride.custom_exceptions.*;
import com.rentmyride.dtos.SelfDriveHandoverDTO;
import com.rentmyride.entities.*;
import com.rentmyride.repository.*;
import com.rentmyride.security.SecurityUtils;
import com.rentmyride.service.CustomerService;
import com.rentmyride.service.NotificationService;
import com.rentmyride.service.OtpService;
import com.rentmyride.service.SelfDriveHandoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelfDriveHandoverServiceImpl implements SelfDriveHandoverService {

    private final ReservationRepository reservationRepository;
    private final RentalRepository rentalRepository;
    private final CarRepository carRepository;
    private final CustomerRepository customerRepository;
    private final SelfDriveConfigRepository selfDriveConfigRepository;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final CustomerService customerService;
    private final RazorpayClient razorpayClient;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${self-drive.deposit-refund-method:WALLET}")
    private String depositRefundMethod;

    /**
     * OTP identifier is scoped to the booking, not the person — so a handover OTP can never be
     * confused with (or consumed by) the same customer's login/forgot-password OTP, and two
     * customers handing over at the same counter can't collide either.
     */
    private String otpKey(Long reservationId) {
        return "SELFDRIVE-" + reservationId;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Customer actions
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public SelfDriveHandoverDTO.OtpResponse requestPickupOtp(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // IDOR guard — a customer may only start their OWN booking.
        SecurityUtils.assertOwnsAsCustomer(reservation.getCustomer().getCustomerId());
        assertSelfDrive(reservation);

        if (reservation.getReservationStatus() == Reservation.ReservationStatus.CANCELLED)
            throw new UnauthorizedAccessException("This booking has been cancelled.");
        if (rentalRepository.findByReservation_ReservationId(reservationId).isPresent())
            throw new InvalidDateRangeException("This trip has already been started.");
        if (reservation.getPickupDate().isAfter(LocalDate.now()))
            throw new InvalidDateRangeException("Your pickup date is " + reservation.getPickupDate()
                    + ". You can start the trip on that date.");
        // Self-drive holds a refundable security deposit — it has to actually be collected
        // before the keys change hands, so a part-paid booking can't be handed over.
        assertFullyPaid(reservation);

        String otp = otpService.generateOtp(otpKey(reservationId), OtpVerification.OtpPurpose.SELF_DRIVE_PICKUP);
        log.info("[DDT] Self-drive PICKUP otp issued for reservation {}", reservationId);

        return SelfDriveHandoverDTO.OtpResponse.builder()
                .otp(otp)
                .expiresInMinutes(otpExpiryMinutes)
                .stage("PICKUP")
                .instruction("Show this code to the RentMyRide staff at the pickup point. "
                        + "They'll note the odometer reading and hand over the keys.")
                .build();
    }

    @Override
    @Transactional
    public SelfDriveHandoverDTO.OtpResponse requestReturnOtp(Long rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));

        SecurityUtils.assertOwnsAsCustomer(rental.getCustomer().getCustomerId());
        assertSelfDrive(rental.getReservation());

        if (rental.getRentalStatus() != Rental.RentalStatus.ACTIVE)
            throw new InvalidDateRangeException("This trip has already been closed.");

        String otp = otpService.generateOtp(otpKey(rental.getReservation().getReservationId()),
                OtpVerification.OtpPurpose.SELF_DRIVE_RETURN);
        log.info("[DDT] Self-drive RETURN otp issued for rental {}", rentalId);

        return SelfDriveHandoverDTO.OtpResponse.builder()
                .otp(otp)
                .expiresInMinutes(otpExpiryMinutes)
                .stage("RETURN")
                .instruction("Show this code to the staff at the drop-off point. "
                        + "They'll check the odometer and settle your security deposit.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SelfDriveHandoverDTO.ActiveTripResponse getMyActiveTrip(Long customerId) {
        SecurityUtils.assertOwnsAsCustomer(customerId);

        Rental rental = rentalRepository
                .findByCustomer_CustomerIdAndRentalStatus(customerId, Rental.RentalStatus.ACTIVE)
                .stream()
                .filter(r -> r.getReservation().getTripMode() == Reservation.TripMode.SELF_DRIVE)
                .findFirst()
                .orElse(null);
        if (rental == null) return null;

        Reservation reservation = rental.getReservation();
        SelfDriveConfig config = resolveConfig(rental.getCar());
        int days = days(reservation);

        return SelfDriveHandoverDTO.ActiveTripResponse.builder()
                .rentalId(rental.getRentalId())
                .reservationId(reservation.getReservationId())
                .carBrand(rental.getCar().getBrand())
                .carModel(rental.getCar().getModel())
                .carRegistrationNumber(rental.getCar().getRegistrationNumber())
                .actualPickupDatetime(rental.getActualPickupDatetime())
                .scheduledReturnDatetime(scheduledReturn(reservation))
                .odometerAtPickup(rental.getOdometerAtPickup())
                .totalDays(days)
                .freeKmPerDay(config.getFreeKmPerDay())
                .freeKmAllowance(config.getFreeKmPerDay() * days)
                .overageRatePerKm(config.getOverageRatePerKm())
                .lateReturnPenaltyPerHour(config.getLateReturnPenaltyPerHour())
                .depositHeld(depositOf(reservation))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Admin actions
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<SelfDriveHandoverDTO.PendingHandover> getPendingHandovers() {
        List<Reservation> candidates = reservationRepository
                .findByTripModeAndReservationStatusAndPickupDateLessThanEqual(
                        Reservation.TripMode.SELF_DRIVE,
                        Reservation.ReservationStatus.CONFIRMED,
                        LocalDate.now());

        List<SelfDriveHandoverDTO.PendingHandover> out = new ArrayList<>();
        for (Reservation r : candidates) {
            // Already handed over? then it belongs on the "active" list, not here.
            if (rentalRepository.findByReservation_ReservationId(r.getReservationId()).isPresent()) continue;

            double paid = r.getAmountPaid() == null ? 0.0 : r.getAmountPaid();
            double due = r.getEstimatedAmount() == null ? 0.0 : r.getEstimatedAmount();
            out.add(SelfDriveHandoverDTO.PendingHandover.builder()
                    .reservationId(r.getReservationId())
                    .customerId(r.getCustomer().getCustomerId())
                    .customerName(r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName())
                    .customerMobile(r.getCustomer().getMobileNumber())
                    .carBrand(r.getCar().getBrand())
                    .carModel(r.getCar().getModel())
                    .carRegistrationNumber(r.getCar().getRegistrationNumber())
                    .pickupDate(r.getPickupDate())
                    .pickupTime(r.getPickupTime())
                    .returnDate(r.getReturnDate())
                    .pickupLocation(r.getPickupLocation())
                    .estimatedAmount(due)
                    .amountPaid(paid)
                    .depositHeld(depositOf(r))
                    .fullyPaid(paid + 0.01 >= due)
                    .build());
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SelfDriveHandoverDTO.ActiveHandover> getActiveHandovers() {
        List<SelfDriveHandoverDTO.ActiveHandover> out = new ArrayList<>();
        for (Rental rental : rentalRepository.findByRentalStatus(Rental.RentalStatus.ACTIVE)) {
            if (rental.getReservation().getTripMode() != Reservation.TripMode.SELF_DRIVE) continue;
            out.add(toActiveHandover(rental));
        }
        return out;
    }

    @Override
    @Transactional
    public SelfDriveHandoverDTO.ActiveHandover confirmPickup(SelfDriveHandoverDTO.PickupRequest req) {
        if (req.getReservationId() == null || req.getOtp() == null || req.getOtp().isBlank())
            throw new InvalidOtpException("Reservation and the customer's OTP are both required.");
        if (req.getOdometerAtPickup() == null || req.getOdometerAtPickup() < 0)
            throw new InvalidPricingException("A valid odometer reading at pickup is required.");

        // Lock the reservation: two staff members submitting the same handover at once would
        // otherwise both pass the "no rental yet" check and create two Rental rows for one car.
        Reservation reservation = reservationRepository.findByIdForUpdate(req.getReservationId())
                .orElseThrow(() -> new ReservationNotFoundException(req.getReservationId()));
        assertSelfDrive(reservation);

        if (reservation.getReservationStatus() == Reservation.ReservationStatus.CANCELLED)
            throw new UnauthorizedAccessException("This booking has been cancelled.");
        if (rentalRepository.findByReservation_ReservationId(reservation.getReservationId()).isPresent())
            throw new InvalidDateRangeException("This trip has already been started.");
        if (reservation.getPickupDate().isAfter(LocalDate.now()))
            throw new InvalidDateRangeException("Pickup date is " + reservation.getPickupDate()
                    + " — the car can't be handed over before that.");
        assertFullyPaid(reservation);

        // The actual proof of handover: the code the customer is holding on their own screen.
        otpService.verifyOtp(otpKey(reservation.getReservationId()), req.getOtp().trim(),
                OtpVerification.OtpPurpose.SELF_DRIVE_PICKUP);

        SelfDriveConfig config = resolveConfig(reservation.getCar());
        double deposit = depositOf(reservation);
        // The fare alone — the deposit sits in estimatedAmount (it was collected up front) but
        // it is NOT revenue, so the rental's billable base has to exclude it.
        double fare = Math.max(0.0, nz(reservation.getEstimatedAmount()) - deposit);

        Rental rental = Rental.builder()
                .reservation(reservation)
                .customer(reservation.getCustomer())
                .car(reservation.getCar())
                .driver(null)                       // self-drive — nobody is assigned, by design
                .actualPickupDatetime(LocalDateTime.now())
                .odometerAtPickup(req.getOdometerAtPickup())
                .fuelLevelAtPickup(req.getFuelLevelAtPickup())
                .depositHeld(deposit)
                .baseAmount(fare)
                .totalAmount(fare)
                .remarks(req.getRemarks())
                .pickupPhotos(joinPhotos(req.getPhotoUrls()))
                .build();

        reservation.getCar().setAvailabilityStatus(Car.AvailabilityStatus.BOOKED);
        carRepository.save(reservation.getCar());
        Rental saved = rentalRepository.save(rental);

        notificationService.notifyCustomer(reservation.getCustomer().getCustomerId(),
                "Self-drive trip started",
                "Keys handed over for " + reservation.getCar().getBrand() + " " + reservation.getCar().getModel()
                        + ". Free km included: " + (int) (config.getFreeKmPerDay() * days(reservation))
                        + " km. Beyond that, ₹" + config.getOverageRatePerKm() + "/km.",
                Notification.Type.GENERAL, reservation.getReservationId());

        log.info("[DDT] Self-drive pickup confirmed: reservation {} -> rental {} (odo {})",
                reservation.getReservationId(), saved.getRentalId(), req.getOdometerAtPickup());

        return toActiveHandover(saved);
    }

    @Override
    @Transactional
    public SelfDriveHandoverDTO.SettlementResponse confirmReturn(SelfDriveHandoverDTO.ReturnRequest req) {
        if (req.getRentalId() == null || req.getOtp() == null || req.getOtp().isBlank())
            throw new InvalidOtpException("Rental and the customer's OTP are both required.");
        if (req.getOdometerAtReturn() == null)
            throw new InvalidPricingException("A valid odometer reading at return is required.");

        // Same lock as RentalServiceImpl.completeRental() — stops a double-submitted drop-off
        // from settling the deposit (and crediting the refund) twice.
        Rental rental = rentalRepository.findByIdForUpdate(req.getRentalId())
                .orElseThrow(() -> new RentalNotFoundException(req.getRentalId()));
        Reservation reservation = rental.getReservation();
        assertSelfDrive(reservation);

        if (rental.getRentalStatus() != Rental.RentalStatus.ACTIVE)
            throw new InvalidDateRangeException("This trip has already been closed.");

        otpService.verifyOtp(otpKey(reservation.getReservationId()), req.getOtp().trim(),
                OtpVerification.OtpPurpose.SELF_DRIVE_RETURN);

        double kmDriven = req.getOdometerAtReturn() - nz(rental.getOdometerAtPickup());
        if (kmDriven < 0)
            throw new InvalidPricingException("Return odometer reading can't be less than the pickup reading ("
                    + rental.getOdometerAtPickup() + " km).");

        SelfDriveConfig config = resolveConfig(rental.getCar());
        int days = days(reservation);
        LocalDateTime actualReturn = req.getActualReturnDatetime() != null
                ? req.getActualReturnDatetime() : LocalDateTime.now();

        // ── 1) Extra km beyond the included allowance ──
        double freeKmAllowance = nz(config.getFreeKmPerDay()) * days;
        double extraKm = Math.max(0.0, kmDriven - freeKmAllowance);
        double overageRate = config.getOverageRatePerKm() == null ? 0.0 : config.getOverageRatePerKm();
        double extraKmCharges = round2(extraKm * overageRate);

        // Sanity guard, same spirit as the chauffeur flow: a mistyped odometer (extra digit)
        // must not silently bill thousands in overage. 1000 km/day is already generous.
        if (kmDriven > days * 1000.0)
            throw new InvalidPricingException("Odometer implies " + (int) kmDriven + " km over " + days
                    + " day(s) — please re-check the reading.");

        // ── 2) Late return, charged per hour from the scheduled return time ──
        LocalDateTime scheduledReturn = scheduledReturn(reservation);
        long lateHours = 0;
        if (actualReturn.isAfter(scheduledReturn)) {
            long lateMinutes = Duration.between(scheduledReturn, actualReturn).toMinutes();
            lateHours = (lateMinutes + 59) / 60;   // round any part-hour up
        }
        double lateReturnCharges = round2(lateHours * nz(config.getLateReturnPenaltyPerHour()));

        // ── 3) Damage — applied straight away, since an ADMIN is the one entering it here
        //        (mirrors completeRental()'s callerIsAdmin branch; no approval queue needed).
        double damageCharges = nz(req.getDamageCharges());
        double damageCap = rental.getCar().getRentPerDay() != null ? rental.getCar().getRentPerDay() * 200 : 500000.0;
        if (damageCharges > damageCap)
            throw new InvalidPricingException("Reported damage of ₹" + damageCharges
                    + " looks implausible — please verify and re-enter.");

        // ── 4) Settle against the deposit ──
        double deposit = rental.getDepositHeld() != null ? rental.getDepositHeld() : depositOf(reservation);
        double fare = nz(rental.getBaseAmount());
        double deductions = round2(extraKmCharges + lateReturnCharges + damageCharges);
        double refund = round2(Math.max(0.0, deposit - deductions));
        double extraDue = round2(Math.max(0.0, deductions - deposit));
        double finalBill = round2(fare + deductions);

        rental.setActualReturnDatetime(actualReturn);
        rental.setOdometerAtReturn(req.getOdometerAtReturn());
        rental.setTotalKmDriven(kmDriven);
        rental.setFuelLevelAtReturn(req.getFuelLevelAtReturn());
        rental.setExtraKmCharges(extraKmCharges);
        rental.setLateReturnCharges(lateReturnCharges);
        rental.setDamageCharges(damageCharges);
        rental.setDamageApprovalStatus(damageCharges > 0
                ? Rental.DamageApprovalStatus.APPROVED : Rental.DamageApprovalStatus.NONE);
        rental.setPendingDamageCharges(null);
        rental.setDepositDeductions(deductions);
        rental.setDepositRefunded(refund);
        rental.setTotalAmount(finalBill);
        rental.setReturnPhotos(joinPhotos(req.getPhotoUrls()));
        rental.setRentalStatus(Rental.RentalStatus.COMPLETED);
        if (req.getRemarks() != null && !req.getRemarks().isBlank()) {
            rental.setRemarks(req.getRemarks());
        }

        rental.getCar().setAvailabilityStatus(Car.AvailabilityStatus.AVAILABLE);
        carRepository.save(rental.getCar());

        // Keep "My Bookings" honest: the bill is now fare + deductions. The customer already
        // paid fare + deposit up front, so:
        //   deductions <= deposit  -> nothing left to pay; the unused deposit is refunded
        //   deductions >  deposit  -> the shortfall shows up as a real balance due
        reservation.setEstimatedAmount(finalBill);
        if (extraDue <= 0) {
            reservation.setAmountPaid(finalBill);   // fully settled, refund goes out separately
        }
        reservation.setReservationStatus(Reservation.ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);

        // Refund the unused deposit. self-drive.deposit-refund-method decides HOW — RAZORPAY
        // sends it back to the customer's original payment method, WALLET (the default) credits
        // it instantly for reuse on the next booking. A failed Razorpay attempt always falls
        // back to wallet credit rather than leaving the customer with no refund at all.
        String actualRefundMethod = null;
        if (refund > 0) {
            actualRefundMethod = issueDepositRefund(reservation, refund);
        }
        rental.setDepositRefundMethod(actualRefundMethod);

        // Trust score, same shape as the chauffeur flow: clean trip up, damage/late down.
        int trustDelta = 2;
        if (damageCharges > 0) trustDelta -= 8;
        if (lateHours > 0) trustDelta -= (int) Math.min(15, lateHours);
        customerService.adjustTrustScore(reservation.getCustomer().getCustomerId(), trustDelta);

        rentalRepository.save(rental);

        String summary = refund > 0
                ? "₹" + refund + " of your ₹" + deposit + " security deposit has been "
                    + ("RAZORPAY".equals(actualRefundMethod)
                        ? "refunded to your original payment method (5-7 working days)."
                        : "credited to your wallet.")
                : (extraDue > 0
                    ? "Charges exceeded your ₹" + deposit + " deposit — ₹" + extraDue + " is still due."
                    : "Your ₹" + deposit + " security deposit was fully used up by trip charges.");

        notificationService.notifyCustomer(reservation.getCustomer().getCustomerId(),
                "Self-drive trip completed", summary + " Total km driven: " + (int) kmDriven + ".",
                Notification.Type.GENERAL, reservation.getReservationId());

        log.info("[DDT] Self-drive return settled: rental {} km={} deductions=₹{} refund=₹{} extraDue=₹{}",
                rental.getRentalId(), kmDriven, deductions, refund, extraDue);

        return SelfDriveHandoverDTO.SettlementResponse.builder()
                .rentalId(rental.getRentalId())
                .tripMode(Reservation.TripMode.SELF_DRIVE)
                .kmDriven(kmDriven)
                .freeKmAllowance(freeKmAllowance)
                .extraKm(extraKm)
                .extraKmCharges(extraKmCharges)
                .lateHours(lateHours)
                .lateReturnCharges(lateReturnCharges)
                .damageCharges(damageCharges)
                .fareAmount(fare)
                .totalDeductions(deductions)
                .depositHeld(deposit)
                .depositRefunded(refund)
                .extraAmountDue(extraDue)
                .finalBillAmount(finalBill)
                .summary(summary)
                .pickupPhotoUrls(splitPhotos(rental.getPickupPhotos()))
                .returnPhotoUrls(splitPhotos(rental.getReturnPhotos()))
                .depositRefundMethod(actualRefundMethod)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void assertSelfDrive(Reservation reservation) {
        if (reservation.getTripMode() != Reservation.TripMode.SELF_DRIVE)
            throw new UnauthorizedAccessException(
                    "This is a with-driver booking — use the driver pickup/drop-off flow instead.");
    }

    private void assertFullyPaid(Reservation reservation) {
        double paid = nz(reservation.getAmountPaid());
        double due = nz(reservation.getEstimatedAmount());
        if (paid + 0.01 < due)
            throw new PaymentFailedException("Self-drive bookings must be paid in full (including the "
                    + "refundable security deposit) before handover. Balance due: ₹" + round2(due - paid) + ".");
    }

    private SelfDriveConfig resolveConfig(Car car) {
        return selfDriveConfigRepository.findByCarCategory(car.getCarCategory())
                .orElseThrow(() -> new SelfDriveConfigNotFoundException(
                        "No self-drive configuration found for " + car.getCarCategory() + " cars."));
    }

    /** The deposit snapshotted onto the reservation at booking time (never re-read from config). */
    private double depositOf(Reservation reservation) {
        return reservation.getSelfDriveDeposit() == null ? 0.0 : reservation.getSelfDriveDeposit();
    }

    private int days(Reservation reservation) {
        return reservation.getTotalDays() != null && reservation.getTotalDays() > 0
                ? reservation.getTotalDays() : 1;
    }

    private LocalDateTime scheduledReturn(Reservation reservation) {
        LocalTime time = reservation.getPickupTime() != null ? reservation.getPickupTime() : LocalTime.of(9, 0);
        return reservation.getReturnDate().atTime(time);
    }

    private SelfDriveHandoverDTO.ActiveHandover toActiveHandover(Rental rental) {
        Reservation reservation = rental.getReservation();
        SelfDriveConfig config = resolveConfig(rental.getCar());
        LocalDateTime scheduled = scheduledReturn(reservation);
        return SelfDriveHandoverDTO.ActiveHandover.builder()
                .rentalId(rental.getRentalId())
                .reservationId(reservation.getReservationId())
                .customerName(rental.getCustomer().getFirstName() + " " + rental.getCustomer().getLastName())
                .customerMobile(rental.getCustomer().getMobileNumber())
                .carBrand(rental.getCar().getBrand())
                .carModel(rental.getCar().getModel())
                .carRegistrationNumber(rental.getCar().getRegistrationNumber())
                .actualPickupDatetime(rental.getActualPickupDatetime())
                .scheduledReturnDatetime(scheduled)
                .odometerAtPickup(rental.getOdometerAtPickup())
                .freeKmAllowance(nz(config.getFreeKmPerDay()) * days(reservation))
                .overageRatePerKm(config.getOverageRatePerKm())
                .depositHeld(rental.getDepositHeld() != null ? rental.getDepositHeld() : depositOf(reservation))
                .overdue(LocalDateTime.now().isAfter(scheduled))
                .pickupPhotoUrls(splitPhotos(rental.getPickupPhotos()))
                .build();
    }

    /**
     * Issues the deposit refund and returns which method actually succeeded ("WALLET" or
     * "RAZORPAY"). Runs in the same transaction as the rest of confirmReturn() — if the wallet
     * credit below fails, the whole return-confirmation rolls back together with it, which is
     * the correct behaviour (an admin retries the whole operation, not just the refund half).
     */
    private String issueDepositRefund(Reservation reservation, double refundAmount) {
        if ("RAZORPAY".equalsIgnoreCase(depositRefundMethod)
                && reservation.getRazorpayPaymentId() != null && !reservation.getRazorpayPaymentId().isBlank()) {
            try {
                JSONObject refundRequest = new JSONObject();
                // Amount in paise, partial refund (the deposit only — not the whole original
                // payment, which also covered the trip fare that's rightfully kept).
                refundRequest.put("amount", Math.round(refundAmount * 100));
                razorpayClient.payments.refund(reservation.getRazorpayPaymentId(), refundRequest);
                log.info("[DDT] Self-drive deposit refunded via Razorpay: reservation {} amount=₹{}",
                        reservation.getReservationId(), refundAmount);
                return "RAZORPAY";
            } catch (RazorpayException e) {
                log.error("[DDT-ERROR] Razorpay deposit refund failed for reservation {} (₹{}): {} — "
                                + "falling back to wallet credit.",
                        reservation.getReservationId(), refundAmount, e.getMessage());
                // Fall through to wallet credit below — the customer still gets their money back,
                // just not via their original payment method this time.
            }
        }
        Customer customer = customerRepository.findById(reservation.getCustomer().getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(reservation.getCustomer().getCustomerId()));
        customer.setWalletBalance(round2(nz(customer.getWalletBalance()) + refundAmount));
        customerRepository.save(customer);
        return "WALLET";
    }

    /** Comma-joins uploaded photo URLs for storage; blanks/nulls are dropped. */
    private static String joinPhotos(java.util.List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        String joined = urls.stream().filter(u -> u != null && !u.isBlank())
                .collect(java.util.stream.Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private static java.util.List<String> splitPhotos(String csv) {
        if (csv == null || csv.isBlank()) return java.util.List.of();
        return java.util.Arrays.asList(csv.split(","));
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
