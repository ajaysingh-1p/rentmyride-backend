package com.rentmyride.dtos;

import com.rentmyride.entities.Invoice;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDTO {

    private Long invoiceId;
    private String invoiceNumber;
    private Long rentalId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerMobile;
    private String customerAddress;
    private Long carId;
    private String carBrand;
    private String carModel;
    private String carRegistrationNumber;

    // Billing breakdown
    private Double rentPerDay;
    private Integer totalDays;
    private Double baseRentAmount;
    private Double extraKmCharges;
    private Double damageCharges;
    private Double lateReturnCharges;
    private Double discountAmount;
    private Double subtotal;

    // GST breakdown
    private Double cgstPercentage;
    private Double sgstPercentage;
    private Double totalGstPercentage;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double totalGstAmount;
    private Double grandTotal;
    private Double dueAmount;

    private Invoice.InvoiceStatus invoiceStatus;
    private LocalDateTime invoiceDate;
    private LocalDateTime dueDate;
    private String notes;

    // Self-drive only — null/0 for a chauffeur-driven rental.
    private Double depositHeld;
    private Double depositRefunded;
    private Double depositAdjusted;
}
