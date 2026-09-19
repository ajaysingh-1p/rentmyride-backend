package com.rentmyride.service;

import com.rentmyride.dtos.InvoiceDTO;
import java.util.List;

public interface InvoiceService {
    InvoiceDTO generateInvoice(Long rentalId);
    InvoiceDTO getInvoiceById(Long invoiceId);
    InvoiceDTO getInvoiceByNumber(String invoiceNumber);
    InvoiceDTO getInvoiceByRentalId(Long rentalId);
    List<InvoiceDTO> getAllInvoices();
    com.rentmyride.dtos.PageResponse<InvoiceDTO> getAllInvoicesPaged(int page, int size);
    List<InvoiceDTO> getInvoicesByCustomer(Long customerId);
    byte[] downloadInvoicePdf(Long invoiceId);
}
