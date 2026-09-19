package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.PaymentFailedException;
import com.rentmyride.custom_exceptions.PaymentNotFoundException;
import com.rentmyride.custom_exceptions.RentalNotFoundException;
import com.rentmyride.custom_exceptions.ReservationNotFoundException;
import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.dtos.PaymentDTO;
import com.rentmyride.dtos.ReservationDTO;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Payment;
import com.rentmyride.entities.Rental;
import com.rentmyride.entities.Reservation;
import com.rentmyride.repository.PaymentRepository;
import com.rentmyride.repository.RentalRepository;
import com.rentmyride.repository.ReservationRepository;
import com.rentmyride.service.PaymentService;
import com.rentmyride.service.ReservationService;
import com.rentmyride.util.RazorpaySignatureUtil;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RentalRepository rentalRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final com.rentmyride.repository.CustomerRepository customerRepository;
    private final com.rentmyride.repository.CarRepository carRepository;
    private final com.rentmyride.service.BusinessSettingsService businessSettingsService;
    private final com.rentmyride.service.EngagementService engagementService;
    private final RazorpayClient razorpayClient;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    @Override
    @Transactional
    public PaymentDTO.RazorpayOrderResponse createRazorpayOrder(PaymentDTO.InitiateRequest req) {
        Rental rental = rentalRepository.findById(req.getRentalId())
                .orElseThrow(() -> new RentalNotFoundException(req.getRentalId()));

        // Bug fix: this used to charge the customer baseAmount + 18% GST on top via Razorpay
        // (and store that inflated total on the Payment row) — but rental.getTotalAmount() is
        // already the final, all-inclusive amount everywhere else in the app (see Invoice.java
        // and ReservationServiceImpl for the same rule). Charging extra tax on top here would
        // have overcharged a customer by 18% for anyone this manual/walk-in-collection endpoint
        // was ever actually used on. Treat totalAmount as the real (GST-inclusive) charge, and
        // only back out CGST/SGST as a record-keeping breakdown of it — same pattern as
        // Invoice.prePersist().
        double totalAmount = rental.getTotalAmount();
        double gstPercentage = 18.0;
        double gstAmount = totalAmount - (totalAmount / (1 + gstPercentage / 100));
        double baseAmount = totalAmount - gstAmount;

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int)(totalAmount * 100)); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "DDT-" + rental.getRentalId());

            Order order = razorpayClient.orders.create(orderRequest);

            // Save pending payment
            Payment payment = Payment.builder()
                    .rental(rental)
                    .customer(rental.getCustomer())
                    .razorpayOrderId(order.get("id"))
                    .baseAmount(baseAmount)
                    .gstPercentage(gstPercentage)
                    .gstAmount(gstAmount)
                    .totalAmount(totalAmount)
                    .paymentMethod(req.getPaymentMethod())
                    .build();
            paymentRepository.save(payment);

            return PaymentDTO.RazorpayOrderResponse.builder()
                    .orderId(order.get("id"))
                    .amount(totalAmount)
                    .currency("INR")
                    .keyId(razorpayKeyId)
                    .customerName(rental.getCustomer().getFirstName() + " " + rental.getCustomer().getLastName())
                    .customerEmail(rental.getCustomer().getEmail())
                    .customerContact(rental.getCustomer().getMobileNumber())
                    .build();

        } catch (RazorpayException e) {
            log.error("[DDT] Razorpay order creation failed: {}", e.getMessage());
            throw new PaymentFailedException("Could not create Razorpay order: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentDTO verifyAndSavePayment(PaymentDTO.VerifyRequest req) {
        Payment payment = paymentRepository.findByRazorpayOrderId(req.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + req.getRazorpayOrderId()));

        // Issue #17 fix: this endpoint used to mark the payment SUCCESS purely because the
        // frontend's checkout callback fired — the same signature check that
        // verifyReservationPayment() below already does was simply missing here. A malicious
        // client could call this endpoint directly with a real orderId (visible in the page) and
        // a made-up paymentId/signature and get a rental marked as paid without ever paying.
        boolean valid = RazorpaySignatureUtil.verify(req.getRazorpayOrderId(), req.getRazorpayPaymentId(),
                req.getRazorpaySignature(), razorpayKeySecret);
        if (!valid) {
            payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new PaymentFailedException("Payment signature verification failed.");
        }

        // Issue #46 fix: enforce "at most one SUCCESSFUL payment per rental" at the point a
        // payment is ABOUT to become SUCCESS — the real invariant, without blocking legitimate
        // FAILED/retry rows from existing for the same rental_id.
        if (paymentRepository.countByRental_RentalIdAndPaymentStatus(payment.getRental().getRentalId(),
                Payment.PaymentStatus.SUCCESS) > 0) {
            throw new PaymentFailedException("This rental has already been paid for — duplicate payment blocked.");
        }

        payment.setRazorpayPaymentId(req.getRazorpayPaymentId());
        payment.setRazorpaySignature(req.getRazorpaySignature());
        payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
        payment.setPaymentDatetime(LocalDateTime.now());
        return mapToDTO(paymentRepository.save(payment));
    }

    // ── Booking-confirmation payment (full or ₹1000+ deposit) via Razorpay, scoped to a Reservation ──
    @Override
    @Transactional(readOnly = true)
    public PaymentDTO.RazorpayOrderResponse createReservationOrder(PaymentDTO.ReservationOrderRequest req) {
        Reservation reservation = reservationRepository.findById(req.getReservationId())
                .orElseThrow(() -> new ReservationNotFoundException(req.getReservationId()));

        double remaining = reservation.getEstimatedAmount() - reservation.getAmountPaid();
        if (remaining <= 0)
            throw new PaymentFailedException("This reservation is already fully paid.");

        double amount;
        if (req.getPaymentType() == Reservation.PaymentType.FULL) {
            amount = remaining;
        } else {
            amount = req.getAmount() == null ? 0 : req.getAmount();
            if (amount < 1000)
                throw new PaymentFailedException("Minimum deposit to confirm a booking is ₹1000.");
            if (amount > remaining) amount = remaining;
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) Math.round(amount * 100)); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "RES-" + reservation.getReservationId());

            Order order = razorpayClient.orders.create(orderRequest);

            return PaymentDTO.RazorpayOrderResponse.builder()
                    .orderId(order.get("id"))
                    .amount(amount)
                    .currency("INR")
                    .keyId(razorpayKeyId)
                    .customerName(reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName())
                    .customerEmail(reservation.getCustomer().getEmail())
                    .customerContact(reservation.getCustomer().getMobileNumber())
                    .build();
        } catch (RazorpayException e) {
            log.error("[DDT] Razorpay order creation failed for reservation {}: {}", req.getReservationId(), e.getMessage());
            throw new PaymentFailedException("Could not create Razorpay order: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReservationDTO verifyReservationPayment(PaymentDTO.ReservationVerifyRequest req) {
        boolean valid = RazorpaySignatureUtil.verify(req.getRazorpayOrderId(), req.getRazorpayPaymentId(),
                req.getRazorpaySignature(), razorpayKeySecret);
        if (!valid)
            throw new PaymentFailedException("Payment signature verification failed.");

        ReservationDTO.PayRequest payRequest = new ReservationDTO.PayRequest(
                req.getPaymentType(), req.getAmount(), "RAZORPAY", req.getRazorpayPaymentId());
        return reservationService.payForReservation(req.getReservationId(), payRequest);
    }

    // New feature: "create only after payment" — order creation for a booking that doesn't exist
    // in the database yet. Computes the price SERVER-SIDE (via the same estimatePrice() the live
    // booking-form estimate uses) rather than trusting a client-supplied amount, so a tampered
    // request can't get a Razorpay order for less than the real price.
    @Override
    @Transactional(readOnly = true)
    public PaymentDTO.RazorpayOrderResponse createNewBookingOrder(Long customerId, PaymentDTO.NewBookingOrderRequest req) {
        var booking = req.getBooking();

        // Bug fix: this used to only check car availability AFTER Razorpay had already charged
        // the customer (inside verifyNewBookingPayment → createReservationWithPayment). That
        // meant a customer could select an already-booked car/date, go all the way through
        // paying, and only THEN be told the booking failed — money already taken by Razorpay,
        // requiring a manual refund, with nothing to show for it but frustration. Checking here,
        // before the Razorpay order is even created, blocks the customer at the "Pay" step
        // instead — same message either way, just far earlier.
        var car = carRepository.findById(booking.getCarId())
                .orElseThrow(() -> new com.rentmyride.custom_exceptions.CarNotFoundException(booking.getCarId()));
        if (car.getAvailabilityStatus() != com.rentmyride.entities.Car.AvailabilityStatus.AVAILABLE)
            throw new com.rentmyride.custom_exceptions.CarNotAvailableException(booking.getCarId());
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                booking.getCarId(), booking.getPickupDate(), booking.getReturnDate());
        if (!conflicts.isEmpty())
            throw new com.rentmyride.custom_exceptions.CarNotAvailableException(
                    "This car is already booked for the selected dates. Please change the date or choose another car.");

        var estimateReq = new ReservationDTO.EstimateRequest(
                booking.getCarId(), booking.getPickupDate(), booking.getReturnDate(), booking.getTripType(),
                booking.getPickupLocation(), booking.getDropLocation(), booking.getViaLocations(), booking.getPromoCode(),
                booking.getTripMode());
        // Promo, loyalty tier AND the self-drive deposit are all resolved inside estimatePrice()
        // now, so none of that math is repeated here. That duplication was exactly why the
        // booking page and the Razorpay checkout showed two different totals: the page rendered
        // a promo-only figure while this method quietly took another 5-10% off for the customer's
        // loyalty tier. One calculation, one number, both ends agree.
        var estimate = reservationService.estimatePrice(estimateReq, customerId);

        double discountedEstimate = estimate.getPayableAmount();
        if (booking.isUseWalletCredits() && discountedEstimate > 0) {
            // Wallet is only READ here — the real deduction happens once payment is verified and
            // the reservation is actually created. This is still just a quote.
            double walletBalance = customerRepository.findById(customerId)
                    .map(c -> c.getWalletBalance() == null ? 0.0 : c.getWalletBalance())
                    .orElse(0.0);
            double walletUsable = Math.min(walletBalance, discountedEstimate);
            discountedEstimate = Math.max(0, discountedEstimate - walletUsable);
        }

        double remaining = discountedEstimate;

        // Bug fix ("Order amount less than minimum amount allowed" / wallet balance stuck unused):
        // when the wallet covers the ENTIRE trip cost, `remaining` lands on ₹0 (or a few paise
        // under ₹1 for an odd balance) — but Razorpay's own minimum order amount is ₹1, so
        // creating an order for that landed here as a hard Razorpay rejection every time, and the
        // customer's own wallet balance became something they could see but never actually
        // spend. There is nothing for Razorpay to do in this case at all, so skip it entirely —
        // create the (already fully paid) reservation directly instead of an order that was
        // never going to succeed. Checked BEFORE the FULL/DEPOSIT branching below, since a
        // customer choosing "Pay Deposit" here would otherwise have their deposit amount clipped
        // down to the same near-₹0 remaining and then get rejected by the minimum-DEPOSIT check
        // instead — equally wrong, since the honest answer is "nothing left to pay at all".
        if (booking.isUseWalletCredits() && remaining < 1.0) {
            var created = reservationService.createReservationWithPayment(
                    customerId, booking, req.getPaymentType(), 0.0, null);
            return PaymentDTO.RazorpayOrderResponse.builder()
                    .walletOnly(true)
                    .reservation(created)
                    .amount(0.0)
                    .currency("INR")
                    .build();
        }

        double amount;
        if (req.getPaymentType() == Reservation.PaymentType.FULL) {
            amount = remaining;
        } else {
            amount = req.getAmount() == null ? 0 : req.getAmount();
            // Consistency fix: use the SAME configurable minimum deposit that payForReservation()
            // enforces later — this used to be hardcoded to ₹1000 here regardless of what an
            // admin actually configured in Business Settings.
            double minDeposit = businessSettingsService.getSettings().getMinDepositAmount();
            if (amount < minDeposit)
                throw new PaymentFailedException("Minimum deposit to confirm a booking is ₹" + (int) minDeposit + ".");
            if (amount > remaining) amount = remaining;
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) Math.round(amount * 100)); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "NEWBOOK-" + customerId + "-" + System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);

            return PaymentDTO.RazorpayOrderResponse.builder()
                    .orderId(order.get("id"))
                    .amount(amount)
                    .currency("INR")
                    .keyId(razorpayKeyId)
                    .customerName(customer.getFirstName() + " " + customer.getLastName())
                    .customerEmail(customer.getEmail())
                    .customerContact(customer.getMobileNumber())
                    .build();
        } catch (RazorpayException e) {
            log.error("[DDT] Razorpay order creation failed for new booking (customer {}): {}", customerId, e.getMessage());
            throw new PaymentFailedException("Could not create Razorpay order: " + e.getMessage());
        } catch (Exception e) {
            // Bug fix: ONLY RazorpayException was being caught here — any other failure inside
            // this try block (a malformed request body, a network hiccup surfacing as a
            // different exception type, an unexpected null somewhere) fell straight through to
            // the generic "Something went wrong on our end, reference ERR-XXXX" handler, with no
            // way to tell from the customer's screen whether it was a Razorpay problem, a
            // wallet-calculation problem, or something else entirely. Catching broadly here and
            // re-throwing as the same PaymentFailedException at least keeps the error specific
            // to "payment/order creation failed" and puts the real cause in the backend log
            // right next to the Razorpay-specific failures above, instead of in the opaque
            // GlobalExceptionHandler catch-all.
            log.error("[DDT-ERROR] Unexpected failure creating new-booking order (customer {}): {}", customerId, e.getMessage(), e);
            throw new PaymentFailedException("Could not create the payment order: " + e.getMessage());
        }
    }

    // Verifies the Razorpay signature FIRST — only once that's confirmed does the reservation
    // ever get created (and it's created already CONFIRMED+paid, see
    // ReservationServiceImpl.createReservationWithPayment). If verification fails, or the car
    // turns out to have been booked by someone else in the meantime, NOTHING is ever written to
    // the reservation table — exactly the "no booking until payment succeeds" behavior requested.
    @Override
    @Transactional
    public ReservationDTO verifyNewBookingPayment(Long customerId, PaymentDTO.NewBookingVerifyRequest req) {
        boolean valid = RazorpaySignatureUtil.verify(req.getRazorpayOrderId(), req.getRazorpayPaymentId(),
                req.getRazorpaySignature(), razorpayKeySecret);
        if (!valid)
            throw new PaymentFailedException("Payment signature verification failed.");

        try {
            return reservationService.createReservationWithPayment(
                    customerId, req.getBooking(), req.getPaymentType(), req.getAmount(), req.getRazorpayPaymentId());
        } catch (RuntimeException e) {
            // Rare race: the car got booked by someone else between order-creation and this
            // payment landing. Money was already captured by Razorpay, so refund it immediately
            // rather than leaving the customer charged with nothing to show for it.
            log.error("[DDT-ERROR] Booking creation failed AFTER successful payment (customer {}, payment {}): {} — issuing refund.",
                    customerId, req.getRazorpayPaymentId(), e.getMessage());
            try {
                JSONObject refundRequest = new JSONObject();
                razorpayClient.payments.refund(req.getRazorpayPaymentId(), refundRequest); // full refund
            } catch (Exception refundError) {
                log.error("[DDT-ERROR] Automatic refund ALSO failed for payment {}: {} — needs manual admin action.",
                        req.getRazorpayPaymentId(), refundError.getMessage());
            }
            throw new PaymentFailedException(
                    "This car was just booked by someone else for those dates. Your payment is being refunded — please try a different car or dates.");
        }
    }

    // Issue #18 fix: previously payment confirmation relied ENTIRELY on the customer's browser
    // calling /verify after checkout — if the tab was closed, the network dropped, or someone
    // simply never called that endpoint, Razorpay could have captured the money while our system
    // still shows the payment as PENDING forever. A webhook is Razorpay calling US, directly,
    // server-to-server, so it doesn't depend on the customer's browser at all.
    //
    // NOTE: this reconciles the rental-Payment flow (createRazorpayOrder/verifyAndSavePayment),
    // which persists a Payment row keyed by razorpayOrderId that this can look up and correct.
    // The newer reservation-booking flow (createReservationOrder/verifyReservationPayment) does
    // NOT currently persist an order-level record before checkout, so there's nothing yet for a
    // webhook to reconcile against for that path — worth persisting a pending record there too
    // in a follow-up so webhook coverage is complete across both flows.
    @Override
    @Transactional
    public void handleWebhookEvent(String rawPayload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("[DDT-ERROR] Razorpay webhook received but razorpay.webhook.secret is not configured — rejecting.");
            throw new PaymentFailedException("Webhook not configured.");
        }
        if (!verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("[DDT-SECURITY] Razorpay webhook signature mismatch — possible spoofed request.");
            throw new PaymentFailedException("Invalid webhook signature.");
        }

        JSONObject event = new JSONObject(rawPayload);
        String eventType = event.optString("event", "");

        JSONObject paymentEntity = event.optJSONObject("payload") != null
                && event.getJSONObject("payload").optJSONObject("payment") != null
                ? event.getJSONObject("payload").getJSONObject("payment").optJSONObject("entity")
                : null;
        if (paymentEntity == null) {
            log.info("[DDT] Ignoring webhook event with no payment entity: {}", eventType);
            return;
        }

        String orderId = paymentEntity.optString("order_id", null);
        String paymentId = paymentEntity.optString("id", null);
        if (orderId == null) return;

        Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
        if (payment == null) {
            // Most likely a reservation-flow order (see note above) — nothing to reconcile yet.
            log.info("[DDT] Webhook for order {} has no matching Payment record (event={}); skipping.", orderId, eventType);
            return;
        }

        switch (eventType) {
            case "payment.captured" -> {
                if (payment.getPaymentStatus() != Payment.PaymentStatus.SUCCESS) {
                    // Issue #46 fix: same duplicate-payment guard as verifyAndSavePayment() — a
                    // webhook retry (Razorpay resends webhooks until it gets a 200) landing after
                    // this rental was already separately marked paid shouldn't create a second
                    // SUCCESS record.
                    boolean alreadyPaid = payment.getRental() != null && paymentRepository
                            .countByRental_RentalIdAndPaymentStatus(payment.getRental().getRentalId(), Payment.PaymentStatus.SUCCESS) > 0;
                    if (alreadyPaid) {
                        log.info("[DDT] Webhook for order {} ignored — rental {} already has a successful payment.", orderId, payment.getRental().getRentalId());
                    } else {
                        payment.setRazorpayPaymentId(paymentId);
                        payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
                        payment.setPaymentDatetime(LocalDateTime.now());
                        paymentRepository.save(payment);
                        log.info("[DDT] Webhook reconciled payment {} for order {} as SUCCESS.", payment.getPaymentId(), orderId);
                    }
                }
            }
            case "payment.failed" -> {
                if (payment.getPaymentStatus() == Payment.PaymentStatus.PENDING) {
                    payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("[DDT] Webhook reconciled payment {} for order {} as FAILED.", payment.getPaymentId(), orderId);
                }
            }
            default -> log.info("[DDT] Ignoring unhandled webhook event type: {}", eventType);
        }
    }

    private boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawPayload.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().equals(signatureHeader);
        } catch (Exception e) {
            log.error("[DDT-ERROR] Webhook signature check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override @Transactional(readOnly = true)
    public PaymentDTO getPaymentById(Long id) {
        // IDOR fix: without this, any logged-in customer could view anyone's payment/transaction
        // details by guessing the paymentId.
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(payment.getCustomer().getCustomerId());
        return mapToDTO(payment);
    }
    @Override @Transactional(readOnly = true)
    public PaymentDTO getPaymentByRentalId(Long rentalId) {
        // Bug fix: prefer the SUCCESSFUL payment if one exists (the one that actually matters to
        // whoever's asking); otherwise fall back to the most recent attempt. Needed now that a
        // rental can have more than one Payment row (see PaymentRepository note).
        List<Payment> payments = paymentRepository.findByRental_RentalId(rentalId);
        Payment payment = payments.stream()
                .filter(p -> p.getPaymentStatus() == Payment.PaymentStatus.SUCCESS)
                .findFirst()
                .or(() -> payments.stream().max(java.util.Comparator.comparing(Payment::getCreatedAt)))
                .orElseThrow(() -> new PaymentNotFoundException("No payment for rental: " + rentalId));
        Long ownerDriverId = payment.getRental().getDriver() != null ? payment.getRental().getDriver().getDriverId() : null;
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomerOrDriver(payment.getCustomer().getCustomerId(), ownerDriverId);
        return mapToDTO(payment);
    }
    @Override @Transactional(readOnly = true)
    public List<PaymentDTO> getAllPayments() {
        // Issue #24 fix — see PaymentRepository.findAllWithDetails().
        return paymentRepository.findAllWithDetails().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated counterpart.
    @Override @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<PaymentDTO> getAllPaymentsPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        var result = paymentRepository.findAllWithDetails(pageable).map(this::mapToDTO);
        return com.rentmyride.dtos.PageResponse.from(result);
    }
    @Override @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByCustomer(Long customerId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return paymentRepository.findByCustomer_CustomerId(customerId).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByStatus(Payment.PaymentStatus status) {
        return paymentRepository.findByPaymentStatus(status).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    @Override @Transactional(readOnly = true)
    public Double getTotalCollected() { return paymentRepository.getTotalCollected(); }
    @Override @Transactional(readOnly = true)
    public Double getMonthlyCollection(int month, int year) { return paymentRepository.getMonthlyCollection(month, year); }

    private PaymentDTO mapToDTO(Payment p) {
        return PaymentDTO.builder()
                .paymentId(p.getPaymentId())
                .rentalId(p.getRental() != null ? p.getRental().getRentalId() : null)
                .reservationId(p.getReservation() != null ? p.getReservation().getReservationId() : null)
                .customerId(p.getCustomer().getCustomerId())
                .customerName(p.getCustomer().getFirstName() + " " + p.getCustomer().getLastName())
                .razorpayOrderId(p.getRazorpayOrderId())
                .razorpayPaymentId(p.getRazorpayPaymentId())
                .baseAmount(p.getBaseAmount()).gstPercentage(p.getGstPercentage())
                .gstAmount(p.getGstAmount()).totalAmount(p.getTotalAmount())
                .paymentMethod(p.getPaymentMethod()).paymentStatus(p.getPaymentStatus())
                .paymentDatetime(p.getPaymentDatetime())
                .build();
    }
}
