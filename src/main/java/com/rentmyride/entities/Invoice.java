package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    @OneToOne(fetch = FetchType.LAZY)
    // Bug fix: unlike Payment (intentionally allows multiple rows per rental — deposit + retry
    // attempts), an Invoice genuinely should be exactly ONE per rental. Without this DB-level
    // constraint, a double-submitted "complete rental" request (see the new status guard in
    // RentalServiceImpl.completeRental()) could have silently created two invoice rows for the
    // same trip — this makes that impossible even as a defense-in-depth backstop.
    @JoinColumn(name = "rental_id", nullable = false, unique = true)
    private Rental rental;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    // Billing breakdown
    @Column(name = "rent_per_day", nullable = false)
    private Double rentPerDay;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "base_rent_amount", nullable = false)
    private Double baseRentAmount;

    @Column(name = "extra_km_charges")
    private Double extraKmCharges = 0.0;

    @Column(name = "damage_charges")
    private Double damageCharges = 0.0;

    @Column(name = "late_return_charges")
    private Double lateReturnCharges = 0.0;

    @Column(name = "discount_amount")
    @Builder.Default
    private Double discountAmount = 0.0;

    @Column(name = "subtotal", nullable = false)
    private Double subtotal;

    // Bug fix: Lombok's @Builder does NOT apply field-initializer defaults (9.0/9.0/18.0) —
    // Invoice.builder()...build() (used by InvoiceServiceImpl.generateInvoice()) never set these
    // three explicitly, so they came out as null on every built Invoice, and @PrePersist's
    // `subtotal * cgstPercentage` NPE'd unboxing a null Double. That NPE happened INSIDE the same
    // transaction as completeRental() (invoice auto-generation runs in it), so Spring marked the
    // whole transaction rollback-only — the rental never actually completed even though the
    // driver's drop-off screen showed no obvious reason why. @Builder.Default makes the
    // initializer actually apply when no value is passed to the builder.
    @Builder.Default
    @Column(name = "cgst_percentage")
    private Double cgstPercentage = 9.0;   // 9% CGST

    @Builder.Default
    @Column(name = "sgst_percentage")
    private Double sgstPercentage = 9.0;   // 9% SGST

    @Builder.Default
    @Column(name = "total_gst_percentage")
    private Double totalGstPercentage = 18.0; // 18% total GST

    @Column(name = "cgst_amount", nullable = false)
    private Double cgstAmount;

    @Column(name = "sgst_amount", nullable = false)
    private Double sgstAmount;

    @Column(name = "total_gst_amount", nullable = false)
    private Double totalGstAmount;

    @Column(name = "grand_total", nullable = false)
    private Double grandTotal;

    // New — how much of grandTotal is still unpaid. Surfaced to admin (Payments/Invoices pages)
    // so an unpaid/partially-paid rental is actually visible instead of silently invisible.
    @Builder.Default
    @Column(name = "due_amount")
    private Double dueAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_status", nullable = false)
    private InvoiceStatus invoiceStatus;

    @Column(name = "invoice_date", nullable = false)
    private LocalDateTime invoiceDate;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "notes", length = 500)
    private String notes;

    // ── Self-drive only: refundable security deposit, settled at return ──
    // Populated by InvoiceServiceImpl.generateInvoice() from the Rental's snapshotted deposit
    // fields (see SelfDriveHandoverServiceImpl.confirmReturn()). Null/0 for a chauffeur-driven
    // rental — nothing here changes GST math, since the deposit was never part of grandTotal.
    @Column(name = "deposit_held")
    private Double depositHeld;

    @Column(name = "deposit_refunded")
    private Double depositRefunded;

    @Column(name = "deposit_adjusted")
    private Double depositAdjusted;   // deductions that exceeded the deposit, billed separately

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.invoiceDate = LocalDateTime.now();
        // Only defaults to GENERATED if the caller hasn't already set a real status (see
        // InvoiceServiceImpl.generateInvoice(), which sets PAID/UNPAID based on how much the
        // customer actually paid — previously this was hardcoded to GENERATED and NEVER changed
        // afterwards, so admin's "Total Revenue" (which only counts PAID invoices) was always ₹0.
        if (this.invoiceStatus == null) this.invoiceStatus = InvoiceStatus.GENERATED;

        // GST Calculation — null-safe: every numeric input here defaults to 0 (or the field's own
        // intended default for the GST percentages) if somehow still unset, so a missing/optional
        // value can never NPE and silently roll back whatever operation triggered invoice
        // generation (e.g. a driver completing a drop-off — see the @Builder.Default fix above
        // for why cgstPercentage/sgstPercentage could be null in the first place).
        // Bug fix: this used to ADD 18% GST on top of baseRent+extraKm+damage+lateReturn-discount
        // to get grandTotal. But the customer is never actually charged that extra 18% anywhere
        // — the price the Admin sets on a car (car.rentPerDay) IS the final amount the customer
        // pays at booking (see ReservationServiceImpl's pricing — no GST is added there at all).
        // The result: grandTotal here always came out ~18% HIGHER than reservation.amountPaid,
        // so due = grandTotal - amountPaid was never ≤ 0 — EVERY invoice showed "UNPAID" no
        // matter how much the customer actually paid, even a customer who paid in full.
        //
        // Fix: treat the amount actually charged as GST-INCLUSIVE (matching how it's charged),
        // and back out CGST/SGST as a breakdown WITHIN that total instead of adding to it.
        // grandTotal now always equals what was actually billed, so a fully-paid rental
        // correctly comes out PAID, and the invoice PDF/UI still shows a proper CGST+SGST
        // breakdown — just as a component of the price, not an addition to it.
        double baseRent = baseRentAmount != null ? baseRentAmount : 0.0;
        double extraKm = extraKmCharges != null ? extraKmCharges : 0.0;
        double damage = damageCharges != null ? damageCharges : 0.0;
        double lateReturn = lateReturnCharges != null ? lateReturnCharges : 0.0;
        double discount = discountAmount != null ? discountAmount : 0.0;
        double cgstPct = cgstPercentage != null ? cgstPercentage : 9.0;
        double sgstPct = sgstPercentage != null ? sgstPercentage : 9.0;
        double totalGstPct = cgstPct + sgstPct;

        double billedAmount = Math.max(0.0, baseRent + extraKm + damage + lateReturn - discount);
        this.grandTotal = Math.round(billedAmount * 100) / 100.0;

        double taxableValue = totalGstPct > 0 ? billedAmount / (1 + totalGstPct / 100.0) : billedAmount;
        this.subtotal = Math.round(taxableValue * 100) / 100.0;
        this.cgstAmount = Math.round((taxableValue * cgstPct / 100.0) * 100) / 100.0;
        this.sgstAmount = Math.round((taxableValue * sgstPct / 100.0) * 100) / 100.0;
        this.totalGstAmount = Math.round((cgstAmount + sgstAmount) * 100) / 100.0;

        // Auto Invoice Number: DDT-YYYYMMDD-ID
        this.invoiceNumber = "DDT-" + java.time.LocalDate.now()
                .toString().replace("-", "") + "-" + (int)(Math.random() * 90000 + 10000);
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Enum
    public enum InvoiceStatus {
        GENERATED, PAID, UNPAID, CANCELLED, REFUNDED
    }
}
