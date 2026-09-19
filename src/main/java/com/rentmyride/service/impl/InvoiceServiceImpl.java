package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.InvoiceNotFoundException;
import com.rentmyride.custom_exceptions.RentalNotFoundException;
import com.rentmyride.dtos.InvoiceDTO;
import com.rentmyride.entities.Invoice;
import com.rentmyride.entities.Rental;
import com.rentmyride.repository.InvoiceRepository;
import com.rentmyride.repository.RentalRepository;
import com.rentmyride.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final RentalRepository rentalRepository;

    @Override
    // Bug fix: this used to be plain @Transactional (default propagation = REQUIRED), meaning it
    // just joined whichever transaction was already active — when called from inside
    // RentalServiceImpl.completeRental() (auto-invoice-generation), any failure in HERE marked
    // the ENTIRE outer transaction rollback-only, silently undoing the driver's drop-off itself
    // even though that part had nothing wrong with it. REQUIRES_NEW makes this run as its own
    // independent transaction: if it fails, only the invoice-generation attempt rolls back — the
    // rental completion the driver actually cares about is unaffected either way.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public InvoiceDTO generateInvoice(Long rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));

        // Idempotency guard — if an invoice already exists for this rental (e.g. this got called
        // twice for the same completion, or an admin manually retriggers generation), return the
        // existing one instead of trying to insert a second row and hitting the new unique
        // constraint on Invoice.rental_id.
        var existing = invoiceRepository.findByRental_RentalId(rentalId);
        if (existing.isPresent()) return mapToDTO(existing.get());

        // Minor fix: a same-day (pickup date == return date) booking computed 0 here, so the
        // invoice line item showed "Rental (0 day(s) @ ₹.../day)" even though a real night/day
        // was charged. A rental is always at least 1 day for billing/display purposes.
        int days = Math.max(1, (int)(rental.getReservation().getReturnDate().toEpochDay()
                - rental.getReservation().getPickupDate().toEpochDay()));

        double extraKm     = rental.getExtraKmCharges()    != null ? rental.getExtraKmCharges()    : 0.0;
        double damage      = rental.getDamageCharges()      != null ? rental.getDamageCharges()      : 0.0;
        double lateCharges = rental.getLateReturnCharges()  != null ? rental.getLateReturnCharges()  : 0.0;
        double discount    = rental.getDiscountAmount()     != null ? rental.getDiscountAmount()     : 0.0;

        // Self-drive: the deposit itself was already excluded from rental.baseAmount (see
        // SelfDriveHandoverServiceImpl.confirmPickup()), so it needs no GST treatment — it's
        // carried on the invoice purely as an informational settlement line. Null for a
        // chauffeur-driven rental (rental.getDepositHeld() is never set on that path).
        Double depositHeld     = rental.getDepositHeld();
        Double depositRefunded = rental.getDepositRefunded();
        Double depositAdjusted = (depositHeld != null && rental.getDepositDeductions() != null)
                ? Math.max(0.0, Math.round((rental.getDepositDeductions() - depositHeld) * 100) / 100.0)
                : null;

        Invoice invoice = Invoice.builder()
                .rental(rental)
                .customer(rental.getCustomer())
                .car(rental.getCar())
                .rentPerDay(rental.getCar().getRentPerDay())
                .totalDays(days)
                .baseRentAmount(rental.getBaseAmount())
                .extraKmCharges(extraKm)
                .damageCharges(damage)
                .lateReturnCharges(lateCharges)
                .discountAmount(discount)
                .depositHeld(depositHeld)
                .depositRefunded(depositRefunded)
                .depositAdjusted(depositAdjusted)
                .build();

        Invoice saved = invoiceRepository.save(invoice);

        // Compare what was actually paid (at booking time, deposit or full) against the FINAL
        // bill (which can be higher — extra km, damage, late fees only known after the trip
        // ends). Previously invoiceStatus was hardcoded and never reflected this at all — admin
        // had no way to see which completed rentals still had money outstanding.
        double amountPaid = rental.getReservation().getAmountPaid() != null ? rental.getReservation().getAmountPaid() : 0.0;
        double due = Math.max(0.0, saved.getGrandTotal() - amountPaid);
        saved.setDueAmount(Math.round(due * 100) / 100.0);
        saved.setInvoiceStatus(due <= 0.01 ? Invoice.InvoiceStatus.PAID : Invoice.InvoiceStatus.UNPAID);
        saved = invoiceRepository.save(saved);

        return mapToDTO(saved);
    }

    // IDOR fix (issue #12) — every "fetch one invoice" path below now checks that the caller is
    // ADMIN, the owning customer, or (where applicable) the rental's assigned driver, mirroring
    // the same fix applied to Reservation endpoints (issues #1/#2).
    private void assertOwnsInvoice(Invoice invoice) {
        Long driverId = invoice.getRental() != null && invoice.getRental().getDriver() != null
                ? invoice.getRental().getDriver().getDriverId() : null;
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomerOrDriver(
                invoice.getCustomer().getCustomerId(), driverId);
    }

    @Override @Transactional(readOnly = true)
    public InvoiceDTO getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
        assertOwnsInvoice(invoice);
        return mapToDTO(invoice);
    }
    @Override @Transactional(readOnly = true)
    public InvoiceDTO getInvoiceByNumber(String number) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(number)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + number));
        assertOwnsInvoice(invoice);
        return mapToDTO(invoice);
    }
    @Override @Transactional(readOnly = true)
    public InvoiceDTO getInvoiceByRentalId(Long rentalId) {
        Invoice invoice = invoiceRepository.findByRental_RentalId(rentalId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice for rental: " + rentalId));
        assertOwnsInvoice(invoice);
        return mapToDTO(invoice);
    }
    @Override @Transactional(readOnly = true)
    public List<InvoiceDTO> getAllInvoices() {
        // Issue #24 fix — see InvoiceRepository.findAllWithDetails().
        return invoiceRepository.findAllWithDetails().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated counterpart.
    @Override @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<InvoiceDTO> getAllInvoicesPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("invoiceDate").descending());
        var result = invoiceRepository.findAllWithDetails(pageable).map(this::mapToDTO);
        return com.rentmyride.dtos.PageResponse.from(result);
    }
    @Override @Transactional(readOnly = true)
    public List<InvoiceDTO> getInvoicesByCustomer(Long customerId) {
        // A customer may only list their own invoices, not another customer's.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return invoiceRepository.findByCustomer_CustomerId(customerId).stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    // Bug fix: every other read method in this class is @Transactional(readOnly = true) — this
    // one wasn't. Invoice.rental/.customer/.car are all LAZY associations, and mapToDTO() below
    // touches every one of them (i.getRental().getRentalId(), i.getCustomer().getFirstName(),
    // i.getCar().getBrand(), etc.). Without an open transaction wrapping the whole method, the
    // Hibernate session closes the instant findById() returns — so those lazy-loads then throw
    // LazyInitializationException, which the catch below re-wraps as a generic RuntimeException,
    // which GlobalExceptionHandler in turn reports as "Something went wrong on our end" (an
    // ERR-xxxxx reference) instead of ever actually building the PDF.
    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoicePdf(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        assertOwnsInvoice(invoice);
        try {
            return com.rentmyride.util.InvoicePdfGenerator.generate(mapToDTO(invoice));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF: " + e.getMessage(), e);
        }
    }

    private InvoiceDTO mapToDTO(Invoice i) {
        return InvoiceDTO.builder()
                .invoiceId(i.getInvoiceId()).invoiceNumber(i.getInvoiceNumber())
                .rentalId(i.getRental().getRentalId())
                .customerId(i.getCustomer().getCustomerId())
                .customerName(i.getCustomer().getFirstName() + " " + i.getCustomer().getLastName())
                .customerEmail(i.getCustomer().getEmail())
                .customerMobile(i.getCustomer().getMobileNumber())
                .carId(i.getCar().getCarId()).carBrand(i.getCar().getBrand())
                .carModel(i.getCar().getModel()).carRegistrationNumber(i.getCar().getRegistrationNumber())
                .rentPerDay(i.getRentPerDay()).totalDays(i.getTotalDays())
                .baseRentAmount(i.getBaseRentAmount()).extraKmCharges(i.getExtraKmCharges())
                .damageCharges(i.getDamageCharges()).lateReturnCharges(i.getLateReturnCharges())
                .discountAmount(i.getDiscountAmount()).subtotal(i.getSubtotal())
                .cgstPercentage(i.getCgstPercentage()).sgstPercentage(i.getSgstPercentage())
                .totalGstPercentage(i.getTotalGstPercentage())
                .cgstAmount(i.getCgstAmount()).sgstAmount(i.getSgstAmount())
                .totalGstAmount(i.getTotalGstAmount()).grandTotal(i.getGrandTotal())
                .dueAmount(i.getDueAmount())
                .invoiceStatus(i.getInvoiceStatus()).invoiceDate(i.getInvoiceDate())
                .dueDate(i.getDueDate()).notes(i.getNotes())
                .depositHeld(i.getDepositHeld()).depositRefunded(i.getDepositRefunded())
                .depositAdjusted(i.getDepositAdjusted())
                .build();
    }
}
