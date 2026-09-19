package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    // Nullable now (was NOT NULL) — a Payment made at BOOKING time (via the reservation-payment
    // flow) happens before any Rental exists at all (a Rental is only created later, at actual
    // pickup). Previously this NOT NULL constraint meant such payments could never be recorded
    // here, so they never showed up in admin's Payments page or in total-revenue calculations.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id")
    private Rental rental;

    // New — the reservation this payment was made against, when it's a booking-time payment
    // (deposit or full payment) rather than a post-rental-completion payment.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "razorpay_order_id", unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @Column(name = "base_amount", nullable = false)
    private Double baseAmount;

    @Column(name = "gst_percentage", nullable = false)
    private Double gstPercentage = 18.0;

    @Column(name = "gst_amount", nullable = false)
    private Double gstAmount;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "payment_datetime")
    private LocalDateTime paymentDatetime;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Bug fix: this used to unconditionally set paymentStatus = PENDING and recompute
        // gstPercentage/gstAmount/totalAmount from scratch on EVERY insert — clobbering whatever
        // the caller had already explicitly built the Payment with. ReservationServiceImpl.
        // payForReservation() (and the new-booking equivalent) builds a Payment with
        // paymentStatus(SUCCESS), gstPercentage(0.0), gstAmount(0.0), totalAmount(amount) —
        // correctly recording that the customer paid exactly `amount`, no GST added on top
        // (car.rentPerDay is already the final price — see Invoice.java for the same fix on
        // the invoicing side). This method threw all of that away right before the INSERT:
        // every payment came out of the DB as PENDING with an 18%-inflated totalAmount,
        // regardless of what was actually charged or whether it had actually succeeded.
        // Only the legacy createRazorpayOrder() flow relies on defaults being applied (it
        // deliberately leaves paymentStatus/gstPercentage/gstAmount/totalAmount unset at
        // order-creation time, to be filled in here) — so defaults still apply, but ONLY when
        // the caller hasn't already set a real value.
        if (this.paymentStatus == null) this.paymentStatus = PaymentStatus.PENDING;
        if (this.gstPercentage == null) this.gstPercentage = 18.0;
        if (this.gstAmount == null) this.gstAmount = (this.baseAmount * this.gstPercentage) / 100;
        if (this.totalAmount == null) this.totalAmount = this.baseAmount + this.gstAmount;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum PaymentMethod {
        UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING, CASH, WALLET
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED
    }
}
