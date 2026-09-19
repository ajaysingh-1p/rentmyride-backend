package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.*;
import com.rentmyride.dtos.PromoCodeDTO;
import com.rentmyride.dtos.ReservationDTO;
import com.rentmyride.entities.*;
import com.rentmyride.repository.*;
import com.rentmyride.service.CustomerService;
import com.rentmyride.service.NotificationService;
import com.rentmyride.service.PromoCodeService;
import com.rentmyride.service.ReservationService;
import com.rentmyride.util.BiharLocations;
import com.rentmyride.util.DistanceUtil;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CarRepository carRepository;
    private final CustomerRepository customerRepository;
    private final NotificationService notificationService;
    private final CustomerService customerService;
    private final PromoCodeService promoCodeService;
    private final com.rentmyride.service.EngagementService engagementService;
    private final com.rentmyride.repository.DriverRepository driverRepository;
    private final com.rentmyride.service.BusinessSettingsService businessSettingsService;
    private final RazorpayClient razorpayClient;
    private final com.rentmyride.repository.AdminRepository adminRepository;
    private final com.rentmyride.repository.PaymentRepository paymentRepository;
    private final com.rentmyride.repository.RentalRepository rentalRepository;
    private final com.rentmyride.repository.InvoiceRepository invoiceRepository;
    private final com.rentmyride.repository.SelfDriveConfigRepository selfDriveConfigRepository;

    // ── Business rules (see class-level notes) ──
    // NOTE: cancellation fee, reschedule fee, free-cancellation window, min deposit, and the
    // LOCAL per-km policy are now admin-configurable via Business Settings (see calls to
    // businessSettingsService.getSettings() below) — these two remain fixed defaults/fallbacks.
    private static final double LOCAL_PACKAGE_RATE_PER_DAY = 2600.0; // fallback only if car has no rentPerDay set
    private static final double MIN_NIGHT_CHARGE = 300.0;             // per night, outstation trips only

    @Override
    @Transactional
    public ReservationDTO createReservation(Long customerId, ReservationDTO.CreateRequest req) {
        return mapToDTO(buildReservation(customerId, req));
    }

    // New feature: "create only after payment". Runs the EXACT same car-lock/conflict-check/
    // pricing/promo/wallet logic as createReservation() above — but within ONE transaction,
    // immediately flips the freshly-built reservation from PENDING straight to CONFIRMED+PAID
    // before that transaction ever commits. That intermediate PENDING state is never visible to
    // any other query/transaction (another customer browsing cars, admin's reservation list,
    // etc.) — from everyone else's perspective, this reservation only ever exists already paid.
    // See PaymentServiceImpl.verifyNewBookingPayment(), which calls this only AFTER Razorpay's
    // signature has been verified.
    @Override
    @Transactional
    public ReservationDTO createReservationWithPayment(Long customerId, ReservationDTO.CreateRequest req,
            Reservation.PaymentType paymentType, Double amount, String razorpayPaymentId) {
        Reservation r = buildReservation(customerId, req);

        // Bug fix: buildReservation() already deducts wallet credits FOR REAL and stores
        // estimatedAmount as whatever's still owed AFTER that deduction — so when the wallet
        // balance covers the whole trip cost, estimatedAmount lands on ₹0 here already. Routing
        // that through payForReservation() next used to be the only path, but that method's very
        // first check is `if (remaining <= 0) throw ... "already fully paid"` — because normally
        // remaining==0 DOES mean a stray extra payment attempt on an already-settled booking, not
        // "there was never anything to pay via Razorpay in the first place". Confirm directly
        // instead: there is nothing left to charge, so there is nothing for payForReservation (or
        // a Razorpay order, see PaymentServiceImpl.createNewBookingOrder()) to do here.
        if (r.getEstimatedAmount() == null || r.getEstimatedAmount() <= 0.005) {
            r.setPaymentStatus(Reservation.BookingPaymentStatus.FULLY_PAID);
            r.setReservationStatus(Reservation.ReservationStatus.CONFIRMED);
            Reservation saved = reservationRepository.save(r);
            notifyBookingConfirmed(saved);
            notificationService.notifyAdmins(
                    "Payment Received",
                    saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName() +
                            " paid the full amount via wallet credits for booking #" + saved.getReservationId() + ".",
                    Notification.Type.PAYMENT_RECEIVED,
                    saved.getReservationId()
            );
            return mapToDTO(saved);
        }

        ReservationDTO.PayRequest payRequest = new ReservationDTO.PayRequest(paymentType, amount, "RAZORPAY", razorpayPaymentId);
        // Self-invocation is fine here — both this method and payForReservation() are
        // @Transactional, and since this is a direct `this.` call (not through the Spring proxy),
        // it simply joins the ALREADY-ACTIVE transaction from this outer method rather than
        // starting a separate one — exactly what's needed for the "never visible unpaid" guarantee.
        return payForReservation(r.getReservationId(), payRequest);
    }

    private Reservation buildReservation(Long customerId, ReservationDTO.CreateRequest req) {
        if (req.getReturnDate().isBefore(req.getPickupDate()))
            throw new InvalidDateRangeException();
        // Issue #23 fix: a pickup date already in the past was previously accepted outright —
        // nothing stopped a reservation being created for "yesterday", which then shows up as an
        // impossible/confusing booking for both the customer and whoever assigns the car.
        if (req.getPickupDate().isBefore(java.time.LocalDate.now()))
            throw new InvalidDateRangeException("Pickup date cannot be in the past.");
        // Bug fix: pickup DATE being validated wasn't enough — a same-day booking with a pickup
        // TIME already in the past (e.g. it's 5 PM and pickupTime = 09:00) sailed straight
        // through, since only the date component was checked. Mirrors the frontend's own check.
        if (req.getPickupDate().isEqual(java.time.LocalDate.now()) && req.getPickupTime() != null
                && req.getPickupTime().isBefore(java.time.LocalTime.now().plusMinutes(30)))
            throw new InvalidDateRangeException("Pickup time must be at least 30 minutes from now.");

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        // Issue #13 fix: lock the car row for the rest of this transaction BEFORE checking for
        // date conflicts. Previously, two customers booking the same car at nearly the same
        // moment could both run the conflict-check, both see "no conflicts" (because neither had
        // saved yet), and both successfully save a reservation for overlapping dates. With the
        // lock, the second request's findByIdForUpdate() blocks until the first request's
        // transaction commits, so it then re-runs the conflict-check against the booking that
        // just got saved and correctly gets rejected.
        Car car = carRepository.findByIdForUpdate(req.getCarId())
                .orElseThrow(() -> new CarNotFoundException(req.getCarId()));
        if (car.getAvailabilityStatus() != Car.AvailabilityStatus.AVAILABLE)
            throw new CarNotAvailableException(req.getCarId());

        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                req.getCarId(), req.getPickupDate(), req.getReturnDate());
        if (!conflicts.isEmpty())
            throw new CarNotAvailableException("Car already reserved for selected dates.");

        int totalDays = (int) (req.getReturnDate().toEpochDay() - req.getPickupDate().toEpochDay());
        Reservation.TripType tripType = req.getTripType() == null ? Reservation.TripType.LOCAL : req.getTripType();
        PriceCalculation price = calculatePrice(car, tripType, req.getPickupLocation(), req.getDropLocation(),
                req.getViaLocations(), totalDays);

        // Self-drive feature: re-validates against the admin config at booking time (not just at
        // estimate time) — the config could have been disabled or changed in the gap between the
        // customer viewing the estimate and hitting "Confirm Booking". The deposit is snapshotted
        // onto the reservation itself so it survives any later admin edit to the config.
        Reservation.TripMode tripMode = req.getTripMode() == null ? Reservation.TripMode.WITH_DRIVER : req.getTripMode();
        Double selfDriveDeposit = null;
        if (tripMode == Reservation.TripMode.SELF_DRIVE) {
            selfDriveDeposit = resolveSelfDriveConfig(car).getSecurityDeposit();
        }

        // Issue #37 — discount stacking order, explained end-to-end (this is intentional, not a bug):
        //   1) PROMO CODE first — a flat/percentage discount off the estimated fare, consumed via
        //      applyAndConsume() (which also enforces the code's own usage limit/expiry/min-amount).
        //   2) LOYALTY TIER next — Silver (5%) / Gold (10%) is applied on what's LEFT after the
        //      promo discount, not on the original fare. So promo and loyalty don't double-count
        //      the same rupee: loyalty only discounts the "already-discounted" remainder.
        //   3) WALLET CREDITS last — applied as a straight cash deduction against whatever amount
        //      is still due after promo + loyalty, capped at the customer's available balance.
        // Net effect: promo and loyalty shrink the bill (multiplicatively, since loyalty applies
        // to the post-promo amount), then wallet pays down the resulting balance in cash.
        double discount = 0.0;
        String appliedPromoCode = null;
        if (req.getPromoCode() != null && !req.getPromoCode().isBlank()) {
            discount = promoCodeService.applyAndConsume(req.getPromoCode(), price.estimatedAmount);
            if (discount > 0) appliedPromoCode = req.getPromoCode().trim().toUpperCase();
        }

        // Loyalty tier discount (Silver 5% / Gold 10%) stacks on top of any promo discount —
        // applied to the post-promo remainder, per the stacking order documented above.
        double loyaltyPct = engagementService.getLoyaltyDiscountPct(customerId);
        if (loyaltyPct > 0) {
            double afterPromo = Math.max(0, price.estimatedAmount - discount);
            // Rounded to paise, matching estimatePrice() exactly — otherwise the quote and the
            // saved reservation drift apart by fractions of a rupee and the balance never
            // settles cleanly to zero.
            discount += Math.round(afterPromo * (loyaltyPct / 100.0) * 100) / 100.0;
        }

        double finalAmount = Math.max(0, price.estimatedAmount - discount);

        // Bug fix: the self-drive security deposit was only ever snapshotted onto the
        // reservation — it never made it into the amount the customer was actually billed, so
        // the deposit was never collected at all. It's added on TOP of the fare here, and
        // deliberately AFTER promo/loyalty: a refundable deposit isn't revenue, so discounting
        // it would mean refunding more than was taken.
        if (selfDriveDeposit != null) finalAmount += selfDriveDeposit;

        double walletUsed = 0.0;
        if (req.isUseWalletCredits() && finalAmount > 0) {
            walletUsed = customerService.deductWalletBalance(customerId, finalAmount);
            finalAmount = Math.max(0, finalAmount - walletUsed);
        }

        Reservation reservation = Reservation.builder()
                .customer(customer).car(car)
                .pickupDate(req.getPickupDate())
                .pickupTime(req.getPickupTime() != null ? req.getPickupTime() : java.time.LocalTime.of(9, 0))
                .returnDate(req.getReturnDate())
                .tripType(tripType)
                .pickupLocation(req.getPickupLocation()).dropLocation(req.getDropLocation())
                .viaLocations(req.getViaLocations() == null ? null : String.join(", ", req.getViaLocations()))
                .distanceKm(price.distanceKm)
                .nights(price.nights)
                .baseFare(price.baseFare)
                .nightCharges(price.nightCharges)
                .promoCode(appliedPromoCode)
                .discountAmount(discount)
                .walletCreditsUsed(walletUsed)
                .estimatedAmount(finalAmount)
                .specialRequests(req.getSpecialRequests())
                .tripMode(tripMode)
                .selfDriveDeposit(selfDriveDeposit)
                .build();
        engagementService.clearBookingIntent(customerId, req.getCarId());
        Reservation saved = reservationRepository.save(reservation);

        // Admin panel alert — new booking just came in, before any payment/confirmation.
        notificationService.notifyAdmins(
                "New Booking Received",
                customer.getFirstName() + " " + customer.getLastName() + " booked " +
                        car.getBrand() + " " + car.getModel() + " for " + req.getPickupDate() +
                        ". Amount: ₹" + saved.getEstimatedAmount(),
                Notification.Type.NEW_RESERVATION,
                saved.getReservationId()
        );

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationDTO.EstimateResponse estimatePrice(ReservationDTO.EstimateRequest req) {
        // An ADMIN previewing a price gets no loyalty discount — the tier belongs to a customer,
        // not to whoever happens to be looking at the quote.
        Long customerId = null;
        var user = com.rentmyride.security.SecurityUtils.currentUser();
        if (user.isCustomer()) customerId = user.getUserId();
        return estimatePrice(req, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationDTO.EstimateResponse estimatePrice(ReservationDTO.EstimateRequest req, Long customerId) {
        Car car = carRepository.findById(req.getCarId())
                .orElseThrow(() -> new CarNotFoundException(req.getCarId()));

        int totalDays = 0;
        if (req.getPickupDate() != null && req.getReturnDate() != null
                && req.getReturnDate().isAfter(req.getPickupDate())) {
            totalDays = (int) (req.getReturnDate().toEpochDay() - req.getPickupDate().toEpochDay());
        }

        Reservation.TripType tripType = req.getTripType() == null ? Reservation.TripType.LOCAL : req.getTripType();
        PriceCalculation price = calculatePrice(car, tripType, req.getPickupLocation(), req.getDropLocation(),
                req.getViaLocations(), totalDays);

        Reservation.TripMode tripMode = req.getTripMode() == null ? Reservation.TripMode.WITH_DRIVER : req.getTripMode();
        SelfDriveConfig selfDriveConfig = null;
        if (tripMode == Reservation.TripMode.SELF_DRIVE) {
            selfDriveConfig = resolveSelfDriveConfig(car);
        }

        PromoCodeDTO.ValidateResponse promoPreview = null;
        if (req.getPromoCode() != null && !req.getPromoCode().isBlank()) {
            promoPreview = promoCodeService.validate(req.getPromoCode(), price.estimatedAmount);
        }

        // Bug fix (the "Razorpay shows less than the page" report): promo was the only discount
        // this quote knew about, but buildReservation() and createNewBookingOrder() also applied
        // a loyalty-tier discount, and self-drive added a deposit on top. Three different places
        // doing three different sums. All of it is resolved here now, and payableAmount is the
        // one number the UI and the Razorpay order both read.
        boolean promoOk   = promoPreview != null && promoPreview.isValid();
        double promoDisc  = promoOk ? promoPreview.getDiscountAmount() : 0.0;
        double afterPromo = promoOk ? promoPreview.getFinalAmount()    : price.estimatedAmount;

        double loyaltyPct   = customerId == null ? 0.0 : engagementService.getLoyaltyDiscountPct(customerId);
        double loyaltyDisc  = Math.round(afterPromo * (loyaltyPct / 100.0) * 100) / 100.0;
        double afterLoyalty = Math.max(0, afterPromo - loyaltyDisc);
        double deposit      = selfDriveConfig != null ? selfDriveConfig.getSecurityDeposit() : 0.0;

        return ReservationDTO.EstimateResponse.builder()
                .tripType(tripType)
                .distanceKm(price.distanceKm)
                // Inclusive calendar-day count, consistent with what's actually billed and with
                // Reservation.totalDays — see calculatePrice()/entity @PrePersist for the same fix.
                .totalDays(totalDays + 1)
                .nights(price.nights)
                .ratePerKm(car.getRatePerKm())
                .baseFare(price.baseFare)
                .nightCharges(price.nightCharges)
                .estimatedAmount(price.estimatedAmount)
                .promoCode(promoOk ? req.getPromoCode().trim().toUpperCase() : null)
                .discountAmount(promoDisc)
                .finalAmount(afterLoyalty)
                .loyaltyDiscountPct(loyaltyPct)
                .loyaltyDiscountAmount(loyaltyDisc)
                .payableAmount(afterLoyalty + deposit)
                .pricingMethod(price.pricingMethod)
                .tripMode(tripMode)
                .selfDriveDeposit(selfDriveConfig != null ? selfDriveConfig.getSecurityDeposit() : null)
                .selfDriveFreeKmPerDay(selfDriveConfig != null ? selfDriveConfig.getFreeKmPerDay() : null)
                .build();
    }

    // Self-drive feature: looks up the admin-configured deposit/km-limit for this car's
    // category. Throws a clear, customer-facing error if self-drive isn't set up (or has been
    // disabled) for that category — nothing here is hardcoded, it's whatever the admin last
    // saved on the Self-Drive Settings page.
    private SelfDriveConfig resolveSelfDriveConfig(Car car) {
        SelfDriveConfig config = selfDriveConfigRepository.findByCarCategory(car.getCarCategory())
                .orElseThrow(() -> new SelfDriveConfigNotFoundException(
                        "Self-drive isn't available for " + car.getCarCategory() + " cars yet."));
        if (!config.isEnabled()) {
            throw new SelfDriveConfigNotFoundException(
                    "Self-drive is currently turned off for " + car.getCarCategory() + " cars.");
        }
        return config;
    }

    // ── Booking payment: full payment, or a minimum ₹1000 deposit to confirm ──
    @Override
    @Transactional
    public ReservationDTO payForReservation(Long reservationId, ReservationDTO.PayRequest req) {
        // Race-condition fix: lock this reservation row before reading amountPaid/estimatedAmount.
        // Without it, two near-simultaneous payment calls for the same reservation (e.g. a
        // webhook and a browser callback both landing together, or a double-submitted deposit)
        // could both read the SAME amountPaid, both compute the same "remaining" balance, and
        // both add their own amount on top — pushing amountPaid past estimatedAmount and creating
        // two Payment rows for what should have been a single payment. The lock makes the second
        // call wait and re-read the already-updated amountPaid instead of racing against the first.
        Reservation r = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        // IDOR guard (issue #1/#2 pattern) — a customer may only pay towards their own booking.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(r.getCustomer().getCustomerId());

        if (r.getReservationStatus() == Reservation.ReservationStatus.CANCELLED)
            throw new PaymentFailedException("This reservation has been cancelled.");

        double remaining = r.getEstimatedAmount() - r.getAmountPaid();
        if (remaining <= 0)
            throw new PaymentFailedException("This reservation is already fully paid.");

        double amount;
        if (req.getPaymentType() == Reservation.PaymentType.FULL) {
            amount = remaining; // pay whatever is left in full
        } else {
            amount = req.getAmount() == null ? 0 : req.getAmount();
            double minDeposit = businessSettingsService.getSettings().getMinDepositAmount();
            if (amount < minDeposit)
                throw new PaymentFailedException("Minimum deposit to confirm a booking is ₹" + (int) minDeposit + ".");
            if (amount > remaining) amount = remaining; // don't overpay
        }

        r.setAmountPaid(r.getAmountPaid() + amount);
        r.setPaymentType(req.getPaymentType());
        if (req.getRazorpayPaymentId() != null) {
            r.setRazorpayPaymentId(req.getRazorpayPaymentId());
        }
        r.setPaymentStatus(r.getAmountPaid() >= r.getEstimatedAmount()
                ? Reservation.BookingPaymentStatus.FULLY_PAID
                : Reservation.BookingPaymentStatus.DEPOSIT_PAID);

        // Record this as an actual Payment row — previously booking-time payments only updated
        // the Reservation's own amountPaid/paymentStatus fields and were never written to the
        // Payment table at all (it required a Rental, which doesn't exist yet at booking time).
        // That's why admin's Payments page and total-revenue figures never reflected booking
        // payments. See Payment entity for the nullable-rental fix that makes this possible.
        com.rentmyride.entities.Payment.PaymentMethod method;
        try {
            method = com.rentmyride.entities.Payment.PaymentMethod.valueOf(
                    (req.getPaymentMethod() == null ? "UPI" : req.getPaymentMethod()).toUpperCase());
        } catch (IllegalArgumentException e) {
            method = com.rentmyride.entities.Payment.PaymentMethod.UPI; // Razorpay itself tracks the exact channel
        }
        paymentRepository.save(com.rentmyride.entities.Payment.builder()
                .reservation(r)
                .customer(r.getCustomer())
                .razorpayPaymentId(req.getRazorpayPaymentId())
                .baseAmount(amount)
                .gstPercentage(0.0)
                .gstAmount(0.0)
                .totalAmount(amount)
                .paymentMethod(method)
                .paymentStatus(com.rentmyride.entities.Payment.PaymentStatus.SUCCESS)
                .paymentDatetime(java.time.LocalDateTime.now())
                .build());

        // Admin panel alert — a real payment (deposit or full) just landed against this booking.
        notificationService.notifyAdmins(
                "Payment Received",
                r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName() +
                        " paid ₹" + amount + " (" + req.getPaymentType() + ") for booking #" + r.getReservationId() + ".",
                Notification.Type.PAYMENT_RECEIVED,
                r.getReservationId()
        );

        // Any qualifying payment (full, or a deposit of at least ₹1000) confirms the booking
        if (r.getReservationStatus() == Reservation.ReservationStatus.PENDING) {
            r.setReservationStatus(Reservation.ReservationStatus.CONFIRMED);
            notifyBookingConfirmed(r);
        }

        Reservation savedReservation = reservationRepository.save(r);

        // Bug fix: a payment made AFTER the trip completes (e.g. paying off a balance-due amount
        // from extra km/damage, once the rental is already COMPLETED and its Invoice already
        // exists) updated this Reservation's own amountPaid/paymentStatus just fine — but never
        // touched the Invoice that was generated back at drop-off (see InvoiceServiceImpl.
        // generateInvoice(), which sets PAID/UNPAID exactly ONCE, from whatever was paid at that
        // single moment). So admin's Invoices page kept showing "Unpaid" forever afterward, even
        // though the Payments page correctly showed this exact payment as "Success" — nothing
        // wrong with the payment itself, just nothing ever went back and updated the invoice
        // once the balance actually got settled. Re-check and refresh it here, every time a
        // payment against this reservation succeeds.
        rentalRepository.findByReservation_ReservationId(reservationId).ifPresent(rental ->
            invoiceRepository.findByRental_RentalId(rental.getRentalId()).ifPresent(invoice -> {
                double due = Math.max(0.0, invoice.getGrandTotal() - savedReservation.getAmountPaid());
                invoice.setDueAmount(Math.round(due * 100) / 100.0);
                if (invoice.getInvoiceStatus() != com.rentmyride.entities.Invoice.InvoiceStatus.CANCELLED
                        && invoice.getInvoiceStatus() != com.rentmyride.entities.Invoice.InvoiceStatus.REFUNDED) {
                    invoice.setInvoiceStatus(due <= 0.01
                            ? com.rentmyride.entities.Invoice.InvoiceStatus.PAID
                            : com.rentmyride.entities.Invoice.InvoiceStatus.UNPAID);
                }
                invoiceRepository.save(invoice);
            })
        );

        return mapToDTO(savedReservation);
    }

    // ── Reschedule: free up to 12 hours before the ORIGINAL pickup, otherwise a ₹300 fee applies ──
    @Override
    @Transactional
    public ReservationDTO.RescheduleResponse rescheduleReservation(Long id, ReservationDTO.RescheduleRequest req) {
        Reservation r = reservationRepository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
        // IDOR guard — a customer may only reschedule their own booking.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(r.getCustomer().getCustomerId());

        if (r.getReservationStatus() == Reservation.ReservationStatus.CANCELLED)
            throw new UnauthorizedAccessException("A cancelled booking cannot be rescheduled.");
        if (r.getReservationStatus() == Reservation.ReservationStatus.COMPLETED)
            throw new UnauthorizedAccessException("A completed rental cannot be rescheduled.");
        // Bug fix: Reservation and Rental used to go out of sync — once a trip's Rental record
        // exists (the car has actually been physically handed over, chauffeur-driven or
        // self-drive), this reschedule endpoint would still happily change the Reservation's
        // pickup/return dates with NO idea a Rental even exists, since Rental only tracks
        // actualPickupDatetime/actualReturnDatetime — there's no "planned dates" field on it to
        // keep in sync. The Reservation would then show a brand-new future pickup date while the
        // Rental still correctly showed the car already out since days ago — two records
        // disagreeing about the same booking. Once a trip has actually started, "reschedule" is
        // the wrong operation entirely; extendRental() (My Bookings → Rentals → Extend Rental)
        // is the one that's built to change dates on an ALREADY-ACTIVE trip, and it keeps
        // Reservation and Rental in sync correctly (see RentalServiceImpl.extendRental()).
        if (rentalRepository.findByReservation_ReservationId(id).isPresent())
            throw new UnauthorizedAccessException(
                    "This trip has already started — use \"Extend Rental\" from My Bookings instead of Reschedule.");
        if (req.getNewReturnDate().isBefore(req.getNewPickupDate()))
            throw new InvalidDateRangeException();

        // Same double-booking race as createReservation() (issue #13) applies here too — lock the
        // car row before re-checking date conflicts for the new dates.
        carRepository.findByIdForUpdate(r.getCar().getCarId());

        // Car must be free for the new dates (ignoring this reservation's own current slot)
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                r.getCar().getCarId(), req.getNewPickupDate(), req.getNewReturnDate());
        conflicts.removeIf(c -> c.getReservationId().equals(id));
        if (!conflicts.isEmpty())
            throw new CarNotAvailableException("Car is already booked for the new dates.");

        LocalDateTime originalPickupDateTime = LocalDateTime.of(r.getPickupDate(), r.getPickupTime());
        long hoursUntilOriginalPickup = Duration.between(LocalDateTime.now(), originalPickupDateTime).toHours();
        var settings = businessSettingsService.getSettings();
        boolean freeReschedule = hoursUntilOriginalPickup >= settings.getFreeCancellationWindowHours();
        double fee = freeReschedule ? 0.0 : settings.getRescheduleFee();

        int newTotalDays = (int) (req.getNewReturnDate().toEpochDay() - req.getNewPickupDate().toEpochDay());
        // Bug fix: a trip with NO via stops has r.getViaLocations() stored as "" (empty string,
        // NOT null — see buildReservation()'s String.join(", ", emptyList) -> "") — isBlank()
        // catches that whole-string case. But a booking created before req.getViaLocations() was
        // filtered with .filter(Boolean) on the frontend (see BookingPage.jsx) could have a
        // stored value like ", Kaimur" — a real stop, but with a stray blank entry baked in
        // alongside it — which splits into ["", "Kaimur"], and that leading "" then fails
        // BiharLocations.findByName() with "Stop '' is not a recognized location.". A LIVE
        // booking-creation request with a genuinely blank via-stop should still be rejected
        // (calculatePrice()'s own strict check further down does that) — but here we're
        // re-pricing a booking that already exists, so the honest response to "your old,
        // already-placed booking has a stray blank stop in it" is to drop the blank and keep the
        // real ones, not block a routine date-change over unrelated historical data.
        List<String> viaLocations = null;
        if (r.getViaLocations() != null && !r.getViaLocations().isBlank()) {
            List<String> parsedStops = java.util.Arrays.stream(r.getViaLocations().split(",\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
            if (!parsedStops.isEmpty()) viaLocations = parsedStops;
        }
        PriceCalculation price = calculatePrice(r.getCar(), r.getTripType(), r.getPickupLocation(), r.getDropLocation(),
                viaLocations, newTotalDays);

        double existingDiscount = r.getDiscountAmount() == null ? 0.0 : r.getDiscountAmount();
        double oldAmount = r.getEstimatedAmount() == null ? 0.0 : r.getEstimatedAmount();

        // Bug fix: self-drive's refundable deposit used to get silently dropped here — this
        // recalculation only ever produced fare − discount + fee, so a self-drive reschedule
        // quietly reduced estimatedAmount by the ENTIRE deposit even though nothing about the
        // deposit itself changed. Re-add it, exactly like buildReservation() does at booking time.
        double deposit = r.getSelfDriveDeposit() != null ? r.getSelfDriveDeposit() : 0.0;
        double newAmount = Math.max(0, price.estimatedAmount - existingDiscount) + fee + deposit;

        double amountPaid = r.getAmountPaid() == null ? 0.0 : r.getAmountPaid();

        // Bug fix: the new dates can price out LOWER than what was already paid (shorter trip,
        // cheaper season) just as easily as higher — that difference used to just vanish, with
        // nothing refunded and nothing shown to the customer. It's credited to their wallet here,
        // the same mechanism self-drive deposit refunds already use, and amountPaid is brought
        // down to match so balanceDue below comes out to exactly zero rather than negative.
        double refundedToWallet = 0.0;
        String rescheduleRefundNote = null;
        if (amountPaid > newAmount + 0.01) {
            refundedToWallet = Math.round((amountPaid - newAmount) * 100) / 100.0;
            // Bug fix: this used to ALWAYS credit wallet, even for a booking paid by card/UPI —
            // now it goes back to the original payment method via Razorpay when one is on file,
            // same as a cancellation refund, and only falls back to wallet credit otherwise.
            rescheduleRefundNote = issueRefund(r, refundedToWallet);
            amountPaid = newAmount;
            r.setAmountPaid(amountPaid);
        }
        double balanceDue = Math.max(0.0, Math.round((newAmount - amountPaid) * 100) / 100.0);

        r.setPickupDate(req.getNewPickupDate());
        r.setPickupTime(req.getNewPickupTime() != null ? req.getNewPickupTime() : r.getPickupTime());
        r.setReturnDate(req.getNewReturnDate());
        r.setTotalDays(newTotalDays);
        r.setDistanceKm(price.distanceKm);
        r.setNights(price.nights);
        r.setBaseFare(price.baseFare);
        r.setNightCharges(price.nightCharges);
        r.setEstimatedAmount(newAmount);
        Reservation saved = reservationRepository.save(r);

        StringBuilder notifyMsg = new StringBuilder("Your booking (RES-" + id + ") was moved to " + req.getNewPickupDate() + ".");
        if (fee > 0) notifyMsg.append(" A ₹").append(fee).append(" reschedule fee was added.");
        if (rescheduleRefundNote != null) notifyMsg.append(" ").append(rescheduleRefundNote);
        else if (balanceDue > 0) notifyMsg.append(" ₹").append(balanceDue).append(" balance is now due for the new dates.");
        notificationService.notifyCustomer(r.getCustomer().getCustomerId(), "Booking Rescheduled",
                notifyMsg.toString(), Notification.Type.GENERAL, id);

        String message;
        if (rescheduleRefundNote != null) message = "Rescheduled — " + rescheduleRefundNote;
        else if (balanceDue > 0) message = "Rescheduled — ₹" + balanceDue + " balance is now due for the new dates.";
        else message = freeReschedule ? "Rescheduled free of charge." : "Rescheduled with a ₹" + fee + " fee.";

        return ReservationDTO.RescheduleResponse.builder()
                .reservation(mapToDTO(saved))
                .freeReschedule(freeReschedule)
                .rescheduleFee(fee)
                .oldAmount(oldAmount)
                .newAmount(newAmount)
                .balanceDue(balanceDue)
                .refundedToWallet(refundedToWallet)
                .message(message)
                .build();
    }
    @Override
    @Transactional
    public ReservationDTO.CancelResponse cancelReservation(Long id) {
        // Race-condition fix: lock this reservation row before checking its current status.
        // Without it, two near-simultaneous cancel requests for the same reservation (double-tap,
        // a retried network request) could both pass the "not already cancelled" check below,
        // and both go on to call the REAL Razorpay refund API — refunding the customer TWICE for
        // one cancellation. The lock makes the second call wait, then correctly see CANCELLED
        // already and get rejected, instead of racing the first call to the refund API.
        Reservation r = reservationRepository.findByIdForUpdate(id).orElseThrow(() -> new ReservationNotFoundException(id));
        // IDOR fix (issue #2) — without this, any logged-in customer could cancel anyone else's booking
        // just by guessing/incrementing the reservationId in the URL.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(r.getCustomer().getCustomerId());

        if (r.getReservationStatus() == Reservation.ReservationStatus.CANCELLED)
            throw new UnauthorizedAccessException("This reservation is already cancelled.");
        if (r.getReservationStatus() == Reservation.ReservationStatus.COMPLETED)
            throw new UnauthorizedAccessException("A completed rental cannot be cancelled.");

        LocalDateTime pickupDateTime = LocalDateTime.of(r.getPickupDate(), r.getPickupTime());
        long hoursUntilPickup = Duration.between(LocalDateTime.now(), pickupDateTime).toHours();
        var settings = businessSettingsService.getSettings();
        boolean freeCancellation = hoursUntilPickup >= settings.getFreeCancellationWindowHours();

        double fee = freeCancellation ? 0.0 : Math.min(settings.getCancellationFee(), r.getAmountPaid());
        double refund = Math.max(0.0, r.getAmountPaid() - fee);

        if (!freeCancellation) {
            customerService.adjustTrustScore(r.getCustomer().getCustomerId(), -10); // late cancellation penalty
        }

        r.setReservationStatus(Reservation.ReservationStatus.CANCELLED);
        r.setCancellationFee(fee);
        r.setRefundAmount(refund);
        r.setCancelledAt(LocalDateTime.now());
        r.setPaymentStatus(refund > 0 && refund < r.getAmountPaid()
                ? Reservation.BookingPaymentStatus.PARTIALLY_REFUNDED
                : Reservation.BookingPaymentStatus.REFUNDED);

        // Issue #16 fix: actually call Razorpay's refund API instead of only flipping this DB
        // status — previously a "cancelled" booking showed a refund amount in our own system
        // with no real money ever moving back to the customer. issueRefund() below sends it back
        // to the ORIGINAL payment method whenever possible, only falling back to wallet credit
        // when there's genuinely nowhere else for it to go (see that method's own comment).
        String refundNote = issueRefund(r, refund);
        reservationRepository.save(r);

        String cancelMessage = freeCancellation
                ? "Your booking (RES-" + id + ") was cancelled free of charge. Refund: ₹" + refund + "."
                : "Your booking (RES-" + id + ") was cancelled within 12 hours of pickup. Fee: ₹" + fee + ", Refund: ₹" + refund + ".";
        if (refundNote != null) cancelMessage += " " + refundNote;
        notificationService.notifyCustomer(r.getCustomer().getCustomerId(), "Booking Cancelled",
                cancelMessage, Notification.Type.CANCELLATION, id);

        String responseMessage = freeCancellation
                ? "Cancelled free of charge — full refund of ₹" + refund + " will be processed."
                : "Cancelled within 12 hours of pickup — ₹" + fee + " cancellation fee deducted. Refund: ₹" + refund + ".";
        if (refundNote != null) responseMessage += " " + refundNote;

        return ReservationDTO.CancelResponse.builder()
                .reservationId(id)
                .freeCancellation(freeCancellation)
                .cancellationFee(fee)
                .refundAmount(refund)
                .message(responseMessage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationDTO getReservationById(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        // IDOR fix (issue #1) — without this, any logged-in customer (or driver) could view
        // anyone else's booking details just by guessing/incrementing the reservationId.
        Long ownerDriverId = r.getAssignedDriver() != null ? r.getAssignedDriver().getDriverId() : null;
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomerOrDriver(r.getCustomer().getCustomerId(), ownerDriverId);
        return mapToDTO(r);
    }
    @Override @Transactional(readOnly = true)
    public List<ReservationDTO> getAllReservations() {
        // Issue #24 fix — see ReservationRepository.findAllWithDetails().
        return reservationRepository.findAllWithDetails().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated counterpart for the admin "all reservations" table.
    @Override @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<ReservationDTO> getAllReservationsPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        var result = reservationRepository.findAllWithDetails(pageable).map(this::mapToDTO);
        return com.rentmyride.dtos.PageResponse.from(result);
    }
    @Override @Transactional(readOnly = true)
    public List<ReservationDTO> getReservationsByCustomer(Long customerId) {
        // IDOR fix — a customer may only list their own reservations, not another customer's.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return reservationRepository.findByCustomer_CustomerId(customerId).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public List<ReservationDTO> getReservationsByStatus(Reservation.ReservationStatus status) {
        return reservationRepository.findByReservationStatus(status).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional
    public ReservationDTO updateReservationStatus(Long id, ReservationDTO.StatusUpdateRequest req) {
        Reservation r = reservationRepository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
        boolean newlyConfirmed = req.getReservationStatus() == Reservation.ReservationStatus.CONFIRMED
                && r.getReservationStatus() != Reservation.ReservationStatus.CONFIRMED;
        r.setReservationStatus(req.getReservationStatus());
        Reservation saved = reservationRepository.save(r);
        if (newlyConfirmed) notifyBookingConfirmed(saved);
        return mapToDTO(saved);
    }
    @Override
    @Transactional(readOnly = true)
    public long countByStatus(Reservation.ReservationStatus status) {
        return reservationRepository.countByReservationStatus(status);
    }

    // Public availability check — shown on the car detail page before the customer books
    @Override
    @Transactional(readOnly = true)
    public List<ReservationDTO.BookedRange> getBookedDateRanges(Long carId) {
        return reservationRepository.findActiveByCarId(carId).stream()
                .map(r -> new ReservationDTO.BookedRange(r.getPickupDate(), r.getReturnDate(), r.getReservationStatus()))
                .collect(Collectors.toList());
    }

    // Admin assigns a driver — notify + email the customer AND the driver with trip details.
    // NOTE: this fires only here (on explicit assignment), never automatically at booking time.
    @Override
    @Transactional
    public ReservationDTO assignDriver(Long reservationId, Long driverId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        // Race-condition fix: lock the driver row BEFORE checking for date conflicts. Without
        // this, two admins assigning different (overlapping-date) reservations to the same
        // driver at nearly the same moment could both read "no conflicts" and both succeed,
        // double-booking the driver. The lock makes the second assignDriver() call wait for the
        // first one's transaction to commit, so it then re-checks conflicts against the
        // assignment that just went through.
        // Bug fix: nothing previously stopped a driver from being (re)assigned to a reservation
        // that's already finished or dead — the "Assign/Reassign Driver" panel showed up for
        // EVERY reservation except CANCELLED/REJECTED on the frontend, and this method itself
        // had no server-side guard at all, so a COMPLETED trip (car already returned, invoice
        // already generated) could still have its driver changed after the fact, which makes no
        // operational sense and could confuse the completed rental's records.
        if (r.getReservationStatus() == Reservation.ReservationStatus.COMPLETED
                || r.getReservationStatus() == Reservation.ReservationStatus.CANCELLED
                || r.getReservationStatus() == Reservation.ReservationStatus.REJECTED) {
            throw new com.rentmyride.custom_exceptions.InvalidDateRangeException(
                    "Can't assign a driver to a " + r.getReservationStatus().name().toLowerCase() + " booking.");
        }
        // Self-drive bookings never get a driver assigned — the customer drives themselves.
        if (r.getTripMode() == Reservation.TripMode.SELF_DRIVE) {
            throw new com.rentmyride.custom_exceptions.InvalidDateRangeException(
                    "This is a self-drive booking — no driver needs to be assigned.");
        }

        com.rentmyride.entities.Driver driver = driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new com.rentmyride.custom_exceptions.DriverNotFoundException(driverId));

        // Prevent double-booking: this driver can't already be assigned to another trip
        // whose dates overlap this reservation's pickup–return window.
        List<Reservation> driverConflicts = reservationRepository.findConflictingReservationsForDriver(
                driverId, r.getPickupDate(), r.getReturnDate());
        driverConflicts.removeIf(c -> c.getReservationId().equals(reservationId));
        if (!driverConflicts.isEmpty()) {
            Reservation clash = driverConflicts.get(0);
            throw new com.rentmyride.custom_exceptions.DriverNotAvailableException(
                    driver.getFirstName() + " " + driver.getLastName() +
                            " is already assigned to booking #RES-" + clash.getReservationId() +
                            " (" + clash.getPickupDate() + " to " + clash.getReturnDate() + "). Choose another driver.");
        }

        r.setAssignedDriver(driver);
        // Driver Trip Accept/Reject: assignment starts as PENDING until the driver actually
        // accepts (see acceptTripAssignment() below) — the customer isn't notified at this
        // PENDING stage at all now (see the notification-spam fix just below); this status just
        // controls what MyBookings shows in the meantime ("Driver: will be assigned soon").
        r.setDriverAssignmentStatus(Reservation.DriverAssignmentStatus.PENDING);
        r.setDriverRejectionReason(null);
        Reservation saved = reservationRepository.save(r);

        String carLabel = r.getCar().getBrand() + " " + r.getCar().getModel();
        String driverName = driver.getFirstName() + " " + driver.getLastName();

        // Bug fix (notification spam): this used to also notify the CUSTOMER right here — an
        // in-app "Driver Being Confirmed" notification, an SMS, AND an email, all for a driver
        // that hasn't even agreed to take the trip yet. That's three separate messages before
        // anything is actually settled, on top of the "Booking Confirmed" message they already
        // got at payment time and the "Driver Confirmed" message still to come once a driver
        // actually accepts below. The customer doesn't need a "we're waiting" update — they need
        // exactly one message, once a driver is actually locked in, with the full details. The
        // DRIVER still needs their own "New Trip Request" notification right here, though —
        // that's the one message that's actually actionable (accept/decline), not a status
        // update, so it stays.

        // ── Notify the driver — this is a request that needs their accept/reject response ──
        String driverMsg = "New trip request! Booking #RES-" + reservationId + " — pick up "
                + r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName()
                + " on " + r.getPickupDate() + " at " + r.getPickupTime()
                + " from " + r.getPickupLocation() + ", drop at " + r.getDropLocation()
                + ". Car: " + carLabel + " (" + r.getCar().getRegistrationNumber() + "). "
                + "Please accept or decline in the app.";
        notificationService.sendSms(driver.getMobileNumber(), driverMsg);
        notificationService.notifyDriver(
                driver.getDriverId(),
                "New Trip Request 🚕",
                driverMsg,
                Notification.Type.TRIP_ASSIGNED, reservationId
        );
        notificationService.sendEmail(
                driver.getEmail(),
                "New Trip Request — Booking #RES-" + reservationId,
                "Hi " + driver.getFirstName() + ",\n\n" +
                        "You've been requested for a new pickup — please accept or decline in the app:\n\n" +
                        "Customer: " + r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName() +
                        " (" + r.getCustomer().getMobileNumber() + ")\n" +
                        "Car: " + carLabel + " (" + r.getCar().getRegistrationNumber() + ")\n" +
                        "Pickup Date: " + r.getPickupDate() + " at " + r.getPickupTime() + "\n" +
                        "Pickup Location: " + r.getPickupLocation() + "\n" +
                        "Drop Location: " + r.getDropLocation() + "\n\n" +
                        "Please check your driver dashboard for full trip details."
        );

        return mapToDTO(saved);
    }

    // New feature: Driver Trip Accept/Reject.
    @Override
    @Transactional
    public ReservationDTO acceptTripAssignment(Long reservationId, Long driverId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        assertPendingAssignmentForDriver(r, driverId);

        r.setDriverAssignmentStatus(Reservation.DriverAssignmentStatus.ACCEPTED);
        Reservation saved = reservationRepository.save(r);

        String driverName = r.getAssignedDriver().getFirstName() + " " + r.getAssignedDriver().getLastName();
        String carLabel = r.getCar().getBrand() + " " + r.getCar().getModel();
        // Bug fix: this is now the ONLY driver-related message the customer gets for the whole
        // assignment process (see assignDriver() above), so it needs to carry everything — not
        // just who's driving, but when and from where. The in-app + SMS versions used to leave
        // pickup date/time/location out entirely (only the email had it), so someone glancing at
        // the notification bell or an SMS still couldn't tell when their own car was arriving.
        String fullDetails = "Your driver for booking #RES-" + reservationId + " is confirmed: " + driverName +
                " (" + r.getAssignedDriver().getMobileNumber() + "), driving " + carLabel +
                " (" + r.getCar().getRegistrationNumber() + "). Pickup: " + r.getPickupDate() +
                " at " + r.getPickupTime() + ", from " + r.getPickupLocation() + ".";
        notificationService.notifyCustomer(
                r.getCustomer().getCustomerId(),
                "Driver Confirmed 🚗",
                fullDetails,
                Notification.Type.DRIVER_ASSIGNED, reservationId
        );
        notificationService.sendSms(r.getCustomer().getMobileNumber(), "RentMyRide: " + fullDetails);
        // Same event, second channel — email carries the full car + driver detail, SMS stays short.
        notificationService.sendEmail(
                r.getCustomer().getEmail(),
                "Driver Confirmed 🚗 — Booking #RES-" + reservationId,
                "Hi " + r.getCustomer().getFirstName() + ",\n\n" +
                        "Your driver is confirmed for booking #RES-" + reservationId + ":\n\n" +
                        "Driver: " + driverName + " (" + r.getAssignedDriver().getMobileNumber() + ")\n" +
                        "Car: " + carLabel + " (" + r.getCar().getRegistrationNumber() + ")\n" +
                        "Pickup: " + r.getPickupDate() + " at " + r.getPickupTime() + "\n" +
                        "Pickup Location: " + r.getPickupLocation() + "\n\n" +
                        "Thank you for choosing RentMyRide!"
        );

        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public ReservationDTO rejectTripAssignment(Long reservationId, Long driverId, String reason) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        assertPendingAssignmentForDriver(r, driverId);

        String driverName = r.getAssignedDriver().getFirstName() + " " + r.getAssignedDriver().getLastName();
        r.setDriverAssignmentStatus(Reservation.DriverAssignmentStatus.REJECTED);
        r.setDriverRejectionReason(reason != null && !reason.isBlank() ? reason.trim() : "No reason given");
        r.setAssignedDriver(null); // free up the reservation for admin to assign someone else
        Reservation saved = reservationRepository.save(r);

        // Admin needs to know ASAP so they can reassign before the pickup window.
        notificationService.sendEmail(
                notificationAdminEmail(),
                "⚠️ Driver Declined Trip — Booking #RES-" + reservationId,
                "Driver " + driverName + " declined booking #RES-" + reservationId + ".\n" +
                        "Reason: " + r.getDriverRejectionReason() + "\n" +
                        "Pickup: " + r.getPickupDate() + " at " + r.getPickupTime() + ", " + r.getPickupLocation() + "\n\n" +
                        "Please assign another driver as soon as possible."
        );
        log.warn("[DDT-WARN] Driver {} rejected trip assignment for reservation {}: {}", driverId, reservationId, r.getDriverRejectionReason());

        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationDTO> getPendingTripRequestsForDriver(Long driverId) {
        return reservationRepository.findPendingTripRequestsForDriver(driverId).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    private void assertPendingAssignmentForDriver(Reservation r, Long driverId) {
        if (r.getAssignedDriver() == null || !r.getAssignedDriver().getDriverId().equals(driverId))
            throw new com.rentmyride.custom_exceptions.UnauthorizedAccessException(
                    "This trip is not assigned to you.");
        if (r.getDriverAssignmentStatus() != Reservation.DriverAssignmentStatus.PENDING)
            throw new InvalidDateRangeException("This trip assignment has already been responded to.");
    }

    // Best-effort — falls back to a generic ops inbox placeholder if no admin has actually
    // registered yet (e.g. a freshly-seeded environment).
    private String notificationAdminEmail() {
        return adminRepository.findAll().stream().findFirst()
                .map(a -> a.getEmail()).orElse("ops@rentmyride.example.com");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationDTO> getPendingPickupsForDriver(Long driverId) {
        return reservationRepository.findPendingPickupsForDriver(driverId).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    // New — "you have a due amount" login popup.
    @Override
    @Transactional(readOnly = true)
    public List<ReservationDTO> getDueBalanceReservations(Long customerId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return reservationRepository.findWithDueBalanceForCustomer(customerId).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    // ── Pricing ──────────────────────────────────────────────
    // LOCAL: car's own per-day rate (set by admin per car) — customer stays within their home city/district.
    // OUTSTATION: round-trip road distance (pickup → via stops → drop → back to pickup) × car's
    //             per-km rate, plus a per-night charge (min ₹300/night) for multi-day trips.
    //             Same-day return = no night charge at all.
    /**
     * Refunds an amount owed back to the customer for reservation r — Razorpay refund to their
     * ORIGINAL payment method whenever a payment is on file, wallet credit only as a genuine
     * last resort (no linked Razorpay payment, or the Razorpay call itself fails). Used by both
     * cancelReservation() and rescheduleReservation()'s price-decrease refund, which used to
     * route straight to wallet credit unconditionally — money the customer paid by card/UPI was
     * coming back as wallet balance instead of to the account it left from, and that wallet
     * balance then had nowhere useful to go (see PaymentServiceImpl.createNewBookingOrder() for
     * the companion fix to actually being ABLE to spend it).
     */
    private String issueRefund(Reservation r, double refundAmount) {
        if (refundAmount <= 0) return null;
        if (r.getRazorpayPaymentId() != null && !r.getRazorpayPaymentId().isBlank()) {
            try {
                org.json.JSONObject refundRequest = new org.json.JSONObject();
                refundRequest.put("amount", Math.round(refundAmount * 100)); // paise
                razorpayClient.payments.refund(r.getRazorpayPaymentId(), refundRequest);
                log.info("[DDT] Razorpay refund of ₹{} initiated for reservation {} (payment {}).",
                        refundAmount, r.getReservationId(), r.getRazorpayPaymentId());
                return "Refund of ₹" + refundAmount + " initiated to your original payment method (5-7 working days).";
            } catch (Exception e) {
                log.error("[DDT-ERROR] Razorpay refund failed for reservation {} (payment {}): {} — falling back to wallet credit.",
                        r.getReservationId(), r.getRazorpayPaymentId(), e.getMessage());
                // Fall through — the customer still gets their money back, just not via their
                // original payment method this time.
            }
        } else {
            log.warn("[DDT-WARN] Reservation {} has ₹{} to refund but no linked Razorpay payment on file — crediting wallet instead.",
                    r.getReservationId(), refundAmount);
        }
        Customer customer = r.getCustomer();
        customer.setWalletBalance(Math.round(((customer.getWalletBalance() == null ? 0.0 : customer.getWalletBalance())
                + refundAmount) * 100) / 100.0);
        customerRepository.save(customer);
        return "₹" + refundAmount + " credited to your wallet.";
    }

    private PriceCalculation calculatePrice(Car car, Reservation.TripType tripType, String pickupLocation,
            String dropLocation, List<String> viaLocations, int totalDays) {

        int nights = Math.max(totalDays, 0); // 0 nights if same-day return

        if (tripType == Reservation.TripType.LOCAL) {
            // Bug fix: billing days must be an INCLUSIVE calendar-day count (pickup today, return
            // tomorrow = 2 days), matching Reservation's own totalDays field (see its @PrePersist)
            // — previously this used the raw night-diff (`totalDays`) with only a same-day floor,
            // silently undercharging every multi-day LOCAL booking by one day.
            double days = totalDays + 1;
            double ratePerDay = (car.getRentPerDay() != null && car.getRentPerDay() > 0)
                    ? car.getRentPerDay() : LOCAL_PACKAGE_RATE_PER_DAY;
            double amount = days * ratePerDay;
            return new PriceCalculation(null, 0, amount, 0.0, amount, "LOCAL_PACKAGE");
        }

        // OUTSTATION
        BiharLocations.Location pickup = BiharLocations.findByName(pickupLocation);
        BiharLocations.Location drop = BiharLocations.findByName(dropLocation);

        // Issue #44 fix: pickup/drop MUST resolve to a known location for outstation trips — this
        // used to silently fall back to flat PER_DAY pricing with no warning whenever either name
        // wasn't recognized, billing the customer under a completely different formula than the
        // distance-based one they were shown at booking time. Now it's a hard, explicit rejection
        // instead of a silent switch — the frontend only ever sends names from a fixed dropdown of
        // known locations, so hitting this in practice means bad/stale data, not a normal user path.
        if (pickup == null)
            throw new com.rentmyride.custom_exceptions.InvalidDateRangeException(
                    "Pickup location '" + pickupLocation + "' is not a recognized location.");
        if (drop == null)
            throw new com.rentmyride.custom_exceptions.InvalidDateRangeException(
                    "Drop location '" + dropLocation + "' is not a recognized location.");

        if (car.getRatePerKm() == null || car.getRatePerKm() <= 0) {
            // Legitimate fallback: the car itself has no per-km rate on file (an admin data gap,
            // not a location problem) — flat per-day pricing is the only option here, but this is
            // now at least logged so an admin can notice and fix the car's pricing.
            log.warn("[DDT-WARN] Car {} has no ratePerKm set — falling back to flat PER_DAY pricing for an outstation booking.", car.getCarId());
            double amount = Math.max(totalDays, 1) * car.getRentPerDay();
            return new PriceCalculation(null, nights, amount, 0.0, amount, "PER_DAY");
        }

        List<BiharLocations.Location> stops = new ArrayList<>();
        stops.add(pickup);
        if (viaLocations != null) {
            for (String v : viaLocations) {
                BiharLocations.Location loc = BiharLocations.findByName(v);
                // Issue #42 fix: an unrecognized via-stop name used to be silently dropped
                // (if (loc != null) stops.add(loc)) — the driver would still physically visit
                // wherever the customer meant, but the round-trip distance (and therefore the
                // fare) was computed as if that stop never existed, undercharging the customer
                // for real km driven. Now it's rejected outright instead of silently ignored.
                if (loc == null)
                    throw new com.rentmyride.custom_exceptions.InvalidDateRangeException(
                            "Stop '" + v + "' is not a recognized location.");
                // Issue #43 fix (part 1): reject a stop that's the same as the one immediately
                // before it — an accidental duplicate that would otherwise silently inflate
                // "nights" without adding any real distance/value to the trip.
                BiharLocations.Location previous = stops.get(stops.size() - 1);
                if (loc.getName().equalsIgnoreCase(previous.getName()))
                    throw new com.rentmyride.custom_exceptions.InvalidDateRangeException(
                            "Stop '" + v + "' is the same as the previous stop — please remove the duplicate.");
                stops.add(loc);
            }
        }
        stops.add(drop);

        double roundTripKm = DistanceUtil.calculateRouteDistanceKm(stops, true);

        // Issue #43 fix (part 2): route reasonableness check. A customer could otherwise submit a
        // zig-zagging stop order (e.g. Patna → Gaya → Patna-adjacent-town → Gaya-adjacent-town →
        // drop) that's technically valid but wildly inefficient compared to a sensible ordering of
        // the same stops — and it would be silently priced (and paid to the driver) at face value.
        // We don't have a full route-optimizer here, so this is a pragmatic sanity bound rather
        // than a true "shortest path" check: flag anything whose actual route is more than 2.5x
        // the direct pickup→drop distance for the number of stops involved, which a sensibly
        // ordered multi-stop trip should rarely exceed.
        double directPickupDropKm = DistanceUtil.calculateRouteDistanceKm(List.of(pickup, drop), false);
        double reasonableCeilingKm = Math.max(directPickupDropKm * 2.5, 50.0) * Math.max(1, stops.size() - 1);
        boolean routeFlaggedInefficient = roundTripKm > reasonableCeilingKm;
        if (routeFlaggedInefficient) {
            log.warn("[DDT-WARN] Route flagged as inefficient: {} stops, {} km actual vs ~{} km reasonable ceiling ({} -> {}).",
                    stops.size(), Math.round(roundTripKm), Math.round(reasonableCeilingKm), pickupLocation, dropLocation);
        }

        // Minimum billable distance: a real outstation package guarantees at least
        // (inclusive days × outstationMinKmPerDay) even on a short-route multi-day trip — the
        // car and driver are committed for the whole period regardless of actual distance. If
        // the real route comes to more than that, the customer pays for the actual km instead
        // (Math.max below), same as any real cab operator's outstation package.
        double inclusiveDays = totalDays + 1;
        double minGuaranteedKm = inclusiveDays * businessSettingsService.getSettings().getOutstationMinKmPerDay();
        double billableKm = Math.max(Math.max(roundTripKm, minGuaranteedKm), 1.0);
        double baseFare = Math.round(billableKm * car.getRatePerKm() * 100) / 100.0;

        double nightRate = (car.getNightChargePerNight() == null || car.getNightChargePerNight() < MIN_NIGHT_CHARGE)
                ? MIN_NIGHT_CHARGE : car.getNightChargePerNight();
        double nightCharges = nights * nightRate;

        double total = Math.round((baseFare + nightCharges) * 100) / 100.0;
        String pricingMethod = routeFlaggedInefficient ? "OUTSTATION_DISTANCE_ROUTE_FLAGGED" : "OUTSTATION_DISTANCE";
        return new PriceCalculation(roundTripKm, nights, baseFare, nightCharges, total, pricingMethod);
    }

    private record PriceCalculation(Double distanceKm, Integer nights, Double baseFare,
                                     Double nightCharges, Double estimatedAmount, String pricingMethod) {}

    // Sends an SMS confirmation, and an in-app notification
    private void notifyBookingConfirmed(Reservation r) {
        String carLabel = r.getCar().getBrand() + " " + r.getCar().getModel()
                + " (" + r.getCar().getRegistrationNumber() + ")";
        com.rentmyride.entities.Driver driver = r.getAssignedDriver();
        String driverLine = driver != null
                ? "Driver: " + driver.getFirstName() + " " + driver.getLastName() + " (" + driver.getMobileNumber() + ")"
                : "Driver: will be assigned soon — you'll get a separate confirmation once one is lined up.";

        notificationService.sendBookingConfirmation(
                r.getCustomer().getFirstName(),
                r.getCustomer().getMobileNumber(),
                r.getReservationId(),
                carLabel,
                r.getPickupDate().toString(),
                r.getEstimatedAmount()
        );
        // Bug fix: booking confirmation only ever went out as an SMS before — no email, and the
        // SMS itself didn't carry car/driver details. Customers now get both channels, and the
        // email includes everything they need for the trip (car + driver, when one's assigned).
        notificationService.sendEmail(
                r.getCustomer().getEmail(),
                "Booking Confirmed 🎉 — #RES-" + r.getReservationId(),
                "Hi " + r.getCustomer().getFirstName() + ",\n\n" +
                        "Your RentMyRide booking is confirmed! Here are your trip details:\n\n" +
                        "Car: " + carLabel + "\n" +
                        driverLine + "\n" +
                        "Pickup: " + r.getPickupDate() + " at " + r.getPickupTime() + "\n" +
                        "Pickup Location: " + r.getPickupLocation() + "\n" +
                        "Drop Location: " + r.getDropLocation() + "\n" +
                        "Amount Paid: ₹" + r.getAmountPaid() + " of ₹" + r.getEstimatedAmount() + "\n\n" +
                        "Thank you for choosing RentMyRide!"
        );
        notificationService.notifyCustomer(
                r.getCustomer().getCustomerId(),
                "Booking Confirmed 🎉",
                "Your booking for " + carLabel + " on " + r.getPickupDate() + " is confirmed. Amount: ₹" + r.getEstimatedAmount(),
                Notification.Type.BOOKING_CONFIRMED,
                r.getReservationId()
        );
    }

    private ReservationDTO mapToDTO(Reservation r) {
        return ReservationDTO.builder()
                .reservationId(r.getReservationId())
                .customerId(r.getCustomer().getCustomerId())
                .customerName(r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName())
                .customerMobile(r.getCustomer().getMobileNumber())
                .carId(r.getCar().getCarId())
                .carBrand(r.getCar().getBrand()).carModel(r.getCar().getModel())
                .carRegistrationNumber(r.getCar().getRegistrationNumber())
                .assignedDriverId(r.getAssignedDriver() != null ? r.getAssignedDriver().getDriverId() : null)
                .assignedDriverName(r.getAssignedDriver() != null
                        ? r.getAssignedDriver().getFirstName() + " " + r.getAssignedDriver().getLastName() : null)
                .assignedDriverMobile(r.getAssignedDriver() != null ? r.getAssignedDriver().getMobileNumber() : null)
                .driverAssignmentStatus(r.getDriverAssignmentStatus()).driverRejectionReason(r.getDriverRejectionReason())
                .pickupDate(r.getPickupDate()).pickupTime(r.getPickupTime()).returnDate(r.getReturnDate())
                .totalDays(r.getTotalDays()).tripType(r.getTripType())
                .pickupLocation(r.getPickupLocation()).dropLocation(r.getDropLocation())
                .viaLocations(r.getViaLocations())
                .distanceKm(r.getDistanceKm()).nights(r.getNights())
                .baseFare(r.getBaseFare()).nightCharges(r.getNightCharges())
                .promoCode(r.getPromoCode()).discountAmount(r.getDiscountAmount())
                .walletCreditsUsed(r.getWalletCreditsUsed())
                .estimatedAmount(r.getEstimatedAmount())
                .reservationStatus(r.getReservationStatus())
                .paymentStatus(r.getPaymentStatus()).paymentType(r.getPaymentType())
                .amountPaid(r.getAmountPaid())
                .balanceDue(Math.max(0.0, r.getEstimatedAmount() - r.getAmountPaid()))
                .cancellationFee(r.getCancellationFee()).refundAmount(r.getRefundAmount())
                .specialRequests(r.getSpecialRequests()).createdAt(r.getCreatedAt())
                .tripMode(r.getTripMode()).selfDriveDeposit(r.getSelfDriveDeposit())
                .build();
    }
}
